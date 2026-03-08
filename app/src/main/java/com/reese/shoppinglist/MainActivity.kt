package com.reese.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reese.shoppinglist.data.Item
import com.reese.shoppinglist.data.ShoppingDatabase
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StorePrefs
import com.reese.shoppinglist.ui.ShoppingViewModel
import com.reese.shoppinglist.ui.ShoppingViewModelFactory
import com.reese.shoppinglist.ui.theme.ShoppingListTheme
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        val database = ShoppingDatabase.getDatabase(applicationContext)
        val repository = ShoppingRepository(database.shoppingDao())
        val prefs = com.reese.shoppinglist.data.StorePrefs(applicationContext)
        val storePrefs = StorePrefs(applicationContext)
        ShoppingViewModelFactory(repository, storePrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShoppingListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(viewModel = viewModel)
                }
            }
        }
    }
}

private enum class Screen { HOME, PICKLIST, EDIT_ITEM, STORES }

private data class Route(
    val screen: Screen,
    val editItemId: Long? = null
)

@Composable
fun AppRoot(viewModel: ShoppingViewModel) {
    // Simple navigation stack
    var backStack by remember {
        mutableStateOf(listOf(Route(Screen.HOME)))
    }

    fun currentRoute(): Route = backStack.last()

    fun push(route: Route) {
        backStack = backStack + route
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false

        val leaving = backStack.last()
        backStack = backStack.dropLast(1)

        // Cleanup when leaving Edit screen
        if (leaving.screen == Screen.EDIT_ITEM) {
            viewModel.clearEditingItem()
        }
        return true
    }

    // ✅ Phone back button: go to previous screen instead of closing the app
    BackHandler(enabled = backStack.size > 1) {
        pop()
    }

    when (val route = currentRoute()) {
        is Route -> {
            when (route.screen) {
                Screen.HOME -> {
                    ShoppingListScreen(
                        viewModel = viewModel,
                        onOpenPicklist = { push(Route(Screen.PICKLIST)) },
                        onOpenEditItem = { itemId -> push(Route(Screen.EDIT_ITEM, editItemId = itemId)) },
                        onOpenStores = { push(Route(Screen.STORES)) }
                    )
                }

                Screen.PICKLIST -> {
                    PicklistScreen(
                        viewModel = viewModel,
                        onDone = { pop() },
                        onOpenEditItem = { itemId ->
                            push(Route(Screen.EDIT_ITEM, editItemId = itemId))
                        },
                        onOpenStores = { push(Route(Screen.STORES)) }
                    )
                }


                Screen.STORES -> {
                    StoresScreen(
                        viewModel = viewModel,
                        onDone = { pop() }
                    )
                }

                Screen.EDIT_ITEM -> {
                    EditItemScreen(
                        viewModel = viewModel,
                        itemId = route.editItemId,
                        onBack = { pop() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingViewModel,
    onOpenPicklist: () -> Unit,
    onOpenEditItem: (Long) -> Unit,
    onOpenStores: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemName by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun sortAisleKeys(keys: Set<String>): List<String> {
        val unassigned = "Unassigned"
        return keys.sortedWith { a, b ->
            val aIsUn = a.equals(unassigned, ignoreCase = true)
            val bIsUn = b.equals(unassigned, ignoreCase = true)
            when {
                aIsUn && !bIsUn -> 1
                !aIsUn && bIsUn -> -1
                else -> a.lowercase(Locale.getDefault()).compareTo(b.lowercase(Locale.getDefault()))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping List") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    var storeMenuOpen by remember { mutableStateOf(false) }
                    val selectedStore = uiState.stores.firstOrNull { it.id == uiState.selectedStoreId }

                    TextButton(
                        onClick = { storeMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedStore?.name ?: "Select store",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DropdownMenu(
                        expanded = storeMenuOpen,
                        onDismissRequest = { storeMenuOpen = false }
                    ) {
                        uiState.stores.forEach { store ->
                            DropdownMenuItem(
                                text = { Text(store.name) },
                                onClick = {
                                    storeMenuOpen = false
                                    viewModel.selectStore(store.id)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = {
                            itemName = it
                            viewModel.setAddItemTypeahead(it)
                        },
                        label = { Text("Add item") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (itemName.isNotBlank()) {
                                    viewModel.addToNeedToGet(itemName)
                                    itemName = ""
                                    viewModel.setAddItemTypeahead("") // Clear suggestions immediately
                                    keyboardController?.hide() // Hide the keyboard
                                }
                            }
                        )
                    )

                    val suggestions = uiState.addItemSuggestions
                    DropdownMenu(
                        expanded = suggestions.isNotEmpty() && itemName.trim().length >= 2,
                        onDismissRequest = { viewModel.setAddItemTypeahead("") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = {
                                    itemName = s.name
                                    viewModel.setAddItemTypeahead("")
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.addToNeedToGet(itemName)
                                itemName = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = itemName.trim().isNotEmpty()
                        ) {
                            Text("Add")
                        }

                        Button(
                            onClick = onOpenPicklist,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Picklist")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val needGroups = uiState.needToGetEntriesByAisle
            val cartGroups = uiState.inCartEntriesByAisle

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Need",
                            count = uiState.needToGetEntries.size,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.storeSpecificOnly,
                                onCheckedChange = { viewModel.setStoreSpecificOnly(it) }
                            )
                            Text("Store specific only")
                        }
                    }
                }

                val needKeys = sortAisleKeys(needGroups.keys)
                needKeys.forEach { aisleName ->
                    val entries = needGroups[aisleName].orEmpty()
                    if (entries.isNotEmpty()) {
                        stickyHeader {
                            AisleHeader(title = aisleName, count = entries.size)
                        }

                        items(
                            items = entries,
                            key = { it.listEntryId }
                        ) { row ->
                            val qty = row.qtyToBuy ?: row.defaultQuantity ?: 1.0

                            val priceCents =
                                row.priceOverrideCents ?: row.storePriceOverrideCents ?: row.defaultPriceCents
                            val priceText =
                                priceCents?.let { cents -> "$" + "%.2f".format(cents / 100.0) } ?: ""

                            ActiveListRow(
                                itemId = row.itemId,
                                itemName = row.itemName,
                                qtyValue = qty,
                                metaRight = priceText,
                                checkedInCart = row.checkedInCart,
                                onToggle = { viewModel.toggleNeedToGetChecked(row.itemId) },
                                onRemove = { viewModel.removeNeedToGetEntry(row.itemId) },
                                onEdit = { onOpenEditItem(row.itemId) },
                                onSetQty = { newQty -> viewModel.setNeedToGetQty(row.itemId, newQty) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    InCartHeaderRow(
                        count = uiState.inCartEntries.size,
                        inCartEntries = uiState.inCartEntries,
                        onCheckoutConfirm = { viewModel.checkoutConfirm() }
                    )
                }


                val cartKeys = sortAisleKeys(cartGroups.keys)
                cartKeys.forEach { aisleName ->
                    val entries = cartGroups[aisleName].orEmpty()
                    if (entries.isNotEmpty()) {
                        stickyHeader {
                            AisleHeader(title = aisleName, count = entries.size)
                        }

                        items(
                            items = entries,
                            key = { it.listEntryId }
                        ) { row ->
                            val qty = row.qtyToBuy ?: row.defaultQuantity ?: 1.0

                            val priceCents =
                                row.priceOverrideCents ?: row.storePriceOverrideCents ?: row.defaultPriceCents
                            val priceText =
                                priceCents?.let { cents -> "$" + "%.2f".format(cents / 100.0) } ?: ""

                            ActiveListRow(
                                itemId = row.itemId,
                                itemName = row.itemName,
                                qtyValue = qty,
                                metaRight = priceText,
                                checkedInCart = row.checkedInCart,
                                onToggle = { viewModel.toggleNeedToGetChecked(row.itemId) },
                                onRemove = { viewModel.removeNeedToGetEntry(row.itemId) },
                                onEdit = { onOpenEditItem(row.itemId) },
                                onSetQty = { newQty -> viewModel.setNeedToGetQty(row.itemId, newQty) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$title ($count)",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun InCartHeaderRow(
    count: Int,
    inCartEntries: List<com.reese.shoppinglist.data.ShoppingDao.ListEntryRow>,
    onCheckoutConfirm: () -> Unit
) {
    var showCheckout by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "In the cart ($count)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = { if (count > 0) showCheckout = true },
            enabled = count > 0
        ) { Text("Checkout") }
    }

    if (showCheckout) {
        // Compute totals from rows already in memory
        var missingPriceCount = 0
        var totalCents = 0L
        var taxableCents = 0L

        inCartEntries.forEach { row ->
            val unitPriceCents =
                row.priceOverrideCents
                    ?: row.storePriceOverrideCents
                    ?: row.defaultPriceCents

            val qty = row.qtyToBuy ?: row.defaultQuantity ?: 1.0

            if (unitPriceCents == null) {
                missingPriceCount += 1
            } else {
                val line = (unitPriceCents.toDouble() * qty).toLong()
                totalCents += line
                if (row.taxable) taxableCents += line
            }
        }

        fun money(cents: Long): String = "$" + "%.2f".format(cents / 100.0)

        AlertDialog(
            onDismissRequest = { showCheckout = false },
            title = { Text("Checkout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total (priced items): ${money(totalCents)}")
                    Text("Taxable subtotal: ${money(taxableCents)}")

                    if (missingPriceCount > 0) {
                        Text("Unpriced items skipped: $missingPriceCount")
                    }

                    Text("Confirm will clear only the items that are currently in the cart.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onCheckoutConfirm()
                    showCheckout = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showCheckout = false }) { Text("Cancel") }
            }
        )
    }
}


@Composable
fun AisleHeader(title: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            text = "$title ($count)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveListRow(
    itemId: Long,
    itemName: String,
    qtyValue: Double,
    metaRight: String,
    checkedInCart: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onSetQty: (Double?) -> Unit
) {
    var showActionsDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    var editingQty by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    var qtyField by remember(qtyValue) {
        val s = if (qtyValue % 1.0 == 0.0) qtyValue.toInt().toString() else qtyValue.toString()
        mutableStateOf(TextFieldValue(s, selection = TextRange(0, s.length))) // selected
    }

    fun commitQty() {
        val trimmed = qtyField.text.trim()
        val parsed = trimmed.toDoubleOrNull()
        when {
            trimmed.isEmpty() -> onSetQty(null)
            parsed != null && parsed > 0.0 -> onSetQty(parsed)
            else -> { /* ignore */ }
        }
        editingQty = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { showActionsDialog = true }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(
            checked = checkedInCart,
            onCheckedChange = { onToggle() }
        )

        Text(
            text = itemName,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!editingQty) {
                Text(
                    text = "x${qtyField.text}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            editingQty = true
                            qtyField = qtyField.copy(selection = TextRange(0, qtyField.text.length))
                        },
                        onLongClick = {
                            editingQty = true
                            qtyField = qtyField.copy(selection = TextRange(0, qtyField.text.length))
                        }
                    )
                )
            } else {
                LaunchedEffect(editingQty) {
                    if (editingQty) {
                        focusRequester.requestFocus()
                        qtyField = qtyField.copy(selection = TextRange(0, qtyField.text.length))
                    }
                }

                OutlinedTextField(
                    value = qtyField,
                    onValueChange = { qtyField = it },
                    singleLine = true,
                    modifier = Modifier
                        .width(86.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { commitQty() }
                    )
                )

                TextButton(onClick = { commitQty() }) { Text("OK") }
            }

            Text(
                text = metaRight.ifBlank { "—" },
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }

    if (showActionsDialog) {
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = { Text(itemName) },
            text = { Text("Choose an action") },
            confirmButton = {
                TextButton(onClick = {
                    showActionsDialog = false
                    onEdit()
                }) { Text("Edit") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActionsDialog = false
                    showRemoveDialog = true
                }) { Text("Remove") }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove from list") },
            text = { Text("Remove \"$itemName\" from this shopping list?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    showRemoveDialog = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = {showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/* ---------- Picklist / Stores / EditItem below unchanged (except store pricing added) ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicklistScreen(
    viewModel: ShoppingViewModel,
    onDone: () -> Unit,
    onOpenEditItem: (Long) -> Unit,
    onOpenStores: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var storeMenuOpen by remember { mutableStateOf(false) }

    val selectedStoreName =
        uiState.stores.firstOrNull { it.id == uiState.selectedStoreId }?.name ?: "No store"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                            .clickable { storeMenuOpen = true }
                    ) {
                        Text("Picklist — $selectedStoreName")

                        DropdownMenu(
                            expanded = storeMenuOpen,
                            onDismissRequest = { storeMenuOpen = false }
                        ) {
                            uiState.stores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name) },
                                    onClick = {
                                        storeMenuOpen = false
                                        viewModel.selectStore(store.id)
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Done") }
                },
                actions = {
                    TextButton(onClick = onOpenStores) { Text("Stores") }
                }
            )
        }

    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.picklistQuery,
                onValueChange = { viewModel.setPicklistQuery(it) },
                label = { Text("Search items") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            val grouped = uiState.picklistRows.groupBy {
                it.aisle?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unassigned"
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grouped.forEach { (aisle, rows) ->
                    item {
                        Text(
                            text = aisle,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(rows.size) { index ->
                        val row = rows[index]
                        PicklistRow(
                            name = row.name,
                            priceCents = row.storePriceOverrideCents ?: row.defaultPriceCents,
                            onTap = { viewModel.addFromPicklist(row.itemId) },
                            onEdit = { onOpenEditItem(row.itemId) },
                            onDelete = { viewModel.deleteFromPicklist(row.itemId) }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PicklistRow(
    name: String,
    priceCents: Int?,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f)
            )

            if (priceCents != null) {
                Text(
                    text = "$" + "%.2f".format(priceCents / 100.0),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(name) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        showMenu = false
                        onEdit()
                    }) { Text("Edit") }

                    TextButton(onClick = {
                        showMenu = false
                        onDelete()
                    }) { Text("Delete") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StoresScreen(
    viewModel: ShoppingViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var storeName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Store?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stores") },
                navigationIcon = { TextButton(onClick = onDone) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("New store name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    viewModel.addStore(storeName)
                    storeName = ""
                },
                enabled = storeName.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add store") }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.stores.size) { index ->
                    val store = uiState.stores[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    viewModel.selectStore(store.id)
                                    onDone() // go back immediately after selecting
                                },
                                onLongClick = { confirmDelete = store }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = store.name,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { store ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete store") },
            text = { Text("Delete \"${store.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStore(store.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    viewModel: ShoppingViewModel,
    itemId: Long?,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) {
        if (itemId != null) viewModel.loadItemForEdit(itemId)
    }

    val item = uiState.editingItem
    val selectedStoreId = uiState.selectedStoreId

    var aisleField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var aisleInitialized by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }

    // Catalog defaults
    var defaultPrice by remember { mutableStateOf("") }
    var defaultQty by remember { mutableStateOf("") }
    var defaultUnit by remember { mutableStateOf("") }
    var defaultSize by remember { mutableStateOf("") }

    // ✅ Store-specific override (selected store)
    var storePrice by remember { mutableStateOf("") }

    var notes by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }
    var showIfAisleUnassigned by remember { mutableStateOf(true) }

    LaunchedEffect(item) {
        if (item != null) {
            name = item.name
            defaultPrice = item.defaultPriceCents?.let { (it / 100.0).toString() } ?: ""
            defaultQty = item.defaultQuantity?.toString() ?: ""
            defaultUnit = item.defaultUnit ?: ""
            defaultSize = item.defaultSize ?: ""
            notes = item.notes ?: ""
            isActive = item.isActive
        }
    }

    LaunchedEffect(item?.id, selectedStoreId) {
        aisleInitialized = false
    }

    LaunchedEffect(item?.id, selectedStoreId, uiState.editingStoreItems) {
        if (item != null && selectedStoreId != null) {
            val si = uiState.editingStoreItems.firstOrNull { it.storeId == selectedStoreId }
            val a = si?.aisle ?: "Unassigned"
            val sp = si?.priceOverrideCents?.let { (it / 100.0).toString() } ?: ""
            val show = si?.showIfAisleUnassigned ?: true

            // ✅ initialize ONCE, do NOT keep resetting while typing
            if (!aisleInitialized) {
                val sp = si?.priceOverrideCents?.let { (it / 100.0).toString() } ?: ""
                val show = si?.showIfAisleUnassigned ?: true

                aisleField = TextFieldValue(a, selection = TextRange(0, a.length))
                storePrice = sp
                showIfAisleUnassigned = show
                aisleInitialized = true
            }
        }
    }

    val canSave = item != null && selectedStoreId != null && name.trim().isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Item") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (itemId == null) {
                Text("No itemId provided.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            if (item == null) {
                Text("Loading item…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = aisleField,
                onValueChange = {
                    aisleField = it
                    viewModel.setAisleTypeahead(selectedStoreId, it.text)
                },
                label = { Text("Aisle (selected store)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            val aisleSugs = uiState.aisleSuggestions

            DropdownMenu(
                expanded = aisleSugs.isNotEmpty() && aisleField.text.trim().length >= 2,
                onDismissRequest = { viewModel.setAisleTypeahead(selectedStoreId, "") },
                modifier = Modifier.fillMaxWidth()
            ) {
                aisleSugs.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a) },
                        onClick = {
                            aisleField = TextFieldValue(
                                a,
                                selection = TextRange(a.length, a.length)
                            )
                            viewModel.setAisleTypeahead(selectedStoreId, "")
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = defaultQty,
                    onValueChange = { defaultQty = it },
                    label = { Text("Quantity") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = defaultUnit,
                    onValueChange = { defaultUnit = it },
                    label = { Text("Default unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = defaultSize,
                    onValueChange = { defaultSize = it },
                    label = { Text("Size") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = defaultPrice,
                    onValueChange = { defaultPrice = it },
                    label = { Text("Default price") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // ✅ New: store-specific price override for currently selected store
            OutlinedTextField(
                value = storePrice,
                onValueChange = { storePrice = it },
                label = { Text("Store price (selected store)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                    Text("Active", modifier = Modifier.padding(start = 8.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showIfAisleUnassigned,
                        onCheckedChange = { showIfAisleUnassigned = it }
                    )
                    Text(
                        "Show if aisle unassigned",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        val qtyParsed =
                            defaultQty.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

                        val defaultPriceCentsParsed =
                            defaultPrice.trim()
                                .takeIf { it.isNotEmpty() }
                                ?.toDoubleOrNull()
                                ?.let { (it * 100).roundToInt() }

                        val storePriceCentsParsed =
                            storePrice.trim()
                                .takeIf { it.isNotEmpty() }
                                ?.toDoubleOrNull()
                                ?.let { (it * 100).roundToInt() }

                        val updated = item.copy(
                            name = name.trim(),
                            defaultQuantity = qtyParsed,
                            defaultUnit = defaultUnit.trim().ifEmpty { null },
                            defaultSize = defaultSize.trim().ifEmpty { null },
                            defaultPriceCents = defaultPriceCentsParsed,
                            notes = notes.trim().ifEmpty { null },
                            isActive = isActive
                        )

                        viewModel.saveItemForSelectedStore(
                            item = updated,
                            storeId = selectedStoreId!!,
                            aisle = aisleField.text,

                            showIfAisleUnassigned = showIfAisleUnassigned,
                            priceOverrideCents = storePriceCentsParsed
                        )

                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSave
                ) {
                    Text("Save")
                }
            }
        }
    }
}
