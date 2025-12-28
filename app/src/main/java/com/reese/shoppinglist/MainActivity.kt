package com.reese.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reese.shoppinglist.data.ShoppingDatabase
import com.reese.shoppinglist.data.ShoppingRepository
import com.reese.shoppinglist.ui.ShoppingViewModel
import com.reese.shoppinglist.ui.ShoppingViewModelFactory
import com.reese.shoppinglist.ui.theme.ShoppingListTheme
import com.reese.shoppinglist.data.Store
import com.reese.shoppinglist.data.StoreItemDisplay



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
                    ShoppingListScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var itemName by remember { mutableStateOf("") }
    var aisleName by remember { mutableStateOf("") }

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

            /* Input section */
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Add item") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = aisleName,
                        onValueChange = { aisleName = it },
                        label = { Text("Aisle (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.addItem(itemName, aisleName)
                            itemName = ""
                            aisleName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = itemName.trim().isNotEmpty()
                    ) {
                        Text("Add to List")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            /* Shopping lists */
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                item {
                    SectionHeader(
                        title = "Need to get",
                        count = uiState.needToGetCount
                    )
                }

                uiState.needToGet.forEach { (aisle, items) ->
                    item {
                        AisleGroup(
                            aisle = aisle,
                            items = items,
                            onToggle = { viewModel.toggleItemCart(it) },
                            onDelete = { viewModel.deleteItem(it) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    SectionHeader(
                        title = "In the cart",
                        count = uiState.inCartCount
                    )
                }

                uiState.inCart.forEach { (aisle, items) ->
                    item {
                        AisleGroup(
                            aisle = aisle,
                            items = items,
                            onToggle = { viewModel.toggleItemCart(it) },
                            onDelete = { viewModel.deleteItem(it) }
                        )
                    }
                }
            }
        }
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

@Composable
fun AisleGroup(
    aisle: String,
    items: List<StoreItemDisplay>,
    onToggle: (StoreItemDisplay) -> Unit,
    onDelete: (StoreItemDisplay) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Text(
                text = aisle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            items.forEach { item ->
                ShoppingItemRow(item, onToggle, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    row: StoreItemDisplay,
    onToggle: (StoreItemDisplay) -> Unit,
    onDelete: (StoreItemDisplay) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle(row) },
                onLongClick = { showDeleteDialog = true }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = row.inCart,
            onCheckedChange = { onToggle(row) }
        )

        Text(
            text = row.name,
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete \"${row.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(row)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
