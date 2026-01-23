package com.reese.shoppinglist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "store_items",
    primaryKeys = ["storeId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = Store::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("storeId"),
        Index("itemId")
    ]
)
data class StoreItem(
    val storeId: Long,
    val itemId: Long,

    val aisle: String? = null,

    // Per-store override (optional)
    val priceOverrideCents: Int? = null,
    val showIfAisleUnassigned: Boolean = true,

    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
