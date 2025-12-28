package com.reese.shoppinglist.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    // --- Stores ---
    @Query("SELECT * FROM stores ORDER BY name")
    fun getStores(): Flow<List<Store>>

    @Insert
    suspend fun insertStore(store: Store): Long

    @Query("SELECT COUNT(*)FROM Stores")
    suspend fun getStoreCount(): Int

    // --- Items ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: Item): Long

    @Query("SELECT id FROM items WHERE name = :name LIMIT 1")
    suspend fun getItemIdByName(name: String): Long?

    // --- Store items (per-store list) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreItem(storeItem: StoreItem)

    @Update
    suspend fun updateStoreItem(storeItem: StoreItem)

    @Query("""
        SELECT si.storeId AS storeId, si.itemId AS itemId, i.name AS name, si.aisle AS aisle, si.inCart AS inCart, si.createdAt AS createdAt
        FROM store_items si
        INNER JOIN items i ON i.id = si.itemId
        WHERE si.storeId = :storeId
        ORDER BY si.aisle, si.createdAt
    """)
    fun getStoreItems(storeId: Long): Flow<List<StoreItemDisplay>>

    @Query("DELETE FROM store_items WHERE storeId = :storeId AND itemId = :itemId")
    suspend fun deleteStoreItem(storeId: Long, itemId: Long)
}