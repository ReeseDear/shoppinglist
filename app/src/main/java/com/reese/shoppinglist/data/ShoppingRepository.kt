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
        return dao.insertStore(Store(name = name.trim()))
    }

    suspend fun deleteStore(storeId: Long) {
        dao.deleteStoreById(storeId)
    }

    // --- Picklist items ---
    fun observeAllItems(): Flow<List<Item>> = dao.observeAllItems()

    fun searchItems(query: String): Flow<List<Item>> = dao.searchItems(query)

    fun observeItemsForStore(storeId: Long): Flow<List<Item>> = dao.observeItemsForStore(storeId)

    fun searchItemsForStore(storeId: Long, query: String): Flow<List<Item>> =
        dao.searchItemsForStore(storeId, query)

    suspend fun deleteCatalogItem(itemId: Long) {
        dao.deleteItemById(itemId)
    }

    // --- Edit Item ---
    suspend fun getItemById(itemId: Long): Item? = dao.getItemById(itemId)

    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem> =
        dao.getStoreItemsForItemOnce(itemId)

    /**
     * Save catalog-level item fields + store-specific fields (aisle, store price, visibility flag).
     */
    suspend fun saveItemForStoreWithAisle(
        item: Item,
        storeId: Long,
        aisle: String?,
        showIfAisleUnassigned: Boolean,
        priceOverrideCents: Int? = null
    ): Long {
        // Save catalog item fields
        dao.updateItem(item)

        // Save store-specific fields
        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = item.id,
                aisle = aisle?.trim()?.ifEmpty { null },
                showIfAisleUnassigned = showIfAisleUnassigned,
                priceOverrideCents = priceOverrideCents
            )
        )

        return item.id
    }

    // --- Active list ---
    fun observeActiveListForStore(storeId: Long): Flow<List<ShoppingDao.ListEntryRow>> =
        dao.observeListEntriesForStore(storeId)

    suspend fun addToActiveListByItemId(itemId: Long) {
        val existing = dao.getListEntryByItemId(itemId)
        if (existing == null) {
            dao.upsertListEntry(
                ListEntry(
                    itemId = itemId,
                    checkedInCart = false,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeFromActiveList(itemId: Long) {
        dao.deleteListEntryByItemId(itemId)
    }

    suspend fun toggleChecked(itemId: Long) {
        val existing = dao.getListEntryByItemId(itemId) ?: return
        dao.upsertListEntry(existing.copy(checkedInCart = !existing.checkedInCart))
    }

    suspend fun setQty(itemId: Long, qty: Double?) {
        val existing = dao.getListEntryByItemId(itemId) ?: return
        dao.upsertListEntry(existing.copy(qtyToBuy = qty))
    }

    // --- Store screen list ---
    fun observeStoreItems(storeId: Long): Flow<List<StoreItemDisplay>> =
        dao.getStoreItems(storeId)
}
