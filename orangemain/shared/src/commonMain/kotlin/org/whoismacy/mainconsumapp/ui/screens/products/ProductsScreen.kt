package org.whoismacy.mainconsumapp.ui.screens.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import orangemain.shared.generated.resources.Res
import orangemain.shared.generated.resources.search
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.whoismacy.mainconsumapp.data.model.Product
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Products",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search products...") },
                leadingIcon = { Icon( painter = painterResource(Res.drawable.search), contentDescription = null) },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))
            // Product count
            Text(
                text = "${state.products.size} product${if (state.products.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Product list
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.products.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No products found", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductItem(
                            product = product,
                            onEdit = { viewModel.showEditProductDialog(product) },
                            onDelete = { viewModel.requestDelete(product) }
                        )
                    }
                }
            }
        }

        // --- Add/Edit Product Dialog ---
        if (state.isAddEditDialogShowing) {
            AddEditProductDialog(
                state = state,
                onDismiss = viewModel::dismissAddEditDialog,
                onNameChanged = viewModel::onFormNameChanged,
                onPriceChanged = viewModel::onFormPriceChanged,
                onCostPriceChanged = viewModel::onFormCostPriceChanged,
                onCategoryChanged = viewModel::onFormCategoryChanged,
                onStockChanged = viewModel::onFormStockChanged,
                onDescriptionChanged = viewModel::onFormDescriptionChanged,
                onBarcodeChanged = viewModel::onFormBarcodeChanged,
                onSave = viewModel::saveProduct,
            )
        }

        // --- Delete Confirmation ---
        if (state.showDeleteConfirmation && state.productToDelete != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete Product") },
                text = {
                    Text("Are you sure you want to delete \"${state.productToDelete!!.name}\"? This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmDelete,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- Category Management Dialog ---
        if (state.isCategoryDialogShowing) {
            CategoryManagementDialog(
                categories = state.categories,
                newName = state.newCategoryName,
                newIcon = state.newCategoryIcon,
                onNewNameChanged = viewModel::onNewCategoryNameChanged,
                onNewIconChanged = viewModel::onNewCategoryIconChanged,
                onAddCategory = viewModel::addCategory,
                onDeleteCategory = viewModel::deleteCategory,
                onDismiss = viewModel::dismissCategoryDialog,
            )
        }
    }
}

// ─── Product List Item ────────────────────────────────────────

@Composable
fun ProductItem(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyFormatter.format(product.price),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "📂 ${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "📦 Stock: ${product.stockQuantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (product.stockQuantity <= 5) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (product.costPrice != null) {
                    Text(
                        text = "Cost: ${CurrencyFormatter.format(product.costPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Actions
            Column {
                TextButton(onClick = onEdit) {
                    Text("✏️ Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("🗑️ Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ─── Add/Edit Product Dialog ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    state: ProductsState,
    onDismiss: () -> Unit,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onCostPriceChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onStockChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onBarcodeChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(state.formTitle, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = state.formName,
                    onValueChange = onNameChanged,
                    label = { Text("Product Name *") },
                    isError = state.formNameError != null,
                    supportingText = state.formNameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Price + Cost Price side by side
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.formPrice,
                        onValueChange = onPriceChanged,
                        label = { Text("Price (KES) *") },
                        isError = state.formPriceError != null,
                        supportingText = state.formPriceError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.formCostPrice,
                        onValueChange = onCostPriceChanged,
                        label = { Text("Cost Price") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Category dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = state.formCategory,
                        onValueChange = onCategoryChanged,
                        label = { Text("Category") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("${category.icon ?: "🏷️"} ${category.name}") },
                                onClick = {
                                    onCategoryChanged(category.name)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                // Stock
                OutlinedTextField(
                    value = state.formStock,
                    onValueChange = onStockChanged,
                    label = { Text("Stock Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Barcode
                OutlinedTextField(
                    value = state.formBarcode,
                    onValueChange = onBarcodeChanged,
                    label = { Text("Barcode (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description
                OutlinedTextField(
                    value = state.formDescription,
                    onValueChange = onDescriptionChanged,
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(if (state.isEditing) "💾 Update" else "➕ Add Product")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Category Management Dialog ────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryManagementDialog(
    categories: List<org.whoismacy.mainconsumapp.data.model.Category>,
    newName: String,
    newIcon: String,
    onNewNameChanged: (String) -> Unit,
    onNewIconChanged: (String) -> Unit,
    onAddCategory: () -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Manage Categories") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Existing categories
                Text(
                    "Current Categories",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = false,
                            onClick = { },
                            label = { Text("${category.icon ?: "🏷️"} ${category.name}") },
                            trailingIcon = {
                                // Don't allow deleting "General"
                                if (category.name != "General") {
                                    TextButton(
                                        onClick = { onDeleteCategory(category.id) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Text("✕", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Add new category
                Text(
                    "Add New Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = newIcon,
                        onValueChange = onNewIconChanged,
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = onNewNameChanged,
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onAddCategory,
                        enabled = newName.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
