package com.reese.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reese.shoppinglist.data.Item
import com.reese.shoppinglist.data.ShoppingDao
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StoreItem
import com.reese.shoppinglist.data.StoreItemDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,

    // Home list split into two sections
    val needToGetEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val inCartEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val listEntryCount: Int = 0,
    val needToGetEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),
    val inCartEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),

    // Picklist
    val picklistQuery: String = "",
    val picklistItems: List<Item> = emptyList(),

    // Edit Item
    val editingItem: Item? = null,
    val editingStoreItems: List<StoreItem> = emptyList()
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var listEntriesJob: Job? = null
    private var picklistJob: Job? = null

    private fun normalizeAisle(raw: String?): String =
        raw?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unassigned"

    init {
        viewModelScope.launch { repository.ensureDefaultStore() }

        viewModelScope.launch {
            repository.stores.collect { stores ->
                val selected = _uiState.value.selectedStoreId ?: stores.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    stores = stores,
                    selectedStoreId = selected
                )

                if (selected != null) {
                    startCollectingActiveList(selected)
                }
            }
        }

        startCollectingPicklist("")
    }

    fun selectStore(storeId: Long) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        startCollectingActiveList(storeId)
    }

    private fun startCollectingActiveList(storeId: Long) {
        listEntriesJob?.cancel()
        listEntriesJob = viewModelScope.launch {
            repository.observeActiveListForStore(storeId).collect { rows ->
                val need = rows.filter { !it.checkedInCart }
                val cart = rows.filter { it.checkedInCart }

                _uiState.value = _uiState.value.copy(
                    needToGetEntries = need,
                    inCartEntries = cart,
                    listEntryCount = rows.size,
                    needToGetEntriesByAisle = need.groupBy { normalizeAisle(it.aisle) },
                    inCartEntriesByAisle = cart.groupBy { normalizeAisle(it.aisle) }
                )
            }
        }
    }

    // --- Picklist ---
    fun setPicklistQuery(query: String) {
        _uiState.value = _uiState.value.copy(picklistQuery = query)
        startCollectingPicklist(query)
    }

    private fun startCollectingPicklist(query: String) {
        picklistJob?.cancel()
        picklistJob = viewModelScope.launch {
            val flow = if (query.trim().isEmpty()) {
                repository.observeAllItems()
            } else {
                repository.searchItems(query.trim())
            }

            flow.collect { items ->
                _uiState.value = _uiState.value.copy(picklistItems = items)
            }
        }
    }

    fun addFromPicklist(itemId: Long) {
        viewModelScope.launch { repository.addToActiveListByItemId(itemId) }
    }

    fun deleteFromPicklist(itemId: Long) {
        viewModelScope.launch { repository.deleteCatalogItem(itemId) }
    }

    // --- Active list (Home) ---
    fun addToNeedToGet(name: String) {
        viewModelScope.launch { repository.addToActiveListByName(name) }
    }

    fun toggleNeedToGetChecked(itemId: Long) {
        viewModelScope.launch { repository.toggleActiveListChecked(itemId) }
    }

    fun removeNeedToGetEntry(itemId: Long) {
        viewModelScope.launch { repository.removeFromActiveList(itemId) }
    }

    // ✅ INLINE QTY EDIT (persists)
    fun setNeedToGetQty(itemId: Long, qtyToBuy: Double?) {
        viewModelScope.launch { repository.setActiveListQty(itemId, qtyToBuy) }
    }

    // --- Edit Item ---
    fun loadItemForEdit(itemId: Long) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId)
            val storeItems = repository.getStoreItemsForItemOnce(itemId)

            _uiState.value = _uiState.value.copy(
                editingItem = item,
                editingStoreItems = storeItems
            )
        }
    }

    fun saveItemForSelectedStore(
        item: Item,
        storeId: Long,
        aisle: String?,
        priceOverrideCents: Int?
    ) {
        viewModelScope.launch {
            repository.saveItemForStoreWithAisle(item, storeId, aisle, priceOverrideCents)
        }
    }

    fun clearEditingItem() {
        _uiState.value = _uiState.value.copy(editingItem = null)
    }

    fun saveItem(updated: Item) {
        viewModelScope.launch {
            repository.upsertItem(updated)
            _uiState.value = _uiState.value.copy(editingItem = updated)
        }
    }

    // --- Stores management ---
    fun addStore(name: String) {
        viewModelScope.launch { repository.addStore(name) }
    }

    fun deleteStore(storeId: Long) {
        viewModelScope.launch { repository.deleteStore(storeId) }
    }
}

class ShoppingViewModelFactory(
    private val repository: ShoppingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
