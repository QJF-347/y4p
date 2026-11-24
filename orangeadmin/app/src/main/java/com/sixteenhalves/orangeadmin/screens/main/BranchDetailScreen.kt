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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.R
import com.sixteenhalves.orangeadmin.domain.BranchDetail
import com.sixteenhalves.orangeadmin.domain.DeviceInfo
import com.sixteenhalves.orangeadmin.domain.InventoryProduct
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.utils.formatKes
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun BranchDetailScreen(
    mainViewModel: MainViewModel,
    branchId: String,
    onAddDevice: () -> Unit,
    onAddInventory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = true,
) {
    val state by mainViewModel.branchDetail.collectAsStateWithLifecycle()

    LaunchedEffect(branchId) {
        mainViewModel.openBranch(branchId)
    }

    when (val current = state) {
        is UiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Failed -> {
            BranchDetailError(
                message = current.message,
                onRetry = { mainViewModel.openBranch(branchId) },
                onBack = onBack,
                modifier = modifier,
            )
        }

        is UiState.Empty -> {
            BranchDetailError(
                message = "We couldn't find the details for this branch. It may have been removed or deactivated.",
                onRetry = { mainViewModel.openBranch(branchId) },
                onBack = onBack,
                modifier = modifier,
            )
        }

        is UiState.Ready -> {
            BranchDetailContent(
                detail = current.value,
                onAddDevice = onAddDevice,
                onAddInventory = onAddInventory,
                onRevokeDevice = { device -> mainViewModel.revokeDevice(branchId, device.id) },
                canManage = canManage,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun BranchDetailContent(
    detail: BranchDetail,
    onAddDevice: () -> Unit,
    onAddInventory: () -> Unit,
    onRevokeDevice: (DeviceInfo) -> Unit,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val branch = detail.branch
    val stats = detail.stats
    val products = detail.products
    val devices = detail.devices

    val totalStock = products.sumOf { it.stock }
    val lowStockCount = products.count { it.isLowStock }
    val topSeller = stats.topSellers.firstOrNull()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = branch.location.ifBlank { branch.code },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            StatusIndicator(isActive = branch.isActive)
        }

        Spacer(modifier = Modifier.height(48.dp))

        SectionHeader("Sales Overview")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricCard(
                title = "Today's Sales",
                value = formatKes(stats.todaySalesCents),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Total Devices",
                value = stats.devicesCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        SectionHeader("Inventory Insights")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InsightCard(
                title = "Total Stock",
                value = totalStock.toString(),
                subtitle = "Units available",
                modifier = Modifier.weight(1f),
            )
            InsightCard(
                title = "Low Stock",
                value = lowStockCount.toString(),
                subtitle = "Items depleting",
                isAlert = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InsightCard(
                title = "Top Seller",
                value = topSeller?.name ?: "—",
                subtitle = topSeller?.let { "${it.quantitySold} units sold" } ?: "No sales yet today",
                modifier = Modifier.weight(1f),
            )
            InsightCard(
                title = "Products",
                value = products.size.toString(),
                subtitle = "In catalog",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        InventorySection(
            products = products,
            onAddInventory = onAddInventory,
            canManage = canManage,
        )

        Spacer(modifier = Modifier.height(40.dp))

        DevicesSection(
            devices = devices,
            onAddDevice = onAddDevice,
            onRevokeDevice = onRevokeDevice,
            canManage = canManage,
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun BranchDetailError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_error_24),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Branch Not Found",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Try again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Return", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun InventorySection(
    products: List<InventoryProduct>,
    onAddInventory: () -> Unit,
    canManage: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader("Inventory Management", paddingBottom = 0.dp)
        if (canManage) {
            IconButton(
                onClick = onAddInventory,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_add_circle_outline_24),
                    contentDescription = "Add Item",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (products.isEmpty()) {
                Text(
                    text = "No products have been added to this branch yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                products.forEachIndexed { index, item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val textColor =
                                if (item.isLowStock) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            val fontWeight = if (item.isLowStock) FontWeight.Bold else FontWeight.Normal
                            Text(
                                text = "${item.stock} units remaining · ${formatKes(item.priceCents)}",
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = fontWeight),
                            )
                        }
                        if (canManage) {
                            Button(
                                onClick = onAddInventory,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            if (item.isLowStock) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        contentColor =
                                            if (item.isLowStock) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                    ),
                            ) {
                                Text("Restock", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    if (index < products.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesSection(
    devices: List<DeviceInfo>,
    onAddDevice: () -> Unit,
    onRevokeDevice: (DeviceInfo) -> Unit,
    canManage: Boolean,
) {
    var pendingRevoke by remember { mutableStateOf<DeviceInfo?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader("POS Devices", paddingBottom = 0.dp)
        if (canManage) {
            IconButton(
                onClick = onAddDevice,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_add_circle_outline_24),
                    contentDescription = "Add Device",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    if (devices.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                    text = "No POS Devices",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You haven't added any POS devices to this branch yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onAddDevice,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Add POS Device", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                devices.forEachIndexed { index, device ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = device.name.ifBlank { "POS Device" },
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Code: ${device.code}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (device.isRevoked) "Revoked" else "Active",
                                color =
                                    if (device.isRevoked) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            if (canManage && !device.isRevoked) {
                                TextButton(
                                    onClick = { pendingRevoke = device },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        text = "Revoke",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                    }
                    if (index < devices.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        )
                    }
                }
            }
        }
    }

    pendingRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text("Revoke device?") },
            text = {
                Text(
                    "\"${device.name.ifBlank { "POS Device" }}\" (${device.code}) will stop accepting sales immediately. This cannot be undone from the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRevoke = null
                        onRevokeDevice(device)
                    },
                ) {
                    Text("Revoke", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    paddingBottom: Dp = 16.dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = paddingBottom),
    )
}

@Composable
private fun StatusIndicator(isActive: Boolean) {
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val text = if (isActive) "Active" else "Inactive"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun InsightCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = labelColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = textColor,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
    }
}
