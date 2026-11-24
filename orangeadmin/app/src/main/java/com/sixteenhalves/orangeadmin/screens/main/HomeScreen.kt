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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.domain.BranchOverview
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel,
    onNewBranch: () -> Unit,
    canManage: Boolean = true,
) {
    val dashboardState by mainViewModel.dashboard.collectAsStateWithLifecycle()
    val branchesState by mainViewModel.branches.collectAsStateWithLifecycle()
    val isRefreshing by mainViewModel.isRefreshingDashboard.collectAsStateWithLifecycle()
    val updatedAt by mainViewModel.dashboardUpdatedAt.collectAsStateWithLifecycle()

    // The dashboard is the server's view of today's takings; re-read it every
    // time the screen is shown instead of relying on the value loaded at login.
    LaunchedEffect(Unit) { mainViewModel.refreshDashboard(showLoading = false) }

    val branchOverviews: List<BranchOverview> =
        (branchesState as? UiState.Ready)?.value.orEmpty()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { mainViewModel.refreshDashboard(showLoading = false) },
        modifier = modifier.fillMaxSize(),
    ) {
    when (val current = dashboardState) {
        is UiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Failed -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
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
                            onClick = { mainViewModel.refreshAll() },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
        }

        is UiState.Empty -> {
            EmptyDashboard(onNewBranch = onNewBranch, modifier = modifier, canManage = canManage)
        }

        is UiState.Ready -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text =
                        updatedAt?.let { "Updated ${formatUpdatedTime(it)}" }
                            ?: "Refreshing…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
                PopulatedDashboard(
                    summary = current.value,
                    branchOverviews = branchOverviews,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }
}

private fun formatUpdatedTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
