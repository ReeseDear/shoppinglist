package com.reese.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reese.shoppinglist.data.Item
import com.reese.shoppinglist.data.ShoppingDatabase
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.ui.ShoppingViewModel
import com.reese.shoppinglist.ui.ShoppingViewModelFactory
import com.reese.shoppinglist.ui.theme.ShoppingListTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        val database = ShoppingDatabase.getDatabase(applicationContext)
        val repository = ShoppingRepository(database.shoppingDao())
        ShoppingViewModelFactory(repository)
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

@Composable
fun AppRoot(viewModel: ShoppingViewModel) {
    var showPicklist by remember { mutableStateOf(false) }

    if (showPicklist) {
        PicklistScreen(
            viewModel = viewModel,
            onDone = { showPicklist = false }
        )
    } else {
        ShoppingListScreen(
            viewModel = viewModel,
            onOpenPicklist = { showPicklist = true }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingViewModel,
    onOpenPicklist: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var itemName by remember { mutableStateOf("") }

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

                    // Store dropdown stays (placeholder for Phase 5 store-aware sorting)
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
                                    viewModel.selectStore(store.id)
                                    storeMenuOpen = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Add item") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionHeader(
                        title = "Need to get",
                        count = uiState.needToGetEntries.size
                    )
                }

                items(uiState.needToGetEntries.size) { index ->
                    val row = uiState.needToGetEntries[index]
                    ActiveListRow(
                        itemName = row.itemName,
                        checkedInCart = row.checkedInCart,
                        onToggle = { viewModel.toggleNeedToGetChecked(row.itemId) },
                        onRemove = { viewModel.removeNeedToGetEntry(row.itemId) }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    SectionHeader(
                        title = "In the cart",
                        count = uiState.inCartEntries.size
                    )
                }

                items(uiState.inCartEntries.size) { index ->
                    val row = uiState.inCartEntries[index]
                    ActiveListRow(
                        itemName = row.itemName,
                        checkedInCart = row.checkedInCart,
                        onToggle = { viewModel.toggleNeedToGetChecked(row.itemId) },
                        onRemove = { viewModel.removeNeedToGetEntry(row.itemId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicklistScreen(
    viewModel: ShoppingViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Picklist") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Done") }
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

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.picklistItems.size) { index ->
                    val item: Item = uiState.picklistItems[index]
                    PicklistRow(
                        name = item.name,
                        onTap = { viewModel.addFromPicklist(item.id) },
                        onLongPressDelete = { viewModel.deleteFromPicklist(item.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PicklistRow(
    name: String,
    onTap: () -> Unit,
    onLongPressDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { showDeleteDialog = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = name,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete item") },
            text = { Text("Permanently delete \"$name\" from the catalog?") },
            confirmButton = {
                TextButton(onClick = {
                    onLongPressDelete()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}




@Composable
fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveListRow(
    itemName: String,
    checkedInCart: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onToggle() },
                    onLongClick = { showRemoveDialog = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checkedInCart,
                onCheckedChange = { onToggle() }
            )

            Text(
                text = itemName,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 16.sp
            )
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove from list") },
            text = { Text("Remove \"$itemName\" from this shopping list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        showRemoveDialog = false
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
