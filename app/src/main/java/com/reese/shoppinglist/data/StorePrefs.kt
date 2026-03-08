package com.reese.shoppinglist.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "shopping_prefs")

class StorePrefs(private val context: Context) {

    private val KEY_SELECTED_STORE_ID = longPreferencesKey("selected_store_id")

    val selectedStoreId: Flow<Long?> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_SELECTED_STORE_ID]
        }

    suspend fun setSelectedStoreId(id: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_STORE_ID] = id
        }
    }

    suspend fun clearSelectedStoreId() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SELECTED_STORE_ID)
        }
    }
}
