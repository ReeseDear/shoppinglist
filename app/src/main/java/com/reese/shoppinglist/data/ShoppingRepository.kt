package com.reese.shoppinglist.data

import kotlinx.coroutines.flow.Flow
import com.reese.shoppinglist.data.ShoppingDao
import com.reese.shoppinglist.data.Store

class ShoppingRepository(private val dao: ShoppingDao) {
    val stores: Flow<List<Store>> = dao.getStores()

    fun storeItems(storeId: Long): Flow<List<StoreItemDisplay>> = dao.getStoreItems(storeId)

    suspend fun ensureDefaultStore(): Long {
        if (dao.getStoreCount() == 0) {
            return dao.insertStore(Store(name = "Default Store"))
        }
        // If you want the first store id, simplest is: insert none and pick from Flow in ViewModel.
        return -1
    }

    suspend fun addItemToStore(storeId: Long, name: String, aisle: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val itemId = dao.getItemIdByName(trimmedName) ?: run {
            val inserted = dao.insertItem(Item(name = trimmedName))
            if (inserted != -1L) inserted else (dao.getItemIdByName(trimmedName) ?: return)
        }

        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = itemId,
                aisle = aisle.trim().ifEmpty { "Unassigned" },
                inCart = false
            )
        )
    }

    suspend fun toggleInCart(row: StoreItemDisplay) {
        dao.updateStoreItem(
            StoreItem(
                storeId = row.storeId,
                itemId = row.itemId,
                aisle = row.aisle,
                inCart = !row.inCart,
                createdAt = row.createdAt
            )
        )
    }

    suspend fun deleteFromStore(row: StoreItemDisplay) {
        dao.deleteStoreItem(row.storeId, row.itemId)
    }
}