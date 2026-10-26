package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "dining_tables")
data class DiningTableEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val tableNumber: Int,
    val name: String, // e.g., "طاولة 1", "شرفة 4"
    val capacity: Int = 4,
    val status: String = "FREE", // FREE, OCCUPIED, BILL_REQUESTED
    val activeOrderId: String? = null,
    val activeWaiterName: String? = null,
    val isTakeaway: Boolean = false
)
