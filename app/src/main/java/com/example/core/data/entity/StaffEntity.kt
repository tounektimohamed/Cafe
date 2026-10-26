package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String, // COUNTER, WAITER, MANAGER
    val pinHash: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
