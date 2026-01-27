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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,

    // Home list
    val storeSpecificOnly: Boolean = false,
    val needToGetEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val inCartEntries: List<ShoppingDao.ListEntryRow> = emptyList(),
    val needToGetEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),
    val inCartEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),

    // Picklist
    val picklistQuery: String = "",
    val picklistRows: List<ShoppingDao.PicklistItemRow> = emptyList(),

    // Typeahead (Add Item)
    val addItemSuggestions: List<Item> = emptyList(),

    // Edit Item
    val editingItem: Item? = null,
    val editingStoreItems: List<StoreItem> = emptyList(),
    val aisleSuggestions: List<String> = emptyList(),

    // Store screen
    val storeItems: List<StoreItemDisplay> = emptyList()
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var storesJob: Job? = null
    private var activeListJob: Job? = null
    private var picklistJob: Job? = null
    private var storeItemsJob: Job? = null

    private var addItemSuggestJob: Job? = null
    private var aisleSuggestJob: Job? = null

    init {
        startCollectingStores()
    }

    // ---------- Stores ----------

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
                    startCollectingPicklist(selected, _uiState.value.picklistQuery)
                    startCollectingStoreItems(selected)
                }
            }
        }
    }

    fun selectStore(storeId: Long) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        startCollectingActiveList(storeId)
        startCollectingPicklist(storeId, _uiState.value.picklistQuery)
        startCollectingStoreItems(storeId)
    }

    fun addStore(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addStore(trimmed) }
    }

    fun deleteStore(storeId: Long) {
        viewModelScope.launch { repository.deleteStore(storeId) }
    }

    // ---------- Home list ----------

    fun setStoreSpecificOnly(enabled: Boolean) {
        if (_uiState.value.storeSpecificOnly == enabled) return
        _uiState.value = _uiState.value.copy(storeSpecificOnly = enabled)
        val storeId = _uiState.value.selectedStoreId ?: return
        startCollectingActiveList(storeId)
    }

    private fun startCollectingActiveList(storeId: Long) {
        activeListJob?.cancel()
        val storeOnly = _uiState.value.storeSpecificOnly
        activeListJob = viewModelScope.launch {
            repository.observeActiveListForStore(storeId, storeOnly).collect { rows ->
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
        val storeId = _uiState.value.selectedStoreId ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addItemByNameToStoreAndList(trimmed, storeId)
        }
    }

    fun toggleNeedToGetChecked(itemId: Long) {
        viewModelScope.launch { repository.toggleActiveListChecked(itemId) }
    }

    fun removeNeedToGetEntry(itemId: Long) {
        viewModelScope.launch { repository.removeFromActiveList(itemId) }
    }

    fun setNeedToGetQty(itemId: Long, qty: Double?) {
        viewModelScope.launch { repository.setQty(itemId, qty) }
    }

    fun checkoutConfirm() {
        val storeId = _uiState.value.selectedStoreId ?: return
        viewModelScope.launch { repository.clearInCartForStore(storeId) }
    }

    // ---------- Picklist ----------

    fun setPicklistQuery(query: String) {
        _uiState.value = _uiState.value.copy(picklistQuery = query)
        val storeId = _uiState.value.selectedStoreId ?: return
        startCollectingPicklist(storeId, query)
    }

    private fun startCollectingPicklist(storeId: Long, query: String) {
        picklistJob?.cancel()
        picklistJob = viewModelScope.launch {
            repository.observePicklistForStore(storeId, query.trim()).collect { rows ->
                _uiState.value = _uiState.value.copy(picklistRows = rows)
            }
        }
    }

    fun addFromPicklist(itemId: Long) {
        viewModelScope.launch { repository.addToActiveListByItemId(itemId) }
    }

    fun deleteFromPicklist(itemId: Long) {
        viewModelScope.launch { repository.deleteCatalogItem(itemId) }
    }

    // ---------- Store screen ----------

    private fun startCollectingStoreItems(storeId: Long) {
        storeItemsJob?.cancel()
        storeItemsJob = viewModelScope.launch {
            repository.storeItems(storeId).collect { rows ->
                _uiState.value = _uiState.value.copy(storeItems = rows)
            }
        }
    }

    // ---------- Edit Item ----------

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

    // ---------- Typeahead ----------

    fun setAddItemTypeahead(query: String) {
        val q = query.trim()
        addItemSuggestJob?.cancel()

        if (q.length < 2) {
            _uiState.value = _uiState.value.copy(addItemSuggestions = emptyList())
            return
        }

        addItemSuggestJob = viewModelScope.launch {
            delay(150)
            repository.observeItemSuggestions(q, 10).collect { list ->
                _uiState.value = _uiState.value.copy(addItemSuggestions = list)
            }
        }
    }

    fun setAisleTypeahead(storeId: Long?, query: String) {
        aisleSuggestJob?.cancel()

        val sid = storeId ?: run {
            _uiState.value = _uiState.value.copy(aisleSuggestions = emptyList())
            return
        }

        val q = query.trim()
        if (q.length < 1) {
            _uiState.value = _uiState.value.copy(aisleSuggestions = emptyList())
            return
        }

        aisleSuggestJob = viewModelScope.launch {
            delay(150)
            repository.observeAisleSuggestions(sid, q, 10).collect { list ->
                _uiState.value = _uiState.value.copy(aisleSuggestions = list)
            }
        }
    }
}

class ShoppingViewModelFactory(
    private val repository: ShoppingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
