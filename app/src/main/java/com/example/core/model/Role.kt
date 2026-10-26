package com.example.core.model

enum class Role(val titleAr: String, val titleFr: String) {
    COUNTER("المحاسب (الكاونتر)", "Comptoiriste"),
    WAITER("النادل", "Serveur"),
    MANAGER("المدير (المسؤول)", "Gérant"),
    KITCHEN("شاشة المطبخ والبار", "Cuisine & Bar");

    companion object {
        fun fromString(value: String): Role {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WAITER
        }
    }
}
