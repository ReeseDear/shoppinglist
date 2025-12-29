package com.reese.shoppinglist.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [Index(value = ["name"], unique = true)]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val name: String,

    // Catalog defaults (optional)
    val defaultPriceCents: Int? = null,
    val defaultQuantity: Double? = null,
    val defaultUnit: String? = null,
    val defaultSize: String? = null,

    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
