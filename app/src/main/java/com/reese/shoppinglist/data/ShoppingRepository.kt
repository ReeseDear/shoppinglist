package com.reese.shoppinglist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val dao: ShoppingDao) {

    // ---------- Stores ----------

    val stores: Flow<List<Store>> = dao.getStores()

    suspend fun ensureDefaultStore(): Long {
        return if (dao.getStoreCount() == 0) {
            dao.insertStore(Store(name = "Default Store"))
        } else {
            -1L
        }
    }

    suspend fun addStore(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertStore(Store(name = trimmed))
    }

    suspend fun deleteStore(storeId: Long) {
        dao.deleteStoreById(storeId)
    }

    // ---------- Store Items (Store screen) ----------

    fun storeItems(storeId: Long): Flow<List<StoreItemDisplay>> = dao.getStoreItems(storeId)

    // ---------- Home list (Active list) ----------

    fun observeActiveListForStore(
        storeId: Long,
        storeSpecificOnly: Boolean
    ): Flow<List<ShoppingDao.ListEntryRow>> {
        return if (storeSpecificOnly) dao.observeListEntries_StoreOnly(storeId)
        else dao.observeListEntriesForStore(storeId)
    }

    suspend fun addItemByNameToStoreAndList(name: String, storeId: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        // 1) Ensure item exists (case-insensitive exact match)
        val existing = dao.getItemByExactName(trimmed)
        val itemId = if (existing != null) {
            existing.id
        } else {
            val inserted = dao.insertItem(Item(name = trimmed))
            if (inserted > 0) inserted else (dao.getItemIdByName(trimmed) ?: return)
        }

        // 2) Ensure a store_items row exists for current store
        val existingStoreItem = dao.getStoreItem(storeId, itemId)
        if (existingStoreItem == null) {
            dao.upsertStoreItem(
                StoreItem(
                    storeId = storeId,
                    itemId = itemId,
                    aisle = null,
                    showIfAisleUnassigned = true,
                    priceOverrideCents = null
                )
            )
        }

        // 3) Ensure list entry exists
        if (dao.getListEntryIdByItemId(itemId) == null) {
            dao.insertListEntry(
                ListEntry(
                    itemId = itemId,
                    checkedInCart = false,
                    qtyToBuy = null,
                    unit = null,
                    size = null,
                    priceOverrideCents = null
                )
            )
        }
    }

    suspend fun addToActiveListByItemId(itemId: Long) {
        if (dao.getListEntryIdByItemId(itemId) == null) {
            dao.insertListEntry(
                ListEntry(
                    itemId = itemId,
                    checkedInCart = false,
                    qtyToBuy = null,
                    unit = null,
                    size = null,
                    priceOverrideCents = null
                )
            )
        }
    }

    suspend fun toggleActiveListChecked(itemId: Long) {
        dao.toggleCheckedInCart(itemId)
    }

    suspend fun removeFromActiveList(itemId: Long) {
        dao.deleteListEntryByItemId(itemId)
    }

    suspend fun setQty(itemId: Long, qtyToBuy: Double?) {
        dao.setQtyToBuy(itemId, qtyToBuy)
    }

    suspend fun clearInCartForStore(storeId: Long) {
        dao.clearInCartForStore(storeId)
    }

    // ---------- Picklist ----------

    fun observePicklistForStore(storeId: Long, query: String): Flow<List<ShoppingDao.PicklistItemRow>> =
        dao.observePicklistForStore(storeId, query)

    fun observeAllItems(): Flow<List<Item>> = dao.observeAllItems()

    fun searchItems(query: String): Flow<List<Item>> = dao.searchItems(query)

    suspend fun deleteCatalogItem(itemId: Long) {
        dao.deleteItemById(itemId)
    }

    // ---------- Edit Item ----------

    suspend fun getItemById(itemId: Long): Item? = dao.getItemById(itemId)

    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem> =
        dao.getStoreItemsForItemOnce(itemId)

    suspend fun upsertItem(item: Item): Long {
        return if (item.id == 0L) {
            val inserted = dao.insertItem(item)
            if (inserted > 0) inserted else (dao.getItemIdByName(item.name) ?: -1L)
        } else {
            dao.updateItem(item)
            item.id
        }
    }

    suspend fun saveItemForStoreWithAisle(
        item: Item,
        storeId: Long,
        aisle: String?,
        showIfAisleUnassigned: Boolean,
        priceOverrideCents: Int?
    ) {
        upsertItem(item)
        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = item.id,
                aisle = aisle?.trim()?.ifEmpty { null },
                showIfAisleUnassigned = showIfAisleUnassigned,
                priceOverrideCents = priceOverrideCents
            )
        )
    }

    // ---------- Typeahead helpers ----------

    fun observeItemSuggestions(query: String, limit: Int = 10): Flow<List<Item>> =
        dao.observeItemSuggestions(query, limit)

    fun observeAisleSuggestions(storeId: Long, query: String, limit: Int = 10): Flow<List<String>> =
        dao.observeAisleSuggestions(storeId, query, limit)
}
