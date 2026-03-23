package com.reese.shoppinglist.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reese.shoppinglist.data.Item
import com.reese.shoppinglist.data.ShoppingDao
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StoreItem
import com.reese.shoppinglist.data.StoreItemDisplay
import com.reese.shoppinglist.data.StorePrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,

    // Home list — grouped by aisle
    val needToGetEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),
    val inCartEntriesByAisle: Map<String, List<ShoppingDao.ListEntryRow>> = emptyMap(),
    val showStoreSpecificOnly: Boolean = false,

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

class ShoppingViewModel(private val repository: ShoppingRepository, private val storePrefs: StorePrefs) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var activeListJob: Job? = null
    private var picklistJob: Job? = null
    private var storeItemsJob: Job? = null
    private var addItemSuggestJob: Job? = null
    private var aisleSuggestJob: Job? = null

    private var prefsLoaded = false
    private var savedStoreId: Long? = null

    init {
        viewModelScope.launch { repository.ensureDefaultStore() }

        viewModelScope.launch {
            storePrefs.selectedStoreId.collect { id ->
                prefsLoaded = true
                savedStoreId = id
                applyStoreSelection(_uiState.value.stores)
            }
        }

        viewModelScope.launch {
            storePrefs.showStoreSpecificOnly.collect { value ->
                _uiState.value = _uiState.value.copy(showStoreSpecificOnly = value)
                val storeId = _uiState.value.selectedStoreId ?: return@collect
                startCollectingActiveList(storeId)
                startCollectingPicklist(storeId, _uiState.value.picklistQuery)
            }
        }

        viewModelScope.launch {
            repository.stores.collect { stores ->
                _uiState.value = _uiState.value.copy(stores = stores)
                applyStoreSelection(stores)
            }
        }
    }

    // ---------- Stores ----------

    fun selectStore(storeId: Long) {
        _uiState.value = _uiState.value.copy(selectedStoreId = storeId)
        viewModelScope.launch {
            storePrefs.setSelectedStoreId(storeId)
            savedStoreId = storeId
        }
        startCollectingActiveList(storeId)
        startCollectingPicklist(storeId, _uiState.value.picklistQuery)
        startCollectingStoreItems(storeId)
    }

    private fun applyStoreSelection(stores: List<Store>) {
        val current = _uiState.value.selectedStoreId
        val desired = when {
            savedStoreId != null && stores.any { it.id == savedStoreId } -> savedStoreId
            current != null && stores.any { it.id == current } -> current
            else -> stores.firstOrNull()?.id
        }

        if (desired != null && desired != _uiState.value.selectedStoreId) {
            _uiState.value = _uiState.value.copy(selectedStoreId = desired)
            startCollectingActiveList(desired)
            startCollectingPicklist(desired, _uiState.value.picklistQuery)
            startCollectingStoreItems(desired)
        }

        if (prefsLoaded && desired != null && desired != savedStoreId) {
            viewModelScope.launch { storePrefs.setSelectedStoreId(desired) }
            savedStoreId = desired
        }
    }

    fun setStoreSpecificFilter(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showStoreSpecificOnly = enabled)
        viewModelScope.launch { storePrefs.setShowStoreSpecificOnly(enabled) }
        val storeId = _uiState.value.selectedStoreId ?: return
        startCollectingActiveList(storeId)
        startCollectingPicklist(storeId, _uiState.value.picklistQuery)
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

    private fun startCollectingActiveList(storeId: Long) {
        activeListJob?.cancel()
        activeListJob = viewModelScope.launch {
            val filter = _uiState.value.showStoreSpecificOnly
            repository.observeActiveListForStore(storeId, filter).collect { rows ->
                val need = rows.filter { !it.checkedInCart }
                val cart = rows.filter { it.checkedInCart }
                _uiState.value = _uiState.value.copy(
                    needToGetEntriesByAisle = groupByAisle(need),
                    inCartEntriesByAisle = groupByAisle(cart)
                )
            }
        }
    }

    private fun groupByAisle(list: List<ShoppingDao.ListEntryRow>): Map<String, List<ShoppingDao.ListEntryRow>> =
        list.groupBy { (it.aisle ?: "").trim().ifEmpty { "Unassigned" } }

    fun addToNeedToGet(name: String, aisle: String = "") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val storeId = _uiState.value.selectedStoreId ?: _uiState.value.stores.firstOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(addItemSuggestions = emptyList(), aisleSuggestions = emptyList())
            repository.addItemByNameToStoreAndList(trimmed, storeId, aisle.trim().takeIf { it.isNotEmpty() })
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
        viewModelScope.launch { repository.clearAllInCart() }
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
            val filter = _uiState.value.showStoreSpecificOnly
            repository.observePicklistForStore(storeId, query.trim(), filter).collect { rows ->
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
            _uiState.value = _uiState.value.copy(editingItem = item, editingStoreItems = storeItems)
        }
    }

    fun clearEditingItem() {
        _uiState.value = _uiState.value.copy(editingItem = null, editingStoreItems = emptyList())
    }

    fun saveItemForSelectedStore(
        item: Item,
        storeId: Long,
        aisle: String?,
        isStoreSpecific: Boolean,
        priceOverrideCents: Int?,
        applyAisleToAllStores: Boolean = false
    ) {
        viewModelScope.launch {
            repository.saveItemForStoreWithAisle(
                item = item,
                storeId = storeId,
                aisle = aisle,
                isStoreSpecific = isStoreSpecific,
                priceOverrideCents = priceOverrideCents,
                applyAisleToAllStores = applyAisleToAllStores
            )
        }
    }

    // ---------- Export / Import ----------

    fun exportData(context: Context, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportData()
                val file = java.io.File(context.getExternalFilesDir(null), "shopping_backup.json")
                file.writeText(json)
                onDone("Exported to:\n${file.absolutePath}")
            } catch (e: Exception) {
                onDone("Export failed: ${e.message}")
            }
        }
    }

    fun importData(context: Context, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "shopping_backup.json")
                if (!file.exists()) {
                    onDone("No backup file found at:\n${file.absolutePath}\n\nCopy your backup there first.")
                    return@launch
                }
                val count = repository.importData(file.readText())
                onDone("Imported $count items successfully.")
            } catch (e: Exception) {
                onDone("Import failed: ${e.message}")
            }
        }
    }

    fun importLegacyItems(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val count = repository.importLegacyCsvItems()
            onDone("Added $count new items from legacy list.")
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
        aisleSuggestJob = viewModelScope.launch {
            delay(150)
            repository.observeAisleSuggestions(sid, query.trim(), 30).collect { list ->
                _uiState.value = _uiState.value.copy(aisleSuggestions = list)
            }
        }
    }
}

class ShoppingViewModelFactory(
    private val repository: ShoppingRepository,
    private val storePrefs: StorePrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ShoppingViewModel(repository, storePrefs) as T
}
