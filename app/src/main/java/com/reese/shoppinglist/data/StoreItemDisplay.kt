package com.reese.shoppinglist.data

data class StoreItemDisplay(
    val storeId: Long,
    val itemId: Long,
    val name: String,
    val aisle: String,
    val inCart: Boolean,
    val createdAt: Long
)