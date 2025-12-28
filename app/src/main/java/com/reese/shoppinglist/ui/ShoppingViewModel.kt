package com.reese.shoppinglist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StoreItemDisplay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val stores: List<Store> = emptyList(),
    val selectedStoreId: Long? = null,

    val needToGet: Map<String, List<StoreItemDisplay>> = emptyMap(),
    val inCart: Map<String, List<StoreItemDisplay>> = emptyMap(),
    val needToGetCount: Int = 0,
    val inCartCount: Int = 0
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private var storeItemsJob: kotlinx.coroutines.Job? = null

    init {
        // Keep store list updated
        viewModelScope.launch {
            repository.stores.collect { stores ->
                val currentSelected = _uiState.value.selectedStoreId
                val selected = currentSelected ?: stores.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    stores = stores,
                    selectedStoreId = selected
                )

                if (selected != null) startCollectingStoreItems(selected)
            }
        }
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

    fun addItem(name: String, aisle: String) {
        val storeId = _uiState.value.selectedStoreId ?: return
        viewModelScope.launch {
            repository.addItemToStore(storeId, name, aisle)
        }
    }

    fun toggleItemCart(row: StoreItemDisplay) {
        viewModelScope.launch {
            repository.toggleInCart(row)
        }
    }

    fun deleteItem(row: StoreItemDisplay) {
        viewModelScope.launch {
            repository.deleteFromStore(row)
        }
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
