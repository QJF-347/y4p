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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sixteenhalves.orangeadmin.R
import com.sixteenhalves.orangeadmin.utils.formatKes

/** v1 contract: low stock is derived; branches use a default threshold of 5. */
const val LOW_STOCK_THRESHOLD = 5

data class InventoryProduct(
    val id: String,
    val name: String,
    val categoryId: String,
    val stock: Int,
    val priceCents: Long,
) {
    val isLowStock: Boolean get() = stock <= LOW_STOCK_THRESHOLD
}

object MockCategoryData {
    const val FRESH_PRODUCE = "019a3c41-1111-7000-8000-000000000001"
    const val BEVERAGES = "019a3c41-2222-7000-8000-000000000002"
    const val KITCHENWARE = "019a3c41-3333-7000-8000-000000000003"

    val names =
        mapOf(
            FRESH_PRODUCE to "Fresh Produce",
            BEVERAGES to "Beverages",
            KITCHENWARE to "Kitchenware",
        )
}

object MockInventoryData {
    val items =
        listOf(
            InventoryProduct("019a3c58-0001-7000-8000-000000000001", "Navel Oranges", MockCategoryData.FRESH_PRODUCE, 12, 120),
            InventoryProduct("019a3c58-0002-7000-8000-000000000002", "Orange Juice (1L)", MockCategoryData.BEVERAGES, 342, 450),
            InventoryProduct("019a3c58-0003-7000-8000-000000000003", "Grapefruits", MockCategoryData.FRESH_PRODUCE, 5, 150),
            InventoryProduct("019a3c58-0004-7000-8000-000000000004", "Tangerines", MockCategoryData.FRESH_PRODUCE, 2, 80),
            InventoryProduct("019a3c58-0005-7000-8000-000000000005", "Apple - Fuji", MockCategoryData.FRESH_PRODUCE, 150, 100),
            InventoryProduct("019a3c58-0006-7000-8000-000000000006", "Banana", MockCategoryData.FRESH_PRODUCE, 85, 50),
            InventoryProduct("019a3c58-0007-7000-8000-000000000007", "Citrus Peeler", MockCategoryData.KITCHENWARE, 42, 500),
            InventoryProduct("019a3c58-0008-7000-8000-000000000008", "Organic Lemonade", MockCategoryData.BEVERAGES, 120, 350),
        )
}

@Composable
fun PopulatedInventory(
    onNavigateAddNewItem: () -> Unit,
    onNavigateBranches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val products = MockInventoryData.items
    //variables: total number of items, low stock items, all products: name, category, stock, price
    // pushes: {branch_id, name, category_id, stock_quantity, price_cents} (DATA_CONTRACT.md)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            InventoryHeader()
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InventoryOverviewCard(
                    title = "Total Items",
                    value = "1,245",
                    subtitle = "In Stock",
                    modifier = Modifier.weight(1f),
                )
                InventoryOverviewCard(
                    title = "Low Stock",
                    value = "3",
                    subtitle = "Needs Attention",
                    isAlert = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {onNavigateAddNewItem()}, colors = ButtonDefaults
                    .buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("Add New Product(s)", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall)
                }

                Button(onClick = {onNavigateBranches()}, colors = ButtonDefaults
                    .buttonColors(containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("Re-Stock Branch Products", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "All Products",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        items(products.size) { index ->
            ProductCard(product = products[index])
        }
    }
}

@Composable
private fun InventoryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Inventory",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage your products, pricing, and stock levels.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InventoryOverviewCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    val containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val labelColor =
        if (isAlert) {
            MaterialTheme.colorScheme.onErrorContainer.copy(
                alpha = 0.8f,
            )
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = labelColor,
            )
        }
    }
}

@Composable
private fun ProductCard(product: InventoryProduct) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
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
                                if (product.isLowStock) {
                                    MaterialTheme
                                        .colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_inventory_2_24),
                        contentDescription = null,
                        tint = if (product.isLowStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = MockCategoryData.names[product.categoryId] ?: "Uncategorized",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatKes(product.priceCents),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (product.isLowStock) "${product.stock} Left - Low" else "${product.stock} in Stock",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (product.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
