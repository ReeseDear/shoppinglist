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
    val needToGetEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),
    val inCartEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),

    // Picklist
    val picklistQuery: String = "",
    val picklistShowAll: Boolean = false,
    val picklistItems: List<Item> = emptyList(),

    // Edit Item
    val editingItem: Item? = null,
    val editingStoreItems: List<StoreItem> = emptyList(),

    // Store screen
    val storeItems: List<StoreItemDisplay> = emptyList()
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var activeListJob: Job? = null
    private var picklistJob: Job? = null
    private var storesJob: Job? = null
    private var storeItemsJob: Job? = null

    init {
        startCollectingStores()
        startCollectingPicklist("")
    }

    // --- Stores ---
    private fun startCollectingStores() {
        storesJob?.cancel()
        storesJob = viewModelScope.launch {
            repository.stores.collect { stores ->
                val currentSelected = _uiState.value.selectedStoreId
                val selected = currentSelected ?: stores.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    stores = stores,
                    selectedStoreId = selected
                )

                if (selected != null) {
                    startCollectingActiveList(selected)
                    startCollectingStoreItems(selected)
                }
            }
        }
    }

    fun selectStore(storeId: Long) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        startCollectingActiveList(storeId)
        startCollectingStoreItems(storeId)
        // Refresh picklist because store scope may have changed
        startCollectingPicklist(_uiState.value.picklistQuery)
    }

    fun addStore(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addStore(trimmed)
        }
    }

    fun deleteStore(storeId: Long) {
        viewModelScope.launch {
            repository.deleteStore(storeId)
        }
    }

    // --- Active List ---
    private fun startCollectingActiveList(storeId: Long) {
        activeListJob?.cancel()
        activeListJob = viewModelScope.launch {
            repository.observeActiveListForStore(storeId).collect { rows ->
                val need = rows.filter { !it.checkedInCart }
                val cart = rows.filter { it.checkedInCart }

                fun groupByAisle(list: List<ShoppingDao.ListEntryRow>): Map<String, List<ShoppingDao.ListEntryRow>> {
                    return list.groupBy { (it.aisle ?: "").trim().ifEmpty { "Unassigned" } }
                }

                _uiState.value = _uiState.value.copy(
                    needToGetEntries = need,
                    inCartEntries = cart,
                    needToGetEntriesByAisle = groupByAisle(need),
                    inCartEntriesByAisle = groupByAisle(cart)
                )
            }
        }
    }

    fun addToNeedToGet(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val storeId = _uiState.value.selectedStoreId ?: return

        viewModelScope.launch {
            repository.addItemByNameToStoreAndList(trimmed, storeId)
        }
    }


    fun toggleNeedToGetChecked(itemId: Long) {
        viewModelScope.launch { repository.toggleChecked(itemId) }
    }

    fun removeNeedToGetEntry(itemId: Long) {
        viewModelScope.launch { repository.removeFromActiveList(itemId) }
    }

    fun setNeedToGetQty(itemId: Long, qty: Double?) {
        viewModelScope.launch { repository.setQty(itemId, qty) }
    }

    // --- Picklist ---
    fun setPicklistQuery(query: String) {
        _uiState.value = _uiState.value.copy(picklistQuery = query)
        startCollectingPicklist(query)
    }

    fun setPicklistShowAll(showAll: Boolean) {
        _uiState.value = _uiState.value.copy(picklistShowAll = showAll)
        startCollectingPicklist(_uiState.value.picklistQuery)
    }

    private fun startCollectingPicklist(query: String) {
        picklistJob?.cancel()
        picklistJob = viewModelScope.launch {
            val q = query.trim()
            val selectedStoreId = _uiState.value.selectedStoreId
            val storeFiltered = selectedStoreId != null && !_uiState.value.picklistShowAll

            val flow = when {
                storeFiltered && q.isEmpty() -> repository.observeItemsForStore(selectedStoreId!!)
                storeFiltered && q.isNotEmpty() -> repository.searchItemsForStore(selectedStoreId!!, q)
                !storeFiltered && q.isEmpty() -> repository.observeAllItems()
                else -> repository.searchItems(q)
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

    // --- Store screen items ---
    private fun startCollectingStoreItems(storeId: Long) {
        storeItemsJob?.cancel()
        storeItemsJob = viewModelScope.launch {
            repository.observeStoreItems(storeId).collect { rows ->
                _uiState.value = _uiState.value.copy(storeItems = rows)
            }
        }
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

    fun clearEditingItem() {
        _uiState.value = _uiState.value.copy(editingItem = null, editingStoreItems = emptyList())
    }

    fun saveItemForSelectedStore(
        item: Item,
        storeId: Long,
        aisle: String?,
        showIfAisleUnassigned: Boolean,
        priceOverrideCents: Int?
    ) {
        viewModelScope.launch {
            repository.saveItemForStoreWithAisle(
                item = item,
                storeId = storeId,
                aisle = aisle,
                showIfAisleUnassigned = showIfAisleUnassigned,
                priceOverrideCents = priceOverrideCents
            )
        }
    }
}

class ShoppingViewModelFactory(private val repository: ShoppingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
