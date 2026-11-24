package org.whoismacy.mainconsumapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NumPad(
    onNumberClick: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackspaceClick: () -> Unit = {}
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    ElevatedButton(
                        onClick = {
                            when (key) {
                                "C" -> onClearClick()
                                "⌫" -> onBackspaceClick()
                                else -> onNumberClick(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.5f),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = when (key) {
                                "C" -> MaterialTheme.colorScheme.errorContainer
                                "⌫" -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when (key) {
                                "C" -> MaterialTheme.colorScheme.onErrorContainer
                                "⌫" -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}