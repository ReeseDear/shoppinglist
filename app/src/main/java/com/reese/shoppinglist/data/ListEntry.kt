package com.reese.shoppinglist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "list_entries",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // One active-list entry per Item. This enables "insert OR increment" logic.
        Index(value = ["itemId"], unique = true),

        // Useful for some queries / debugging; optional but fine to keep.
        Index(value = ["createdAtEpochMs"])
    ]
)
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val itemId: Long,

    val checkedInCart: Boolean = false,

    // Optional overrides for “this trip”
    val qtyToBuy: Double? = null,
    val unit: String? = null,
    val size: String? = null,
    val priceOverrideCents: Int? = null,

    val createdAtEpochMs: Long = System.currentTimeMillis()
)
