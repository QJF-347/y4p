package org.whoismacy.mainconsumapp.ui.screens.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.Product
import org.whoismacy.mainconsumapp.ui.components.CartItemRow
import org.whoismacy.mainconsumapp.ui.components.ProductCard
import org.whoismacy.mainconsumapp.ui.components.SearchBar
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PosScreen(
    onNavigateToCheckout: (Double) -> Unit,
    viewModel: PosViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp

        if (isCompact) {
            CompactPosLayout(
                state = state,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onAddToCart = viewModel::addToCart,
                onIncreaseQuantity = viewModel::increaseQuantity,
                onDecreaseQuantity = viewModel::decreaseQuantity,
                onRemoveItem = viewModel::removeFromCart,
                onNavigateToCheckout = onNavigateToCheckout
            )
        } else {
            ExpandedPosLayout(
                state = state,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onAddToCart = viewModel::addToCart,
                onIncreaseQuantity = viewModel::increaseQuantity,
                onDecreaseQuantity = viewModel::decreaseQuantity,
                onRemoveItem = viewModel::removeFromCart,
                onNavigateToCheckout = onNavigateToCheckout
            )
        }
    }
}

@Composable
private fun CompactPosLayout(
    state: PosState,
    onSearchQueryChanged: (String) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onNavigateToCheckout: (Double) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val totalItemCount = state.cartItems.sumOf { it.quantity }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Products") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Cart")
                        if (totalItemCount > 0) {
                            Badge { Text(totalItemCount.toString()) }
                        }
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTabIndex == 0) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ProductCatalogContent(
                        state = state,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onAddToCart = onAddToCart,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (state.cartItems.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "$totalItemCount item${if (totalItemCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(state.totalAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Button(
                                    onClick = { selectedTabIndex = 1 }
                                ) {
                                    Text("View Cart")
                                }
                            }
                        }
                    }
                }
            } else {
                CartContent(
                    state = state,
                    onIncreaseQuantity = onIncreaseQuantity,
                    onDecreaseQuantity = onDecreaseQuantity,
                    onRemoveItem = onRemoveItem,
                    onNavigateToCheckout = onNavigateToCheckout,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ExpandedPosLayout(
    state: PosState,
    onSearchQueryChanged: (String) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onNavigateToCheckout: (Double) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        ProductCatalogContent(
            state = state,
            onSearchQueryChanged = onSearchQueryChanged,
            onAddToCart = onAddToCart,
            modifier = Modifier.weight(1.5f).fillMaxSize()
        )

        CartContent(
            state = state,
            onIncreaseQuantity = onIncreaseQuantity,
            onDecreaseQuantity = onDecreaseQuantity,
            onRemoveItem = onRemoveItem,
            onNavigateToCheckout = onNavigateToCheckout,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun ProductCatalogContent(
    state: PosState,
    onSearchQueryChanged: (String) -> Unit,
    onAddToCart: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SearchBar(
            query = state.searchQuery,
            onQueryChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No products available")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                items(state.products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onClick = { onAddToCart(product) },
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CartContent(
    state: PosState,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    onNavigateToCheckout: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Current Order",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (state.cartItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Cart is empty", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.cartItems, key = { it.product.id }) { item ->
                        CartItemRow(
                            item = item,
                            onIncreaseQuantity = { onIncreaseQuantity(it) },
                            onDecreaseQuantity = { onDecreaseQuantity(it) },
                            onRemoveItem = { onRemoveItem(it) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                    Text(CurrencyFormatter.format(state.subtotal), style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tax (16%)", style = MaterialTheme.typography.bodyLarge)
                    Text(CurrencyFormatter.format(state.taxAmount), style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        CurrencyFormatter.format(state.totalAmount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onNavigateToCheckout(state.totalAmount) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = state.cartItems.isNotEmpty()
                ) {
                    Text("CHARGE " + CurrencyFormatter.format(state.totalAmount))
                }
            }
        }
    }
}
