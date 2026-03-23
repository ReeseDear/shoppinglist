package com.reese.shoppinglist.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class ShoppingRepository(private val dao: ShoppingDao) {

    // ---------- Stores ----------

    val stores: Flow<List<Store>> = dao.getStores()

    suspend fun ensureDefaultStore(): Long {
        return if (dao.getStoreCount() == 0) {
            dao.insertStore(Store(name = "Default Store"))
        } else {
            -1L
        }
    }

    suspend fun addStore(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertStore(Store(name = trimmed))
    }

    suspend fun deleteStore(storeId: Long) {
        dao.deleteStoreById(storeId)
    }

    // ---------- Store Items (Store screen) ----------

    fun storeItems(storeId: Long): Flow<List<StoreItemDisplay>> = dao.getStoreItems(storeId)

    // ---------- Home list (Active list) ----------

    fun observeActiveListForStore(storeId: Long, filterByStore: Boolean): Flow<List<ShoppingDao.ListEntryRow>> =
        dao.observeListEntriesForStore(storeId, filterByStore)

    suspend fun addItemByNameToStoreAndList(name: String, storeId: Long, aisle: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val existing = dao.getItemByExactName(trimmed)
        val itemId = if (existing != null) {
            existing.id
        } else {
            val newId = dao.insertItem(Item(name = trimmed))
            if (newId > 0) newId else (dao.getItemByExactName(trimmed)?.id ?: return)
        }

        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = itemId,
                aisle = aisle?.trim()?.takeIf { it.isNotEmpty() },
                isStoreSpecific = false,
                priceOverrideCents = null
            )
        )

        val currentEntryId = dao.getListEntryIdByItemId(itemId)
        if (currentEntryId == null) {
            dao.insertListEntry(ListEntry(itemId = itemId, checkedInCart = false))
        } else {
            dao.updateListEntryChecked(itemId, false)
        }
    }

    suspend fun addToActiveListByItemId(itemId: Long) {
        if (dao.getListEntryIdByItemId(itemId) == null) {
            dao.insertListEntry(ListEntry(itemId = itemId, checkedInCart = false))
        }
    }

    suspend fun toggleActiveListChecked(itemId: Long) {
        dao.toggleCheckedInCart(itemId)
    }

    suspend fun removeFromActiveList(itemId: Long) {
        dao.deleteListEntryByItemId(itemId)
    }

    suspend fun setQty(itemId: Long, qtyToBuy: Double?) {
        dao.setQtyToBuy(itemId, qtyToBuy)
    }

    suspend fun clearAllInCart() {
        dao.clearAllInCart()
    }

    // ---------- Picklist ----------

    fun observePicklistForStore(storeId: Long, query: String, filterByStore: Boolean): Flow<List<ShoppingDao.PicklistItemRow>> =
        dao.observePicklistForStore(storeId, query, filterByStore)

    suspend fun deleteCatalogItem(itemId: Long) {
        dao.deleteItemById(itemId)
    }

    // ---------- Edit Item ----------

    suspend fun getItemById(itemId: Long): Item? = dao.getItemById(itemId)

    suspend fun getStoreItemsForItemOnce(itemId: Long): List<StoreItem> =
        dao.getStoreItemsForItemOnce(itemId)

    suspend fun upsertItem(item: Item): Long {
        return if (item.id == 0L) {
            val inserted = dao.insertItem(item)
            if (inserted > 0) inserted else (dao.getItemByExactName(item.name)?.id ?: -1L)
        } else {
            dao.updateItem(item)
            item.id
        }
    }

    suspend fun saveItemForStoreWithAisle(
        item: Item,
        storeId: Long,
        aisle: String?,
        isStoreSpecific: Boolean,
        priceOverrideCents: Int?,
        applyAisleToAllStores: Boolean = false
    ) {
        val cleanAisle = aisle?.trim()?.ifEmpty { null }
        upsertItem(item)

        if (applyAisleToAllStores) {
            dao.updateAisleForAllStores(item.id, cleanAisle)
            val existingStoreIds = dao.getStoreItemsForItemOnce(item.id).map { it.storeId }.toSet()
            dao.getStoresOnce().forEach { store ->
                if (store.id !in existingStoreIds) {
                    dao.upsertStoreItem(StoreItem(storeId = store.id, itemId = item.id, aisle = cleanAisle))
                }
            }
        }

        // Always write selected store with full details (price + isStoreSpecific)
        dao.upsertStoreItem(
            StoreItem(
                storeId = storeId,
                itemId = item.id,
                aisle = cleanAisle,
                isStoreSpecific = isStoreSpecific,
                priceOverrideCents = priceOverrideCents
            )
        )
    }

    // ---------- Typeahead helpers ----------

    fun observeItemSuggestions(query: String, limit: Int = 10): Flow<List<Item>> =
        dao.observeItemSuggestions(query, limit)

    fun observeAisleSuggestions(storeId: Long, query: String, limit: Int = 10): Flow<List<String>> =
        dao.observeAisleSuggestions(storeId, query, limit)

    // ---------- Export / Import ----------

    suspend fun exportData(): String {
        val items = dao.getAllItemsOnce()
        val stores = dao.getStoresOnce()
        val storeItems = dao.getAllStoreItemsOnce()

        val storeMap = stores.associateBy { it.id }
        val storeItemsByItemId = storeItems.groupBy { it.itemId }

        val root = JSONObject()
        root.put("version", 1)

        val itemsArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("name", item.name)
            if (item.defaultPriceCents != null) obj.put("defaultPriceCents", item.defaultPriceCents)
            obj.put("taxable", item.taxable)
            if (item.defaultQuantity != null) obj.put("defaultQuantity", item.defaultQuantity)
            if (item.defaultUnit != null) obj.put("defaultUnit", item.defaultUnit)
            if (item.defaultSize != null) obj.put("defaultSize", item.defaultSize)
            if (item.notes != null) obj.put("notes", item.notes)

            val assignments = JSONArray()
            storeItemsByItemId[item.id]?.forEach { si ->
                val store = storeMap[si.storeId] ?: return@forEach
                val a = JSONObject()
                a.put("storeName", store.name)
                if (si.aisle != null) a.put("aisle", si.aisle)
                if (si.priceOverrideCents != null) a.put("priceOverrideCents", si.priceOverrideCents)
                a.put("showIfAisleUnassigned", si.isStoreSpecific)
                assignments.put(a)
            }
            obj.put("storeAssignments", assignments)
            itemsArray.put(obj)
        }
        root.put("items", itemsArray)
        return root.toString(2)
    }

    suspend fun importData(json: String): Int {
        val root = JSONObject(json)
        val stores = dao.getStoresOnce()
        val storeByName = stores.associateBy { it.name }

        val itemsArray = root.getJSONArray("items")
        var count = 0
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            val name = obj.getString("name").trim()
            if (name.isEmpty()) continue

            val existing = dao.getItemByExactName(name)
            val itemId = if (existing != null) {
                existing.id
            } else {
                val newItem = Item(
                    name = name,
                    defaultPriceCents = if (obj.has("defaultPriceCents")) obj.getInt("defaultPriceCents") else null,
                    taxable = obj.optBoolean("taxable", true),
                    defaultQuantity = if (obj.has("defaultQuantity")) obj.getDouble("defaultQuantity") else null,
                    defaultUnit = if (obj.has("defaultUnit")) obj.getString("defaultUnit") else null,
                    defaultSize = if (obj.has("defaultSize")) obj.getString("defaultSize") else null,
                    notes = if (obj.has("notes")) obj.getString("notes") else null
                )
                val id = dao.insertItem(newItem)
                if (id > 0) id else (dao.getItemByExactName(name)?.id ?: continue)
            }

            val assignments = obj.optJSONArray("storeAssignments")
            if (assignments != null) {
                for (j in 0 until assignments.length()) {
                    val a = assignments.getJSONObject(j)
                    val store = storeByName[a.getString("storeName")] ?: continue
                    dao.upsertStoreItem(
                        StoreItem(
                            storeId = store.id,
                            itemId = itemId,
                            aisle = if (a.has("aisle")) a.getString("aisle").ifEmpty { null } else null,
                            priceOverrideCents = if (a.has("priceOverrideCents")) a.getInt("priceOverrideCents") else null,
                            isStoreSpecific = a.optBoolean("showIfAisleUnassigned", true)
                        )
                    )
                }
            }
            count++
        }
        return count
    }

    suspend fun importLegacyCsvItems(): Int {
        val legacyItems = listOf(
            "Valerium root", "Beef Sausage", "Beans and rice", "Peanuts", "Tater tots",
            "Beef jerky", "tone soap", "Swiss miss", "Gum", "Tide", "Water", "Eggs",
            "Sandwhich meats", "Fruits", "Mayonnaise", "Ketchup", "Evaporated milk",
            "Banana boat dark tanning oil", "Neutrogena", "Fish fry", "Shampoo", "Conditioner",
            "Ice Cream", "Rotella motor oil", "Canned peaches", "Pineapple",
            "Breakfast sausage", "Maple syrup", "Roll-aids", "Chili", "Garlic bread",
            "Tuna helper", "Q Tips", "Biscuits", "Ritz crackers", "Salsa", "Chocolate",
            "Sunflower seeds", "almonds", "Breakfast Cereal", "Bread", "Milk",
            "Orange juice", "Bacon", "Potatoes", "Toilet Paper", "Paper Towels",
            "Anti-itch medicine", "Tortilla Chips", "Pancake Mix", "Ground Beef",
            "Neosporin", "Shrimp", "Country Crock Spread", "Butter", "Shake and bake",
            "Chicken Legs", "Stir Fry vegetables", "Stew meat", "Tomatoes", "Listerine",
            "Tomato Paste", "Tomato Sauce", "Deodorant", "Parmesan cheese", "Prego sauce",
            "Eye drops", "Alcohol", "Beef broth", "Sage", "Holy basil", "Rosemary",
            "Garlic", "Cashews", "Beef stew seasoning", "Corn", "Sugar", "Peanut Butter",
            "Corn meal", "Zucchini", "Watermelon", "Strawberries", "Plums", "Peppers",
            "Onions", "Lettuce", "Limes", "Lemons", "Grapes", "Cucumbers", "Cherries",
            "Carrots", "Broccoli", "Berries", "Bananas", "Asparagus", "Apples",
            "Rib Eye Steak", "Baking Powder", "Baking Soda", "Batteries", "Bleach",
            "Bottled Water", "Chicken and Rice Soup", "Chicken Noodle Soup", "Raisins",
            "Gatorade", "Tea", "mini blind", "El. Cook top cleaner", "Vinegar",
            "Bed Sheet", "Large trash bags", "Air filters", "Spaghetti", "Popcorn",
            "Chicken", "salami", "Cheese", "lemon", "blackberries", "truvia",
            "beef pot pie", "turkey pot pie", "saran wrap", "melatonin", "Sponges",
            "steel wool 0000", "Shaving Cream", "Parsley", "Oatmill C & S",
            "Oatmill M & BS", "Oatmill A & C", "Casava/Cauliflower Chips", "Toothpaste",
            "bubble bath", "dried banana chips", "pistachios", "Tuna",
            "cfl 25watt softwhite", "hibiscus", "hawthorn berry", "canned cat food",
            "kitty litter", "de wormer", "3d white strips"
        )
        var count = 0
        for (name in legacyItems) {
            if (dao.getItemByExactName(name) == null) {
                dao.insertItem(Item(name = name))
                count++
            }
        }
        return count
    }
}
