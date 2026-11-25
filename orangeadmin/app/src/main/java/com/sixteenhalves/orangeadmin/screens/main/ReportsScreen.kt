package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.domain.BranchComparisonRow
import com.sixteenhalves.orangeadmin.domain.BranchOverview
import com.sixteenhalves.orangeadmin.domain.SalesTrend
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.utils.formatKes
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

private val TREND_WINDOWS = listOf(7, 14, 30)

/**
 * Business-intelligence reports: a daily sales bar chart per branch and a
 * revenue-ranked comparison across the branches the user can see.
 */
@Composable
fun ReportsScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val branchesState by mainViewModel.branches.collectAsStateWithLifecycle()
    val trendState by mainViewModel.salesTrend.collectAsStateWithLifecycle()
    val comparisonState by mainViewModel.branchComparison.collectAsStateWithLifecycle()

    var days by remember { mutableIntStateOf(7) }
    var selectedBranchId by remember { mutableStateOf<String?>(null) }

    val branches: List<BranchOverview> =
        when (val state = branchesState) {
            is UiState.Ready -> state.value
            else -> emptyList()
        }
    val selectedBranch = branches.firstOrNull { it.branch.id == selectedBranchId } ?: branches.firstOrNull()

    LaunchedEffect(Unit) {
        if (branchesState !is UiState.Ready) mainViewModel.refreshBranches()
    }
    LaunchedEffect(selectedBranch?.branch?.id, days) {
        selectedBranch?.branch?.id?.let { mainViewModel.loadSalesTrend(it, days) }
    }
    LaunchedEffect(days) {
        mainViewModel.loadBranchComparison(days)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Reports",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Paid sales over whole UTC days, straight from the server ledger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TREND_WINDOWS.forEach { window ->
                FilterChip(
                    selected = days == window,
                    onClick = { days = window },
                    label = { Text("$window days") },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(title = "Sales trend") {
            if (branches.isEmpty()) {
                ReportMessage(if (branchesState is UiState.Failed) "Couldn't load branches" else "Loading branches…")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    branches.forEach { overview ->
                        val branch = overview.branch
                        FilterChip(
                            selected = branch.id == selectedBranch?.branch?.id,
                            onClick = { selectedBranchId = branch.id },
                            label = { Text(branch.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when (val state = trendState) {
                    is UiState.Loading -> ReportLoading()
                    is UiState.Failed -> ReportMessage(state.message)
                    is UiState.Ready -> SalesTrendContent(state.value)
                    UiState.Empty -> ReportMessage("No sales yet")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(title = "Branch comparison · last $days days") {
            when (val state = comparisonState) {
                is UiState.Loading -> ReportLoading()
                is UiState.Failed -> ReportMessage(state.message)
                is UiState.Ready ->
                    if (state.value.isEmpty()) {
                        ReportMessage("No branches to compare")
                    } else {
                        ComparisonContent(state.value)
                    }
                UiState.Empty -> ReportMessage("No branches to compare")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SalesTrendContent(trend: SalesTrend) {
    val totalCents = trend.points.sumOf { it.salesTotalCents }
    val totalSales = trend.points.sumOf { it.salesCount }
    Text(
        text = "${trend.branchName}: ${formatKes(totalCents)} from $totalSales sales",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(12.dp))

    val maxCents = trend.points.maxOfOrNull { it.salesTotalCents } ?: 0L
    if (maxCents <= 0L) {
        ReportMessage("No paid sales in this window")
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        val baselineY = size.height - 1.dp.toPx()
        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
        )
        val count = trend.points.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = (slot * 0.55f).coerceAtMost(28.dp.toPx())
        trend.points.forEachIndexed { index, point ->
            val fraction = if (maxCents > 0) (point.salesTotalCents.toFloat() / maxCents) else 0f
            val height = (size.height - 4.dp.toPx()) * fraction
            val left = index * slot + (slot - barWidth) / 2f
            drawRect(
                color = if (point.salesTotalCents > 0) barColor else emptyColor,
                topLeft = Offset(left, baselineY - height.coerceAtLeast(2.dp.toPx())),
                size = Size(barWidth, height.coerceAtLeast(2.dp.toPx())),
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = shortDate(trend.points.firstOrNull()?.date.orEmpty()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Peak ${formatKes(maxCents)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = shortDate(trend.points.lastOrNull()?.date.orEmpty()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComparisonContent(rows: List<BranchComparisonRow>) {
    val maxCents = rows.maxOfOrNull { it.salesTotalCents } ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            val fraction = if (maxCents > 0) row.salesTotalCents.toFloat() / maxCents else 0f
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatKes(row.salesTotalCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${row.salesCount} sales · ${row.lowStockItems} low stock · ${row.devicesCount} devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ReportLoading() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.widthIn(max = 28.dp).height(28.dp))
    }
}

@Composable
private fun ReportMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "2026-11-25" -> "Nov 25". */
private fun shortDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size != 3) return isoDate
    val month =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            .getOrNull(parts[1].toIntOrNull()?.minus(1) ?: -1)
            ?: return isoDate
    return "$month ${parts[2].trimStart('0')}"
}
