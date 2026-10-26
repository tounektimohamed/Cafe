package com.example.core.printer

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

enum class PrinterConnectionType {
    LAN_WIFI,
    BLUETOOTH,
    USB,
    PREVIEW_MODE
}

data class PrinterStatus(
    val isConnected: Boolean = true,
    val connectionType: PrinterConnectionType = PrinterConnectionType.PREVIEW_MODE,
    val targetAddress: String = "192.168.1.100:9100",
    val lastPrintSuccess: Boolean = true,
    val lastError: String? = null
)

interface ThermalPrinter {
    suspend fun printBitmap(bitmap: Bitmap): Boolean
    suspend fun testConnection(): Boolean
    fun getStatus(): StateFlow<PrinterStatus>
}

class PosPrinterManager(
    private val context: Context
) : ThermalPrinter {

    private val _status = MutableStateFlow(PrinterStatus())
    override fun getStatus(): StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _lastPrintedBitmap = MutableStateFlow<Bitmap?>(null)
    val lastPrintedBitmap: StateFlow<Bitmap?> = _lastPrintedBitmap.asStateFlow()

    fun updateConfig(type: PrinterConnectionType, targetAddress: String) {
        _status.value = _status.value.copy(
            connectionType = type,
            targetAddress = targetAddress
        )
    }

    override suspend fun printBitmap(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        _lastPrintedBitmap.value = bitmap
        val bytes = ArabicReceiptRenderer.bitmapToEscPosBytes(bitmap)

        when (_status.value.connectionType) {
            PrinterConnectionType.LAN_WIFI -> {
                sendToLanPrinter(bytes, _status.value.targetAddress)
            }
            PrinterConnectionType.BLUETOOTH -> {
                // In production, connects via BluetoothSocket(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                _status.value = _status.value.copy(isConnected = true, lastPrintSuccess = true, lastError = null)
                true
            }
            PrinterConnectionType.USB -> {
                _status.value = _status.value.copy(isConnected = true, lastPrintSuccess = true, lastError = null)
                true
            }
            PrinterConnectionType.PREVIEW_MODE -> {
                _status.value = _status.value.copy(isConnected = true, lastPrintSuccess = true, lastError = null)
                true
            }
        }
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (_status.value.connectionType == PrinterConnectionType.PREVIEW_MODE) {
            _status.value = _status.value.copy(isConnected = true, lastError = null)
            return@withContext true
        }

        try {
            if (_status.value.connectionType == PrinterConnectionType.LAN_WIFI) {
                val parts = _status.value.targetAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 9100
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 2000)
                }
                _status.value = _status.value.copy(isConnected = true, lastError = null)
                true
            } else {
                _status.value = _status.value.copy(isConnected = true, lastError = null)
                true
            }
        } catch (e: Exception) {
            _status.value = _status.value.copy(isConnected = false, lastError = e.localizedMessage)
            false
        }
    }

    private fun sendToLanPrinter(bytes: ByteArray, address: String): Boolean {
        return try {
            val parts = address.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 9100
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                val out: OutputStream = socket.getOutputStream()
                out.write(bytes)
                out.flush()
            }
            _status.value = _status.value.copy(isConnected = true, lastPrintSuccess = true, lastError = null)
            true
        } catch (e: Exception) {
            // Log and update status
            _status.value = _status.value.copy(isConnected = false, lastPrintSuccess = false, lastError = "تعذر الاتصال بالطابعة عبر الشبكة")
            false
        }
    }
}
