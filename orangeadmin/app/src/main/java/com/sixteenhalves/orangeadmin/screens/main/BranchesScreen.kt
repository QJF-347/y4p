package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sixteenhalves.orangeadmin.R
import com.sixteenhalves.orangeadmin.utils.formatKes

data class Branch(
    val id: String,
    val name: String,
    val code: String,
    val location: String,
    val isActive: Boolean = true,
)

/**
 * Aggregates are served separately from the Branch entity (see API_CONTRACT.md §Admin & stats),
 * so mock data keeps them out of the synced record shape.
 */
data class BranchStats(
    val devicesCount: Int,
    val todaySalesCents: Long,
)

object MockBranchData {
    val branches =
        listOf(
            Branch("019a3c40-0001-7000-8000-000000000001", "Downtown Store", "NBO-CBD-01", "Kenyatta Ave, Nairobi"),
            Branch("019a3c40-0002-7000-8000-000000000002", "Uptown Mall", "NBO-WST-02", "Waiyaki Way, Nairobi"),
            Branch("019a3c40-0003-7000-8000-000000000003", "Mombasa Road Outlet", "MSA-RD-03", "Mombasa Rd, Nairobi"),
            Branch("019a3c40-0004-7000-8000-000000000004", "Kisumu Pop-up", "KSM-CBD-04", "Oginga Odinga St, Kisumu", isActive = false),
        )

    val stats =
        mapOf(
            branches[0].id to BranchStats(devicesCount = 3, todaySalesCents = 425_080),
            branches[1].id to BranchStats(devicesCount = 5, todaySalesCents = 812_050),
            branches[2].id to BranchStats(devicesCount = 2, todaySalesCents = 234_000),
            branches[3].id to BranchStats(devicesCount = 0, todaySalesCents = 85_000),
        )
}

@Composable
fun BranchesScreen(
    onNewBranch: () -> Unit,
    onBranchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val branches = MockBranchData.branches
    // variables: MockBranchData; Branches { branchname, location, salestoday, active, posdevices, totalstock, lowstock, topsellerWithQuantity, leastsoldWithQuantity, POSDEVICE)
    // BRANCH (DATA_CONTRACT.md): {id: UUID, name: "", code: "", location: "", is_active: Bool}
    // Stats (devicesCount / todaySales) come from a separate stats endpoint, not the entity.
    // POSDEVICE: {deviceName: "", serialNumber: "", status: Bool}

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBranch,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_add_circle_outline_24),
                        contentDescription = null,
                    )
                },
                text = {
                    Text("Add Branch", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "Branches",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Manage your store fronts and locations.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (branches.isEmpty()) {
                item {
                    EmptyBranchesState(onNewBranch = onNewBranch)
                }
            } else {
                items(branches) { branch ->
                    BranchCard(
                        branch = branch,
                        onClick = { onBranchClick(branch.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchCard(
    branch: Branch,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_storefront_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = branch.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = branch.location,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                StatusBadge(isActive = branch.isActive)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BranchMetric(
                    label = "Today's Sales",
                    value = formatKes(MockBranchData.stats[branch.id]?.todaySalesCents ?: 0L),
                )
                BranchMetric(
                    label = "POS Devices",
                    value = "${MockBranchData.stats[branch.id]?.devicesCount ?: 0} Active",
                    alignment = Alignment.End,
                )
            }
        }
    }
}

@Composable
private fun BranchMetric(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (isActive) "Active" else "Inactive"

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
        )
    }
}

@Composable
private fun EmptyBranchesState(onNewBranch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_storefront_24),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No Branches Yet",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add your store fronts here to manage them effectively.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onNewBranch,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Create New Branch", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
