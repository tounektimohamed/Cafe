package com.example.core.model

enum class OrderState(
    val labelAr: String,
    val isTerminal: Boolean
) {
    NEW("طلب جديد", false),
    AWAITING_PAYMENT("في انتظار الدفع", false),
    PAID("تم الخلاص (مدفوع)", false),
    READY("جاهز للتقديم", false),
    SERVED("تم التقديم (مغلق)", true),
    CANCELLED("ملغى", true);

    companion object {
        fun fromString(name: String): OrderState {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NEW
        }
    }
}
