package com.example.core.network

enum class ConnectionStatus(val labelAr: String) {
    CONNECTED("متصل بالشبكة المحلية"),
    RECONNECTING("جارِ إعادة الاتصال..."),
    OFFLINE("غير متصل (الوضع دون إنترنت)")
}

data class PairingPayload(
    val hostIp: String,
    val port: Int = 8088,
    val pairingToken: String,
    val cafeName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkMessage(
    val type: String, // ORDER_CREATED, PAYMENT_REQUESTED, ORDER_PAID, ORDER_READY, ORDER_SERVED, ORDER_CANCELLED, SYNC_REQUEST, PING
    val orderId: String? = null,
    val payloadJson: String = "",
    val token: String = "",
    val clientDeviceId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class OutboxItem(
    val id: String, // Idempotent UUID
    val action: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
