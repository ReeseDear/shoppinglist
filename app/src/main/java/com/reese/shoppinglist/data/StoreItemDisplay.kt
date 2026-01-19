package com.reese.shoppinglist.data

data class StoreItemDisplay(
    val storeId: Long,
    val itemId: Long,
    val name: String,
    val aisle: String?,

    // Phase 5: per-store override
    val priceOverrideCents: Int?,

    // Derived by query (left-join to list_entries)
    val inCart: Boolean,

    // From StoreItem.createdAtEpochMs (aliased as createdAt)
    val createdAt: Long
)
