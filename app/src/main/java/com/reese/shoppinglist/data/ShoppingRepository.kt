package com.reese.shoppinglist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val dao: ShoppingDao) {

    // --- Stores ---
    val stores: Flow<List<Store>> = dao.getStores()

    suspend fun ensureDefaultStore(): Long {
        if (dao.getStoreCount() == 0) {
            return dao.insertStore(Store(name = "Default Store"))
        }
        return -1
    }

    suspend fun addStore(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        return dao.insertStore(Store(name = trimmed))
    }

    suspend fun deleteStore(storeId: Long) {
        dao.deleteStoreById(storeId)
    }

    // --- Store items (per-store list) ---
    fun storeItems(storeId: Long): Flow<List<StoreItemDisplay>> = dao.getStoreItems(storeId)

    suspend fun addItemToStore(storeId: Long, name: String, aisle: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val itemId = dao.getItemIdByName(trimmedName) ?: run {
            val inserted = dao.insertItem(Item(name = trimmedName))
            if (inserted > 0) inserted else (dao.getItemIdByName(trimmedName) ?: return)
        }

        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = itemId,
                aisle = aisle.trim().ifEmpty { null },
                priceOverrideCents = null
            )
        )
    }

    suspend fun deleteFromStore(row: StoreItemDisplay) {
        dao.deleteStoreItem(row.storeId, row.itemId)
    }

    // --- Active List (Home) ---
    fun observeActiveListForStore(storeId: Long): Flow<List<ShoppingDao.ListEntryRow>> =
        dao.observeListEntriesForStore(storeId)

    suspend fun addToActiveListByName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.addToNeedToGetByName(trimmed)
    }

    suspend fun addToActiveListByItemId(itemId: Long) {
        dao.addOrIncrementListEntry(itemId)
    }

    suspend fun toggleActiveListChecked(itemId: Long) {
        dao.toggleListEntryCheckedByItemId(itemId)
    }

    suspend fun removeFromActiveList(itemId: Long) {
        dao.deleteListEntryByItemId(itemId)
    }

    // ✅ INLINE QTY EDIT: persist trip qty override
    suspend fun setActiveListQty(itemId: Long, qtyToBuy: Double?) {
        dao.setQtyToBuy(itemId, qtyToBuy)
    }

    // --- Picklist (Items catalog) ---
    fun observeAllItems(): Flow<List<Item>> = dao.observeAllItems()

    fun searchItems(query: String): Flow<List<Item>> = dao.searchItems(query)

    suspend fun deleteCatalogItem(itemId: Long) {
        dao.deleteItemById(itemId)
    }

    // --- Edit Item support ---
    suspend fun getItemById(itemId: Long): Item? = dao.getItemById(itemId)

    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem> =
        dao.getStoreItemsForItemOnce(itemId)

    /**
     * Used by ViewModel.saveItem(updated)
     * (kept simple: insert if new, update if existing)
     */
    suspend fun upsertItem(item: Item): Long {
        return if (item.id == 0L) {
            val inserted = dao.insertItem(item)
            if (inserted > 0) inserted
            else (dao.getItemIdByName(item.name) ?: -1L)
        } else {
            dao.updateItem(item)
            item.id
        }
    }

    /**
     * Multi-store version (checkbox UI):
     * - checkedStoreIds defines which stores get a StoreItem row.
     * - aisle/priceOverride are null for now.
     */
    suspend fun saveItemAndStoreMappings(
        item: Item,
        checkedStoreIds: List<Long>
    ): Long {
        val allStoreIds = dao.getStoresOnce().map { it.id }

        val checkedSet = checkedStoreIds.toSet()
        val uncheckedStoreIds = allStoreIds.filter { it !in checkedSet }

        val checkedStoreItems = checkedStoreIds.distinct().map { storeId ->
            StoreItem(
                storeId = storeId,
                itemId = item.id, // DAO normalizes if item.id == 0
                aisle = null,
                priceOverrideCents = null
            )
        }

        return dao.saveItemAndStoreMappings(
            item = item,
            checkedStoreItems = checkedStoreItems,
            uncheckedStoreIds = uncheckedStoreIds
        )
    }

    /**
     * Single-store + aisle version (selected store in Edit Item):
     * - Saves Item defaults
     * - Upserts ONE StoreItem row for storeId with aisle + price override
     * - DOES NOT remove mappings for other stores
     */
    suspend fun saveItemForStoreWithAisle(
        item: Item,
        storeId: Long,
        aisle: String?,
        priceOverrideCents: Int? = null
    ): Long {

        val checkedStoreItems = listOf(
            StoreItem(
                storeId = storeId,
                itemId = item.id,
                aisle = aisle?.trim()?.ifEmpty { null },
                priceOverrideCents = priceOverrideCents
            )
        )

        return dao.saveItemAndStoreMappings(
            item = item,
            checkedStoreItems = checkedStoreItems,
            uncheckedStoreIds = emptyList()
        )
    }

    // --- Backward-compatible wrappers (store-agnostic) ---
    fun observeNeedToGet(): Flow<List<ShoppingDao.ListEntryRow>> = dao.observeListEntries()

    suspend fun addByName(name: String) = addToActiveListByName(name)

    suspend fun setChecked(listEntryId: Long, checked: Boolean) {
        dao.setListEntryChecked(listEntryId, checked)
    }

    suspend fun deleteListEntry(listEntryId: Long) {
        dao.deleteListEntry(listEntryId)
    }
}
