package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val nameAr: String,
    val nameFr: String,
    val iconName: String = "coffee",
    val sortOrder: Int = 0
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val nameAr: String,
    val nameFr: String,
    val priceMillimes: Long,
    val isAvailable: Boolean = true,
    val isFavorite: Boolean = false,
    val stockQuantity: Int = 100, // Stock tracking
    val sortOrder: Int = 0
)

@Entity(tableName = "modifier_options")
data class ModifierOptionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val nameAr: String,
    val extraPriceMillimes: Long = 0L,
    val isSugar: Boolean = false,
    val isSize: Boolean = false
)
