package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun InventoryScreen(
    onNewBranch: () -> Unit,
    onNavigateAddNewItem: () -> Unit,
    onNavigateBranch: () -> Unit,
    mainViewModel: MainViewModel,
    canManage: Boolean = true,
) {
    val state by mainViewModel.inventory.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.refreshInventory()
    }

    when (val current = state) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Failed -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { mainViewModel.refreshInventory() },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
        }

        is UiState.Empty -> {
            EmptyInventory(onNewBranch = onNewBranch, canManage = canManage)
        }

        is UiState.Ready -> {
            PopulatedInventory(
                products = current.value,
                onNavigateAddNewItem = if (canManage) onNavigateAddNewItem else null,
                onNavigateBranches = onNavigateBranch,
            )
        }
    }
}
