package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.R
import com.sixteenhalves.orangeadmin.domain.InventoryProduct
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun BranchAddInventoryScreen(
    mainViewModel: MainViewModel,
    branchId: String,
    onBack: () -> Unit,
    onAddNewItem: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = true,
) {
    val detailState by mainViewModel.branchDetail.collectAsStateWithLifecycle()

    LaunchedEffect(branchId) {
        mainViewModel.openBranch(branchId)
    }

    val products: List<InventoryProduct> =
        ((detailState as? UiState.Ready)?.value)?.products.orEmpty()

    val quantities = remember { mutableStateMapOf<String, Int>() }
    val deletedItems = remember { mutableStateListOf<String>() }
    // Last stock value seen from the server per product, used as the
    // baseline for conflict detection when saving absolute quantities.
    val serverStock = remember { mutableStateMapOf<String, Int>() }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(products) {
        val productIds = products.map { it.id }.toSet()
        deletedItems.removeAll { it !in productIds }
        quantities.keys.toList().forEach { id -> if (id !in productIds) quantities.remove(id) }
        serverStock.keys.toList().forEach { id -> if (id !in productIds) serverStock.remove(id) }
        products.forEach { product ->
            val known = serverStock[product.id]
            if (known == null || known != product.stock) {
                // First load, or the server value changed: adopt it.
                serverStock[product.id] = product.stock
                quantities[product.id] = product.stock
            }
        }
    }

    val displayItems =
        products
            .filter { it.id !in deletedItems }
            .sortedBy { !it.isLowStock }
    val hasChanges =
        deletedItems.isNotEmpty() ||
            products.any { it.id !in deletedItems && (quantities[it.id] ?: it.stock) != it.stock }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_arrow_back_24),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Branch Inventory",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        when (val current = detailState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Failed -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { mainViewModel.openBranch(branchId) }, shape = RoundedCornerShape(12.dp)) {
                        Text("Try again")
                    }
                }
            }

            is UiState.Empty -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "This branch has no products yet. Add a new item to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is UiState.Ready -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Available Items (${displayItems.size})",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (canManage) {
                                IconButton(
                                    onClick = onAddNewItem,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.baseline_add_circle_outline_24),
                                        contentDescription = "Create New Item",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(displayItems, key = { it.id }) { item ->
                        AddInventoryCard(
                            item = item,
                            quantity = quantities[item.id] ?: item.stock,
                            onQuantityChange = { quantities[item.id] = it },
                            onDelete = { deletedItems.add(item.id) },
                            canManage = canManage,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                ) {
                    if (!canManage) {
                        Text(
                            text = "Read-only access. Ask an owner to change this inventory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Button(
                            onClick = {
                                val restocks =
                                    products
                                        .filter { it.id !in deletedItems }
                                        .mapNotNull { product ->
                                            val quantity = quantities[product.id] ?: product.stock
                                            if (quantity != product.stock) product.id to quantity else null
                                        }.toMap()
                                val baseline =
                                    restocks.keys
                                        .mapNotNull { id -> serverStock[id]?.let { id to it } }
                                        .toMap()
                                saving = true
                                mainViewModel.saveInventoryChanges(
                                    branchId = branchId,
                                    baseline = baseline,
                                    restocks = restocks,
                                    deletions = deletedItems.toSet(),
                                ) { success ->
                                    saving = false
                                    if (success) onBack()
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = hasChanges && !saving,
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(
                                    text = "Save Changes",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddInventoryCard(
    item: InventoryProduct,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    onDelete: () -> Unit,
    canManage: Boolean,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.isLowStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_inventory_2_24),
                            contentDescription = null,
                            tint = if (item.isLowStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (quantity > 0) "In Stock" else "Out of Stock",
                            style =
                                MaterialTheme.typography
                                    .bodyMedium
                                    .copy(fontWeight = if (quantity == 0) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (quantity == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (canManage) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Remove", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (canManage) {
                        IconButton(
                            onClick = { if (quantity > 0) onQuantityChange(quantity - 1) },
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_remove_24),
                                contentDescription = "Decrease",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.Center,
                    )

                    if (canManage) {
                        IconButton(
                            onClick = { onQuantityChange(quantity + 1) },
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_add_circle_outline_24),
                                contentDescription = "Increase",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.outline_remove_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = "Remove Item?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to completely remove '${item.name}' from this branch's inventory? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
        )
    }
}
