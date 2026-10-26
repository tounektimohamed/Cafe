package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val openedByStaffId: String,
    val openedByName: String,
    val role: String, // COUNTER, WAITER
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val startingFloatMillimes: Long = 0L, // Fond de caisse
    val expectedCashMillimes: Long = 0L,
    val actualCashMillimes: Long = 0L,
    val differenceMillimes: Long = 0L, // Positive = surplus, Negative = shortage
    val managerApprovalPin: String = "",
    val isClosed: Boolean = false
)

@Entity(tableName = "cash_ledger_entries")
data class CashLedgerEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val shiftId: String,
    val waiterId: String,
    val waiterName: String,
    val orderId: String,
    val amountMillimes: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val action: String, // CANCEL_ORDER, APPLY_DISCOUNT, CLOSE_SHIFT, CHANGE_PRICE, LOGIN
    val details: String,
    val staffId: String,
    val staffName: String,
    val deviceId: String = "local",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "print_jobs")
data class PrintJobEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: String, // RECEIPT, KITCHEN_TICKET, SHIFT_REPORT
    val content: String,
    val status: String = "PENDING", // PENDING, PRINTED, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deviceName: String,
    val role: String, // COUNTER, WAITER, MANAGER
    val pairingToken: String,
    val ipAddress: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isHost: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
