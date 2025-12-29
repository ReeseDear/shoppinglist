package com.reese.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    // --- Items list for Picklist ---
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun observeAllItems(): Flow<List<Item>>

    @Query("""
    SELECT * FROM items
    WHERE name LIKE '%' || :query || '%'
    ORDER BY name COLLATE NOCASE
""")

    fun searchItems(query: String): Flow<List<Item>>

    // --- Stores ---
    @Query("SELECT * FROM stores ORDER BY name")
    fun getStores(): Flow<List<Store>>

    @Insert
    suspend fun insertStore(store: Store): Long

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int


    // --- Items (catalog) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: Item): Long

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
     *
     * (This matches the “add/increment” behavior without needing a separate Int column.)
     */
    @Query("""
        UPDATE list_entries
        SET qtyToBuy = CASE
            WHEN qtyToBuy IS NULL THEN 2.0
            ELSE qtyToBuy + 1.0
        END
        WHERE itemId = :itemId
    """)
    suspend fun incrementQtyToBuy(itemId: Long)

    @Query("UPDATE list_entries SET checkedInCart = NOT checkedInCart WHERE itemId = :itemId")
    suspend fun toggleListEntryCheckedByItemId(itemId: Long)

    @Query("DELETE FROM list_entries WHERE itemId = :itemId")
    suspend fun deleteListEntryByItemId(itemId: Long)

    data class ListEntryRow(
        val listEntryId: Long,
        val itemId: Long,
        val itemName: String,
        val checkedInCart: Boolean,
        val qtyToBuy: Double?,
        val unit: String?,
        val size: String?,
        val priceOverrideCents: Int?,
        val createdAtEpochMs: Long
    )

    /**
     * Home-screen feed (store-agnostic for now):
     * Sorted by item name to match Phase 4 default behavior when no store is selected.
     */
    @Query("""
        SELECT 
            le.id AS listEntryId,
            le.itemId AS itemId,
            i.name AS itemName,
            le.checkedInCart AS checkedInCart,
            le.qtyToBuy AS qtyToBuy,
            le.unit AS unit,
            le.size AS size,
            le.priceOverrideCents AS priceOverrideCents,
            le.createdAtEpochMs AS createdAtEpochMs
        FROM list_entries le
        INNER JOIN items i ON i.id = le.itemId
        ORDER BY i.name COLLATE NOCASE ASC, le.createdAtEpochMs DESC
    """)
    fun observeListEntries(): Flow<List<ListEntryRow>>

    // (Kept for compatibility if any UI code already calls it)
    @Query("DELETE FROM list_entries WHERE id = :listEntryId")
    suspend fun deleteListEntry(listEntryId: Long)

    // (Kept for compatibility if any UI code already calls it)
    @Query("UPDATE list_entries SET checkedInCart = :checked WHERE id = :listEntryId")
    suspend fun setListEntryChecked(listEntryId: Long, checked: Boolean)

    /**
     * Add-or-increment behavior for the active list.
     * - If ListEntry insert succeeds -> done.
     * - If it already exists -> increment qtyToBuy by 1.
     */
    @Transaction
    suspend fun addOrIncrementListEntry(itemId: Long): Long {
        val insertedId = insertListEntry(ListEntry(itemId = itemId))
        return if (insertedId > 0) {
            insertedId
        } else {
            // Existing entry. Increment quantity.
            incrementQtyToBuy(itemId)
            getListEntryIdByItemId(itemId) ?: error("ListEntry exists but could not be found for itemId=$itemId")
        }
    }

    /**
     * Creates (or finds) the Item, then add/increment it in the active list.
     */
    @Transaction
    suspend fun addToNeedToGetByName(rawName: String): Long {
        val name = rawName.trim()
        require(name.isNotBlank()) { "Item name cannot be blank" }

        // If item exists, use it. Otherwise create it.
        val existingId = getItemIdByName(name)
        val itemId = existingId ?: run {
            val inserted = insertItem(Item(name = name))
            if (inserted > 0) inserted else (getItemIdByName(name) ?: error("Failed to create/find item"))
        }

        // Add to active list (insert or increment)
        return addOrIncrementListEntry(itemId)
    }


    // --- Store items (per-store list) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreItem(storeItem: StoreItem)

    @Query("""
        SELECT 
            si.storeId AS storeId,
            si.itemId AS itemId,
            i.name AS name,
            si.aisle AS aisle,
            si.inCart AS inCart,
            si.createdAt AS createdAt
        FROM store_items si
        INNER JOIN items i ON i.id = si.itemId
        WHERE si.storeId = :storeId
        ORDER BY si.aisle, si.createdAt
    """)
    fun getStoreItems(storeId: Long): Flow<List<StoreItemDisplay>>

    @Query("DELETE FROM store_items WHERE storeId = :storeId AND itemId = :itemId")
    suspend fun deleteStoreItem(storeId: Long, itemId: Long)
}
