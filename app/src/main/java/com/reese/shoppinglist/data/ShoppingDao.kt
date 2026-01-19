package com.reese.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    // --- Items ---
    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    // For Edit Item screen
    @Query("SELECT * FROM items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): Item?

    /**
     * NOTE:
     * Using IGNORE means insert may return -1 if a conflict occurs.
     * For "create new item" flows, callers should handle fallback lookup by name if needed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    // --- Items list for Picklist ---
    // FIX: items table does NOT have an aisle column (aisle lives in store_items)
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

    // --- Stores ---
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

    // --- Items (catalog) ---
    @Query("SELECT id FROM items WHERE name = :name LIMIT 1")
    suspend fun getItemIdByName(name: String): Long?

    // --- ListEntries (active list) ---
    /**
     * IMPORTANT:
     * We use IGNORE so that if your ListEntry table enforces a UNIQUE itemId,
     * inserts will fail gracefully and we can "increment" instead.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListEntry(entry: ListEntry): Long

    @Query("SELECT id FROM list_entries WHERE itemId = :itemId LIMIT 1")
    suspend fun getListEntryIdByItemId(itemId: Long): Long?

    /**
     * Quantity behavior:
     * - If qtyToBuy is null, treat it like 1.
     * - Increment by 1 each time the item is added again.
     */
    @Query(
        """
        UPDATE list_entries
        SET qtyToBuy = CASE
            WHEN qtyToBuy IS NULL THEN 2.0
            ELSE qtyToBuy + 1.0
        END
        WHERE itemId = :itemId
        """
    )
    suspend fun incrementQtyToBuy(itemId: Long)

    @Query("UPDATE list_entries SET checkedInCart = NOT checkedInCart WHERE itemId = :itemId")
    suspend fun toggleListEntryCheckedByItemId(itemId: Long)

    @Query("DELETE FROM list_entries WHERE itemId = :itemId")
    suspend fun deleteListEntryByItemId(itemId: Long)

    @Query("UPDATE list_entries SET qtyToBuy = :qtyToBuy WHERE itemId = :itemId")
    suspend fun setQtyToBuy(itemId: Long, qtyToBuy: Double?)

    /**
     * Row model for the active list.
     *
     * IMPORTANT:
     * - qty/price can come from list_entries (trip overrides)
     * - otherwise fall back to store_items (per-store override)
     * - otherwise fall back to items (catalog defaults)
     */
    data class ListEntryRow(
        val listEntryId: Long,
        val itemId: Long,
        val itemName: String,
        val checkedInCart: Boolean,

        // Trip overrides (list_entries)
        val qtyToBuy: Double?,
        val unit: String?,
        val size: String?,
        val priceOverrideCents: Int?,

        // Catalog defaults (items)
        val defaultQuantity: Double?,
        val defaultUnit: String?,
        val defaultPriceCents: Int?,

        // Per-store override (store_items for selected store; null for store-agnostic feed)
        val storePriceOverrideCents: Int?,

        // Store-aware aisle (null for store-agnostic feed)
        val aisle: String?,

        val createdAtEpochMs: Long
    )

    /**
     * Home-screen feed (store-agnostic)
     * aisle/storePriceOverrideCents are always NULL here.
     */
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

            NULL AS storePriceOverrideCents,
            NULL AS aisle,

            le.createdAtEpochMs AS createdAtEpochMs
        FROM list_entries le
        INNER JOIN items i ON i.id = le.itemId
        ORDER BY i.name COLLATE NOCASE ASC, le.createdAtEpochMs DESC
        """
    )
    fun observeListEntries(): Flow<List<ListEntryRow>>

    /**
     * Home-screen feed (store-aware)
     * Pulls aisle + per-store price from store_items for the selected store.
     */
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
        ORDER BY 
            COALESCE(NULLIF(si.aisle, ''), 'ZZZ') COLLATE NOCASE ASC,
            i.name COLLATE NOCASE ASC,
            le.createdAtEpochMs DESC
        """
    )
    fun observeListEntriesForStore(storeId: Long): Flow<List<ListEntryRow>>

    // (Kept for compatibility if any UI code already calls it)
    @Query("DELETE FROM list_entries WHERE id = :listEntryId")
    suspend fun deleteListEntry(listEntryId: Long)

    // (Kept for compatibility if any UI code already calls it)
    @Query("UPDATE list_entries SET checkedInCart = :checked WHERE id = :listEntryId")
    suspend fun setListEntryChecked(listEntryId: Long, checked: Boolean)

    /**
     * Add-or-increment behavior for the active list.
     */
    @Transaction
    suspend fun addOrIncrementListEntry(itemId: Long): Long {
        val insertedId = insertListEntry(ListEntry(itemId = itemId))
        return if (insertedId > 0) {
            insertedId
        } else {
            incrementQtyToBuy(itemId)
            getListEntryIdByItemId(itemId)
                ?: error("ListEntry exists but could not be found for itemId=$itemId")
        }
    }

    /**
     * Creates (or finds) the Item, then add/increment it in the active list.
     */
    @Transaction
    suspend fun addToNeedToGetByName(rawName: String): Long {
        val name = rawName.trim()
        require(name.isNotBlank()) { "Item name cannot be blank" }

        val existingId = getItemIdByName(name)
        val itemId = existingId ?: run {
            val inserted = insertItem(Item(name = name))
            if (inserted > 0) inserted
            else (getItemIdByName(name) ?: error("Failed to create/find item"))
        }

        return addOrIncrementListEntry(itemId)
    }

    // --- StoreItems (catalog per-store mapping) ---

    // For Edit Item screen (load existing checked stores)
    @Query("SELECT * FROM store_items WHERE itemId = :itemId")
    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreItem(storeItem: StoreItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreItems(storeItems: List<StoreItem>)

    @Query("DELETE FROM store_items WHERE storeId = :storeId AND itemId = :itemId")
    suspend fun deleteStoreItem(storeId: Long, itemId: Long)

    @Query("DELETE FROM store_items WHERE itemId = :itemId AND storeId IN (:storeIds)")
    suspend fun deleteStoreItemsForItem(itemId: Long, storeIds: List<Long>)

    /**
     * Save Item + per-store mappings
     *
     * - Inserts new item if id==0
     * - Updates existing item if id!=0
     * - Upserts StoreItem rows for checked stores
     * - Deletes StoreItem rows for unchecked stores
     */
    @Transaction
    suspend fun saveItemAndStoreMappings(
        item: Item,
        checkedStoreItems: List<StoreItem>,
        uncheckedStoreIds: List<Long>
    ): Long {

        val itemId = if (item.id == 0L) {
            val inserted = insertItem(item)
            if (inserted > 0) inserted
            else (getItemIdByName(item.name) ?: error("Failed to create/find item '${item.name}'"))
        } else {
            updateItem(item)
            item.id
        }

        if (checkedStoreItems.isNotEmpty()) {
            val normalized = checkedStoreItems.map { si ->
                if (si.itemId == itemId) si else si.copy(itemId = itemId)
            }
            upsertStoreItems(normalized)
        }

        if (uncheckedStoreIds.isNotEmpty()) {
            deleteStoreItemsForItem(itemId, uncheckedStoreIds)
        }

        return itemId
    }

    /**
     * Store view list.
     *
     * NOTE:
     * StoreItem no longer has inCart, so we derive "inCart"
     * from whether that item currently exists in list_entries.
     */
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
}
