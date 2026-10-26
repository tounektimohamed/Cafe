package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["tableId"]),
        Index(value = ["state"]),
        Index(value = ["createdAt"])
    ]
)
data class OrderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val orderNumber: Int = 1,
    val tableId: String? = null,
    val tableName: String = "سفري",
    val isTakeaway: Boolean = false,
    val waiterId: String = "",
    val waiterName: String = "",
    val state: String = "NEW", // NEW, AWAITING_PAYMENT, PAID, READY, SERVED, CANCELLED
    val totalMillimes: Long = 0L,
    val receivedMillimes: Long = 0L,
    val changeMillimes: Long = 0L,
    val discountMillimes: Long = 0L,
    val discountReason: String = "",
    val cancellationReason: String = "",
    val paymentFirst: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L // Optimistic concurrency control
)

@Entity(
    tableName = "order_lines",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderLineEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val productId: String,
    val productNameAr: String,
    val unitPriceMillimes: Long,
    val quantity: Int = 1,
    val notes: String = "",
    val modifiersJson: String = "" // e.g. "بدون سكر" or "+ حليب"
)

@Entity(
    tableName = "order_deltas",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderDeltaEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val description: String,
    val deltaMillimes: Long,
    val approvedByStaffId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
