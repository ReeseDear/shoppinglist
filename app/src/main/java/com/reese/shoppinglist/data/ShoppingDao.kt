package com.reese.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    /**
     * Row model for the Active List query (list_entries + items + store_items).
     * This MUST exist because MainActivity / ViewModel reference ShoppingDao.ListEntryRow.
     */
    data class ListEntryRow(
        val listEntryId: Long,
        val itemId: Long,
        val itemName: String,
        val checkedInCart: Boolean,

        val qtyToBuy: Double?,
        val unit: String?,
        val size: String?,
        val priceOverrideCents: Int?,

        val defaultQuantity: Double?,
        val defaultUnit: String?,
        val defaultPriceCents: Int?,

        val storePriceOverrideCents: Int?,
        val aisle: String?,

        val createdAtEpochMs: Long
    )

    // --- Items ---
    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): Item?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    // --- Items list for Picklist (ALL) ---
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun observeAllItems(): Flow<List<Item>>

    @Query(
        """
        SELECT * FROM items
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """
    )
    fun searchItems(query: String): Flow<List<Item>>

    // --- Items list for Picklist (STORE FILTERED) ---
    @Query(
        """
        SELECT i.* FROM items i
        INNER JOIN store_items si ON si.itemId = i.id
        WHERE si.storeId = :storeId
        ORDER BY i.name COLLATE NOCASE
        """
    )
    fun observeItemsForStore(storeId: Long): Flow<List<Item>>

    @Query(
        """
        SELECT i.* FROM items i
        INNER JOIN store_items si ON si.itemId = i.id
        WHERE si.storeId = :storeId
          AND i.name LIKE '%' || :query || '%'
        ORDER BY i.name COLLATE NOCASE
        """
    )
    fun searchItemsForStore(storeId: Long, query: String): Flow<List<Item>>

    // --- Stores ---
    @Query("SELECT * FROM stores ORDER BY name")
    fun getStores(): Flow<List<Store>>

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int

    @Insert
    suspend fun insertStore(store: Store): Long

    @Query("DELETE FROM stores WHERE id = :storeId")
    suspend fun deleteStoreById(storeId: Long)

    // --- StoreItems ---
    @Query(
        """
        SELECT * FROM store_items
        WHERE storeId = :storeId AND itemId = :itemId
        LIMIT 1
        """
    )
    suspend fun getStoreItem(storeId: Long, itemId: Long): StoreItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreItem(storeItem: StoreItem)

    @Query("DELETE FROM store_items WHERE storeId = :storeId AND itemId = :itemId")
    suspend fun deleteStoreItem(storeId: Long, itemId: Long)

    @Query(
        """
        SELECT si.storeId AS storeId,
               si.itemId AS itemId,
               i.name AS name,
               si.aisle AS aisle,
               si.priceOverrideCents AS priceOverrideCents,
               CASE WHEN le.id IS NULL THEN 0 ELSE 1 END AS inCart,
               si.createdAtEpochMs AS createdAt
        FROM store_items si
        INNER JOIN items i ON i.id = si.itemId
        LEFT JOIN list_entries le ON le.itemId = si.itemId
        WHERE si.storeId = :storeId
          AND (
                NULLIF(si.aisle, '') IS NOT NULL
                OR COALESCE(si.showIfAisleUnassigned, 1) = 1
              )
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            si.createdAtEpochMs
        """
    )
    fun getStoreItems(storeId: Long): Flow<List<StoreItemDisplay>>

    @Query("SELECT * FROM store_items WHERE itemId = :itemId")
    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem>

    // --- List Entries (Active List) ---
    @Query(
        """
        SELECT
            le.id AS listEntryId,
            le.itemId AS itemId,
            i.name AS itemName,
            le.checkedInCart AS checkedInCart,

            le.qtyToBuy AS qtyToBuy,
            le.unit AS unit,
            le.size AS size,
            le.priceOverrideCents AS priceOverrideCents,

            i.defaultQuantity AS defaultQuantity,
            i.defaultUnit AS defaultUnit,
            i.defaultPriceCents AS defaultPriceCents,

            si.priceOverrideCents AS storePriceOverrideCents,
            si.aisle AS aisle,

            le.createdAtEpochMs AS createdAtEpochMs
        FROM list_entries le
        INNER JOIN items i ON i.id = le.itemId
        LEFT JOIN store_items si
            ON si.itemId = i.id AND si.storeId = :storeId
        WHERE
            si.itemId IS NOT NULL
            AND (
                NULLIF(si.aisle, '') IS NOT NULL
                OR COALESCE(si.showIfAisleUnassigned, 1) = 1
            )
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            i.name COLLATE NOCASE ASC,
            le.createdAtEpochMs DESC
        """
    )
    fun observeListEntriesForStore(storeId: Long): Flow<List<ListEntryRow>>

    @Query("SELECT * FROM list_entries WHERE itemId = :itemId LIMIT 1")
    suspend fun getListEntryByItemId(itemId: Long): ListEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListEntry(entry: ListEntry): Long

    @Query("DELETE FROM list_entries WHERE itemId = :itemId")
    suspend fun deleteListEntryByItemId(itemId: Long)

    @Query("DELETE FROM list_entries WHERE id = :listEntryId")
    suspend fun deleteListEntry(listEntryId: Long)
}
