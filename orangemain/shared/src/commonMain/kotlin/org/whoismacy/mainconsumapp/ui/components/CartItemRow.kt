package org.whoismacy.mainconsumapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@Composable
fun CartItemRow(
    item: CartItem,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    onRemoveItem: (CartItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Name & Unit Price
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = CurrencyFormatter.format(item.product.price),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Quantity Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDecreaseQuantity(item) }) {
                // TODO: Replace with minus/remove icon
                Text("➖")
            }
            Text(
                text = item.quantity.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onIncreaseQuantity(item) }) {
                // TODO: Replace with add icon
                Text("➕")
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Total Price & Remove Action
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.format(item.totalPrice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(onClick = { onRemoveItem(item) }) {
                // TODO: Replace with delete/trash icon
                Text("🗑️") 
            }
        }
    }
}
