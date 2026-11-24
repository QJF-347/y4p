package com.sixteenhalves.orangeadmin.domain

/** v1 contract: stock at or below this threshold is considered low. */
const val LOW_STOCK_THRESHOLD = 5

data class Branch(
    val id: String,
    val name: String,
    val code: String = "",
    val location: String = "",
    val isActive: Boolean = true,
)

data class TopSeller(
    val name: String,
    val quantitySold: Int,
)

data class BranchStats(
    val devicesCount: Int = 0,
    val todaySalesCents: Long = 0,
    val salesCount: Int = 0,
    val lowStockCount: Int = 0,
    val topSellers: List<TopSeller> = emptyList(),
)

data class BranchOverview(
    val branch: Branch,
    val stats: BranchStats,
)

data class DeviceInfo(
    val id: String,
    val name: String,
    val code: String,
    val isRevoked: Boolean,
)

data class InventoryProduct(
    val id: String,
    val name: String,
    val categoryName: String,
    val branchName: String,
    val stock: Int,
    val priceCents: Long,
) {
    val isLowStock: Boolean get() = stock <= LOW_STOCK_THRESHOLD
}

data class DashboardSummary(
    val branchesCount: Int = 0,
    val activeDevices: Int = 0,
    val salesTodayCents: Long = 0,
    val salesTodayCount: Int = 0,
    val pendingPayments: Int = 0,
    val lowStockItems: Int = 0,
    val topSellers: List<TopSeller> = emptyList(),
)

data class BranchDetail(
    val branch: Branch,
    val stats: BranchStats,
    val products: List<InventoryProduct>,
    val devices: List<DeviceInfo>,
)

data class CategoryOption(
    val id: String,
    val name: String,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data object Empty : UiState<Nothing>

    data class Ready<T>(val value: T) : UiState<T>

    data class Failed(val message: String) : UiState<Nothing>
}
