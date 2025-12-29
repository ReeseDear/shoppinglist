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
                inCart = false
            )
        )
    }

    suspend fun deleteFromStore(row: StoreItemDisplay) {
        dao.deleteStoreItem(row.storeId, row.itemId)
    }

    suspend fun toggleInCart(row: StoreItemDisplay) {
        dao.upsertStoreItem(
            StoreItem(
                storeId = row.storeId,
                itemId = row.itemId,
                aisle = row.aisle,
                inCart = !row.inCart,
                createdAt = row.createdAt
            )
        )
    }

    // --- Active List (Home) ---
    fun observeActiveList(): Flow<List<ShoppingDao.ListEntryRow>> = dao.observeListEntries()

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

    // --- Picklist (Items catalog) ---
    fun observeAllItems(): Flow<List<Item>> = dao.observeAllItems()

    fun searchItems(query: String): Flow<List<Item>> = dao.searchItems(query)

    suspend fun deleteCatalogItem(itemId: Long) {
        dao.deleteItemById(itemId)
    }

    // --- Backward-compatible wrappers ---
    fun observeNeedToGet(): Flow<List<ShoppingDao.ListEntryRow>> = observeActiveList()

    suspend fun addByName(name: String) = addToActiveListByName(name)

    suspend fun setChecked(listEntryId: Long, checked: Boolean) {
        dao.setListEntryChecked(listEntryId, checked)
    }

    suspend fun deleteListEntry(listEntryId: Long) {
        dao.deleteListEntry(listEntryId)
    }
}
