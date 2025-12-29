package com.reese.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reese.shoppinglist.data.Item
import com.reese.shoppinglist.data.ShoppingDao
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StoreItemDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,

    // Existing per-store view (aisle is nullable)
    val needToGet: Map<String?, List<StoreItemDisplay>> = emptyMap(),
    val inCart: Map<String?, List<StoreItemDisplay>> = emptyMap(),
    val needToGetCount: Int = 0,
    val inCartCount: Int = 0,

    // Active list split into two sections for Home UI
    val needToGetEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val inCartEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val listEntryCount: Int = 0,

    // Picklist
    val picklistQuery: String = "",
    val picklistItems: List<Item> = emptyList()
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var storeItemsJob: Job? = null
    private var listEntriesJob: Job? = null
    private var picklistJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureDefaultStore() }

        viewModelScope.launch {
            repository.stores.collect { stores ->
                val selected = _uiState.value.selectedStoreId ?: stores.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    stores = stores,
                    selectedStoreId = selected
                )

                if (selected != null) startCollectingStoreItems(selected)
            }
        }

        startCollectingActiveList()
        startCollectingPicklist("") // default: all items
    }

    fun selectStore(storeId: Long) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        startCollectingStoreItems(storeId)
    }

    private fun startCollectingStoreItems(storeId: Long) {
        storeItemsJob?.cancel()
        storeItemsJob = viewModelScope.launch {
            repository.storeItems(storeId).collect { rows ->
                val need = rows.filter { !it.inCart }.groupBy { it.aisle }
                val cart = rows.filter { it.inCart }.groupBy { it.aisle }

                _uiState.value = _uiState.value.copy(
                    needToGet = need,
                    inCart = cart,
                    needToGetCount = rows.count { !it.inCart },
                    inCartCount = rows.count { it.inCart }
                )
            }
        }
    }

    private fun startCollectingActiveList() {
        listEntriesJob?.cancel()
        listEntriesJob = viewModelScope.launch {
            repository.observeActiveList().collect { rows ->
                val need = rows.filter { !it.checkedInCart }
                val cart = rows.filter { it.checkedInCart }

                _uiState.value = _uiState.value.copy(
                    needToGetEntries = need,
                    inCartEntries = cart,
                    listEntryCount = rows.size
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
        viewModelScope.launch {
            repository.addToActiveListByItemId(itemId)
        }
    }

    fun deleteFromPicklist(itemId: Long) {
        viewModelScope.launch {
            repository.deleteCatalogItem(itemId)
        }
    }

    // --- Store-based (kept) ---
    fun addItem(name: String, aisle: String) {
        val storeId = _uiState.value.selectedStoreId ?: return
        if (storeId == -1L) return

        viewModelScope.launch {
            repository.addItemToStore(storeId, name, aisle)
        }
    }

    fun toggleItemCart(row: StoreItemDisplay) {
        viewModelScope.launch { repository.toggleInCart(row) }
    }

    fun deleteItem(row: StoreItemDisplay) {
        viewModelScope.launch { repository.deleteFromStore(row) }
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

    // Backward-compatible (if anything still calls these)
    fun setNeedToGetChecked(listEntryId: Long, checked: Boolean) {
        viewModelScope.launch { repository.setChecked(listEntryId, checked) }
    }

    fun deleteNeedToGetEntry(listEntryId: Long) {
        viewModelScope.launch { repository.deleteListEntry(listEntryId) }
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
