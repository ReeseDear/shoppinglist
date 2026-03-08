package com.reese.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    // ---------- Items ----------

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): Item?

    @Query(
        """
        SELECT * FROM items
        WHERE LOWER(name) = LOWER(:name)
        LIMIT 1
        """
    )
    suspend fun getItemByExactName(name: String): Item?

    @Query("SELECT id FROM items WHERE name = :name LIMIT 1")
    suspend fun getItemIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

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

    @Query(
        """
        SELECT * FROM items
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun observeItemSuggestions(query: String, limit: Int = 10): Flow<List<Item>>

    // ---------- Stores ----------

    @Query("SELECT * FROM stores ORDER BY name")
    fun getStores(): Flow<List<Store>>

    @Query("SELECT * FROM stores ORDER BY name")
    suspend fun getStoresOnce(): List<Store>

    @Insert
    suspend fun insertStore(store: Store): Long

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int

    @Query("DELETE FROM stores WHERE id = :storeId")
    suspend fun deleteStoreById(storeId: Long)

    // ---------- StoreItems ----------

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

    @Query("SELECT * FROM store_items WHERE itemId = :itemId")
    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem>

    @Query(
        """
        SELECT DISTINCT aisle
        FROM store_items
        WHERE storeId = :storeId
          AND aisle IS NOT NULL
          AND TRIM(aisle) != ''
          AND aisle LIKE '%' || :query || '%'
        ORDER BY aisle COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun observeAisleSuggestions(storeId: Long, query: String, limit: Int = 10): Flow<List<String>>

    // ---------- Store screen rows ----------

    @Query(
        """
        SELECT
            si.storeId AS storeId,
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
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            si.createdAtEpochMs
        """
    )
    fun getStoreItems(storeId: Long): Flow<List<StoreItemDisplay>>

    // ---------- ListEntries (Active list / Home) ----------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListEntry(entry: ListEntry): Long

    @Query("SELECT id FROM list_entries WHERE itemId = :itemId LIMIT 1")
    suspend fun getListEntryIdByItemId(itemId: Long): Long?

    @Query("UPDATE list_entries SET qtyToBuy = :qtyToBuy WHERE itemId = :itemId")
    suspend fun setQtyToBuy(itemId: Long, qtyToBuy: Double?)

    @Query("UPDATE list_entries SET checkedInCart = CASE WHEN checkedInCart = 1 THEN 0 ELSE 1 END WHERE itemId = :itemId")
    suspend fun toggleCheckedInCart(itemId: Long)

    // ADDED THIS FUNCTION TO FIX UNRESOLVED REFERENCE
    @Query("UPDATE list_entries SET checkedInCart = :checked WHERE itemId = :itemId")
    suspend fun updateListEntryChecked(itemId: Long, checked: Boolean)

    @Query("DELETE FROM list_entries WHERE itemId = :itemId")
    suspend fun deleteListEntryByItemId(itemId: Long)

    @Query(
        """
        DELETE FROM list_entries
        WHERE checkedInCart = 1
          AND itemId IN (
            SELECT i.id
            FROM items i
            INNER JOIN store_items si ON si.itemId = i.id
            WHERE si.storeId = :storeId
          )
        """
    )
    suspend fun clearInCartForStore(storeId: Long)

    /**
     * Row model for the Home list
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
        val taxable: Boolean,
        val storePriceOverrideCents: Int?,
        val aisle: String?,
        val createdAtEpochMs: Long
    )

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
            i.taxable AS taxable,
            si.priceOverrideCents AS storePriceOverrideCents,
            si.aisle AS aisle,
            le.createdAtEpochMs AS createdAtEpochMs
        FROM list_entries le
        INNER JOIN items i ON i.id = le.itemId
        LEFT JOIN store_items si
            ON si.itemId = i.id AND si.storeId = :storeId
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            i.name COLLATE NOCASE ASC,
            le.createdAtEpochMs DESC
        """
    )
    fun observeListEntriesForStore(storeId: Long): Flow<List<ListEntryRow>>

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
            i.taxable AS taxable,
            si.priceOverrideCents AS storePriceOverrideCents,
            si.aisle AS aisle,
            le.createdAtEpochMs AS createdAtEpochMs
        FROM list_entries le
        INNER JOIN items i ON i.id = le.itemId
        INNER JOIN store_items si
            ON si.itemId = i.id AND si.storeId = :storeId
        WHERE le.checkedInCart = 0
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            i.name COLLATE NOCASE ASC,
            le.createdAtEpochMs DESC
        """
    )
    fun observeListEntries_StoreOnly(storeId: Long): Flow<List<ListEntryRow>>

    // ---------- Picklist rows ----------

    data class PicklistItemRow(
        val itemId: Long,
        val name: String,
        val defaultPriceCents: Int?,
        val storePriceOverrideCents: Int?,
        val aisle: String?
    )

    @Query(
        """
        SELECT
            i.id AS itemId,
            i.name AS name,
            i.defaultPriceCents AS defaultPriceCents,
            si.priceOverrideCents AS storePriceOverrideCents,
            si.aisle AS aisle
        FROM items i
        LEFT JOIN store_items si
            ON si.itemId = i.id AND si.storeId = :storeId
        WHERE (:query = '' OR i.name LIKE '%' || :query || '%')
        ORDER BY
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            i.name COLLATE NOCASE ASC
        """
    )
    fun observePicklistForStore(storeId: Long, query: String): Flow<List<PicklistItemRow>>
}