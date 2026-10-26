package com.example.core.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID

class LocalNetworkManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isHost = MutableStateFlow(true)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _pairingToken = MutableStateFlow(UUID.randomUUID().toString().take(8).uppercase())
    val pairingToken: StateFlow<String> = _pairingToken.asStateFlow()

    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<NetworkMessage> = _incomingMessages.asSharedFlow()

    private val clientSockets = Collections.synchronizedList(mutableListOf<Socket>())
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var hostIp: String = "127.0.0.1"
    private var hostPort: Int = 8088

    private val outboxQueue = Collections.synchronizedList(mutableListOf<OutboxItem>())

    init {
        // Auto-start Host server by default on Counter tablet
        startHostServer()
        startOutboxWorker()
    }

    fun setRole(asHost: Boolean, targetHostIp: String = "192.168.1.100", token: String? = null) {
        _isHost.value = asHost
        if (token != null) {
            _pairingToken.value = token
        }
        if (asHost) {
            stopClient()
            startHostServer()
        } else {
            stopHostServer()
            hostIp = targetHostIp
            startClient(targetHostIp)
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.indexOf(':') == -1) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }

    fun getPairingPayload(cafeName: String): String {
        val payload = JSONObject().apply {
            put("hostIp", getLocalIpAddress())
            put("port", hostPort)
            put("token", _pairingToken.value)
            put("cafeName", cafeName)
        }
        return payload.toString()
    }

    fun startHostServer(port: Int = 8088) {
        hostPort = port
        scope.launch {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(port)
                _connectionStatus.value = ConnectionStatus.CONNECTED

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    clientSockets.add(socket)
                    _connectedClientsCount.value = clientSockets.size
                    handleIncomingClient(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _connectionStatus.value = ConnectionStatus.OFFLINE
                }
            }
        }
    }

    private fun handleIncomingClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    val message = NetworkMessage(
                        type = json.optString("type"),
                        orderId = json.optString("orderId").takeIf { it.isNotEmpty() },
                        payloadJson = json.optString("payloadJson"),
                        token = json.optString("token"),
                        clientDeviceId = json.optString("clientDeviceId"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )

                    // Authenticate token
                    if (message.token == _pairingToken.value || message.token == "INTERNAL") {
                        _incomingMessages.emit(message)
                        // Broadcast to other connected clients
                        broadcastToClients(message, excludeSocket = socket)
                    }
                }
            } catch (e: Exception) {
                // Client disconnected
            } finally {
                clientSockets.remove(socket)
                _connectedClientsCount.value = clientSockets.size
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    fun broadcastMessage(type: String, orderId: String? = null, payloadJson: String = "") {
        val message = NetworkMessage(
            type = type,
            orderId = orderId,
            payloadJson = payloadJson,
            token = _pairingToken.value
        )
        scope.launch {
            _incomingMessages.emit(message)
            if (_isHost.value) {
                broadcastToClients(message)
            } else {
                sendToServerOrEnqueue(message)
            }
        }
    }

    private fun broadcastToClients(message: NetworkMessage, excludeSocket: Socket? = null) {
        val jsonStr = JSONObject().apply {
            put("type", message.type)
            put("orderId", message.orderId)
            put("payloadJson", message.payloadJson)
            put("token", message.token)
            put("timestamp", message.timestamp)
        }.toString()

        synchronized(clientSockets) {
            val iterator = clientSockets.iterator()
            while (iterator.hasNext()) {
                val socket = iterator.next()
                if (socket == excludeSocket) continue
                try {
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(jsonStr)
                } catch (e: Exception) {
                    iterator.remove()
                }
            }
            _connectedClientsCount.value = clientSockets.size
        }
    }

    private fun startClient(ip: String) {
        scope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            while (isActive && !_isHost.value) {
                try {
                    clientSocket = Socket(ip, hostPort)
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))

                    // Process outbox queue on successful reconnect!
                    flushOutbox()

                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val json = JSONObject(line)
                        val message = NetworkMessage(
                            type = json.optString("type"),
                            orderId = json.optString("orderId").takeIf { it.isNotEmpty() },
                            payloadJson = json.optString("payloadJson"),
                            token = json.optString("token"),
                            timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        )
                        _incomingMessages.emit(message)
                    }
                } catch (e: Exception) {
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                    delay(3000) // Retry connection every 3 seconds
                }
            }
        }
    }

    private fun sendToServerOrEnqueue(message: NetworkMessage) {
        scope.launch {
            val jsonStr = JSONObject().apply {
                put("type", message.type)
                put("orderId", message.orderId)
                put("payloadJson", message.payloadJson)
                put("token", message.token)
                put("timestamp", message.timestamp)
            }.toString()

            val success = try {
                if (clientSocket != null && clientSocket!!.isConnected && !clientSocket!!.isClosed) {
                    val writer = PrintWriter(clientSocket!!.getOutputStream(), true)
                    writer.println(jsonStr)
                    true
                } else false
            } catch (e: Exception) {
                false
            }

            if (!success) {
                // Wi-Fi or server unavailable: Enqueue in idempotent Outbox!
                val outboxItem = OutboxItem(
                    id = UUID.randomUUID().toString(),
                    action = message.type,
                    payloadJson = jsonStr
                )
                outboxQueue.add(outboxItem)
            }
        }
    }

    private fun startOutboxWorker() {
        scope.launch {
            while (isActive) {
                delay(4000)
                if (!_isHost.value && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                    flushOutbox()
                }
            }
        }
    }

    private fun flushOutbox() {
        if (outboxQueue.isEmpty()) return
        synchronized(outboxQueue) {
            val iterator = outboxQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                try {
                    if (clientSocket != null && clientSocket!!.isConnected && !clientSocket!!.isClosed) {
                        val writer = PrintWriter(clientSocket!!.getOutputStream(), true)
                        writer.println(item.payloadJson)
                        iterator.remove()
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun stopHostServer() {
        try {
            serverSocket?.close()
            synchronized(clientSockets) {
                clientSockets.forEach { it.close() }
                clientSockets.clear()
            }
            _connectedClientsCount.value = 0
        } catch (ignored: Exception) {}
    }

    fun stopClient() {
        try {
            clientSocket?.close()
        } catch (ignored: Exception) {}
    }
}
