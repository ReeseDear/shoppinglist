package com.reese.shoppinglist.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val aisle: String,
    val inCart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis() )