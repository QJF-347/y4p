package com.sixteenhalves.orangeadmin.repository

import com.sixteenhalves.orangeadmin.domain.Branch
import com.sixteenhalves.orangeadmin.domain.BranchDetail
import com.sixteenhalves.orangeadmin.domain.BranchOverview
import com.sixteenhalves.orangeadmin.domain.BranchStats
import com.sixteenhalves.orangeadmin.domain.CategoryOption
import com.sixteenhalves.orangeadmin.domain.DashboardSummary
import com.sixteenhalves.orangeadmin.domain.DeviceInfo
import com.sixteenhalves.orangeadmin.domain.InventoryProduct
import com.sixteenhalves.orangeadmin.domain.TopSeller
import com.sixteenhalves.orangeadmin.ntwk.BranchDto
import com.sixteenhalves.orangeadmin.ntwk.BranchStatsDto
import com.sixteenhalves.orangeadmin.ntwk.CreateBranchRequest
import com.sixteenhalves.orangeadmin.ntwk.CreateCategoryRequest
import com.sixteenhalves.orangeadmin.ntwk.CreateDeviceRequest
import com.sixteenhalves.orangeadmin.ntwk.CreateProductRequest
import com.sixteenhalves.orangeadmin.ntwk.DashboardSummaryDto
import com.sixteenhalves.orangeadmin.ntwk.DeviceDto
import com.sixteenhalves.orangeadmin.ntwk.NetworkService
import com.sixteenhalves.orangeadmin.ntwk.ProductDto
import com.sixteenhalves.orangeadmin.ntwk.RestockRequest
import com.sixteenhalves.orangeadmin.ntwk.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceRegistration(
    val code: String,
    val token: String,
)

/** A non-2xx response from the API, carrying the contract error code (API_CONTRACT §0). */
typealias AdminApiException = com.sixteenhalves.orangeadmin.ntwk.ApiException

/** Turns a repository failure into something safe to show to an admin. */
fun Throwable.userFacingMessage(fallback: String): String = toUserMessage(fallback)

interface AdminRepository {
    suspend fun getBranchOverviews(): Result<List<BranchOverview>>

    suspend fun getDashboardSummary(): Result<DashboardSummary>

    suspend fun getInventory(): Result<List<InventoryProduct>>

    suspend fun getBranchDetail(branchId: String): Result<BranchDetail>

    suspend fun getCategories(branchId: String): Result<List<CategoryOption>>

    suspend fun createBranch(
        name: String,
        location: String,
    ): Result<Branch>

    suspend fun createProduct(
        branchId: String,
        name: String,
        categoryName: String?,
        priceCents: Long,
        stock: Int,
    ): Result<Unit>

    suspend fun restock(
        productId: String,
        stockQuantity: Int,
    ): Result<Unit>

    suspend fun deleteProduct(productId: String): Result<Unit>

    suspend fun registerDevice(
        branchId: String,
        name: String,
    ): Result<DeviceRegistration>

    suspend fun revokeDevice(deviceId: String): Result<Unit>

    // Last-known-good snapshots, available while offline.
    fun cachedDashboardSummary(): DashboardSummary?

    fun cachedBranchOverviews(): List<BranchOverview>?

    fun cachedInventory(): List<InventoryProduct>?

    fun cachedBranchDetail(branchId: String): BranchDetail?
}

@Singleton
class AdminRepositoryImpl
    @Inject
    constructor(
        private val networkService: NetworkService,
        private val snapshotStore: AdminSnapshotStore,
    ) : AdminRepository {
        private val branchesLock = Mutex()
        private val codeRandom = SecureRandom()

        @Volatile private var branchesCache: CachedBranches? = null

        private data class CachedBranches(
            val at: Long,
            val branches: List<Branch>,
        )

        private suspend fun <T> apiCall(block: suspend () -> Response<T>): Result<T> =
            try {
                val response = block()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Result.success(body)
                } else {
                    Result.failure(parseError(response))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        private fun parseError(response: Response<*>): AdminApiException =
            com.sixteenhalves.orangeadmin.ntwk.parseApiError(response)

        /**
         * Fetches the active branch list, coalescing concurrent callers (the
         * dashboard, branches and inventory screens all load on startup) and
         * serving a short-lived cache so startup costs one list request.
         * Failures are thrown; they are never disguised as an empty list.
         */
        private suspend fun loadBranches(): List<Branch> =
            branchesLock.withLock {
                val cached = branchesCache
                if (cached != null && System.currentTimeMillis() - cached.at < BRANCHES_TTL_MS) {
                    return@withLock cached.branches
                }
                val branches =
                    apiCall { networkService.getBranches() }
                        .getOrThrow()
                        .data
                        .orEmpty()
                        .map { it.toBranch() }
                        .filter { it.isActive }
                branchesCache = CachedBranches(System.currentTimeMillis(), branches)
                snapshotStore.saveBranches(branches)
                branches
            }

        private fun invalidateBranches() {
            branchesCache = null
        }

        private suspend fun fetchStats(branchId: String): BranchStats =
            apiCall { networkService.getBranchStatsToday(branchId) }
                .getOrThrow()
                .toBranchStats()

        override suspend fun getBranchOverviews(): Result<List<BranchOverview>> =
            try {
                val branches = loadBranches()
                val overviews =
                    coroutineScope {
                        branches
                            .map { branch -> async { BranchOverview(branch, fetchStats(branch.id)) } }
                            .awaitAll()
                    }
                snapshotStore.saveOverviews(overviews)
                Result.success(overviews)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun getDashboardSummary(): Result<DashboardSummary> =
            try {
                val summary =
                    apiCall { networkService.getDashboardSummary() }
                        .getOrThrow()
                        .toDashboardSummary()
                snapshotStore.saveDashboard(summary)
                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun getInventory(): Result<List<InventoryProduct>> =
            try {
                val branches = loadBranches()
                val items =
                    coroutineScope {
                        branches
                            .map { branch ->
                                async {
                                    val categories =
                                        apiCall { networkService.getBranchCategories(branch.id) }
                                            .getOrThrow()
                                            .data
                                            .orEmpty()
                                            .associate { it.id to (it.name ?: "") }
                                    val products =
                                        apiCall { networkService.getBranchProducts(branch.id) }
                                            .getOrThrow()
                                            .data
                                            .orEmpty()
                                    products.map { it.toInventoryProduct(categories, branch) }
                                }
                            }.awaitAll()
                            .flatten()
                    }
                snapshotStore.saveInventory(items)
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun getBranchDetail(branchId: String): Result<BranchDetail> =
            try {
                val branch =
                    apiCall { networkService.getBranch(branchId) }
                        .getOrThrow()
                        .toBranch()
                val categories =
                    apiCall { networkService.getBranchCategories(branchId) }
                        .getOrThrow()
                        .data
                        .orEmpty()
                        .associate { it.id to (it.name ?: "") }
                val products =
                    apiCall { networkService.getBranchProducts(branchId) }
                        .getOrThrow()
                        .data
                        .orEmpty()
                        .map { it.toInventoryProduct(categories, branch) }
                val devices =
                    apiCall { networkService.getBranchDevices(branchId) }
                        .getOrThrow()
                        .data
                        .orEmpty()
                        .map { it.toDeviceInfo() }
                val detail =
                    BranchDetail(
                        branch = branch,
                        stats = fetchStats(branchId),
                        products = products,
                        devices = devices,
                    )
                snapshotStore.saveBranchDetail(branchId, detail)
                Result.success(detail)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun getCategories(branchId: String): Result<List<CategoryOption>> =
            apiCall { networkService.getBranchCategories(branchId) }.map { envelope ->
                envelope.data.orEmpty().mapNotNull { dto ->
                    dto.id?.let { CategoryOption(it, dto.name ?: "") }
                }
            }

        override suspend fun createBranch(
            name: String,
            location: String,
        ): Result<Branch> {
            var lastCollision: Throwable? = null
            // The generated code is random; retry on the (rare) 409 collision.
            repeat(3) {
                try {
                    val branch =
                        apiCall { networkService.createBranch(CreateBranchRequest(name, generatedBranchCode(name), location)) }
                            .getOrThrow()
                            .toBranch()
                    invalidateBranches()
                    return Result.success(branch)
                } catch (e: AdminApiException) {
                    lastCollision = e
                    if (e.statusCode != 409) return Result.failure(e)
                } catch (e: Exception) {
                    return Result.failure(e)
                }
            }
            return Result.failure(lastCollision ?: IllegalStateException("Couldn't create branch"))
        }

        override suspend fun createProduct(
            branchId: String,
            name: String,
            categoryName: String?,
            priceCents: Long,
            stock: Int,
        ): Result<Unit> =
            try {
                val categoryId = resolveCategoryId(branchId, categoryName)
                apiCall {
                    networkService.createProduct(
                        branchId,
                        CreateProductRequest(
                            name = name,
                            categoryId = categoryId,
                            priceCents = priceCents,
                            stockQuantity = stock,
                        ),
                    )
                }.getOrThrow()
                invalidateBranches()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

        private suspend fun resolveCategoryId(
            branchId: String,
            categoryName: String?,
        ): String? {
            val trimmed = categoryName?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val existing =
                apiCall { networkService.getBranchCategories(branchId) }
                    .getOrThrow()
                    .data
                    .orEmpty()
                    .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            if (existing?.id != null) return existing.id
            return apiCall { networkService.createCategory(branchId, CreateCategoryRequest(trimmed)) }
                .getOrThrow()
                .id
        }

        override suspend fun restock(
            productId: String,
            stockQuantity: Int,
        ): Result<Unit> =
            try {
                apiCall { networkService.restockProduct(productId, RestockRequest(stockQuantity)) }
                    .getOrThrow()
                invalidateBranches()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun deleteProduct(productId: String): Result<Unit> =
            try {
                val response = networkService.deleteProduct(productId)
                if (response.isSuccessful) {
                    invalidateBranches()
                    Result.success(Unit)
                } else {
                    Result.failure(parseError(response))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun registerDevice(
            branchId: String,
            name: String,
        ): Result<DeviceRegistration> =
            apiCall { networkService.createDevice(branchId, CreateDeviceRequest(name)) }
                .map {
                    DeviceRegistration(
                        code = it.deviceCode.orEmpty(),
                        token = it.deviceToken.orEmpty(),
                    )
                }

        override suspend fun revokeDevice(deviceId: String): Result<Unit> =
            try {
                val response = networkService.revokeDevice(deviceId)
                if (response.isSuccessful) {
                    invalidateBranches()
                    Result.success(Unit)
                } else {
                    Result.failure(parseError(response))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override fun cachedDashboardSummary(): DashboardSummary? = snapshotStore.readDashboard()

        override fun cachedBranchOverviews(): List<BranchOverview>? =
            snapshotStore.readOverviews()?.takeUnless { it.isEmpty() }

        override fun cachedInventory(): List<InventoryProduct>? = snapshotStore.readInventory()

        override fun cachedBranchDetail(branchId: String): BranchDetail? = snapshotStore.readBranchDetail(branchId)

        private fun generatedBranchCode(name: String): String {
            val base =
                name.uppercase()
                    .filter { it.isLetterOrDigit() }
                    .take(5)
                    .ifEmpty { "BRANCH" }
            val suffix =
                (1..6)
                    .map { CODE_ALPHABET[codeRandom.nextInt(CODE_ALPHABET.length)] }
                    .joinToString("")
            return "$base-$suffix"
        }

        private fun BranchDto.toBranch(): Branch =
            Branch(
                id = id.orEmpty(),
                name = name.orEmpty(),
                code = code.orEmpty(),
                location = location.orEmpty(),
                isActive = isActive ?: true,
            )

        private fun BranchStatsDto.toBranchStats(): BranchStats =
            BranchStats(
                devicesCount = devicesCount ?: 0,
                todaySalesCents = salesTotalCents ?: 0,
                salesCount = salesCount ?: 0,
                lowStockCount = lowStockCount ?: 0,
                topSellers =
                    topSellers.orEmpty().mapNotNull { seller ->
                        seller.name?.let { TopSeller(it, seller.quantitySold ?: 0) }
                    },
            )

        private fun DashboardSummaryDto.toDashboardSummary(): DashboardSummary =
            DashboardSummary(
                branchesCount = branchesCount ?: 0,
                activeDevices = activeDevices ?: 0,
                salesTodayCents = salesTodayCents ?: 0,
                salesTodayCount = salesTodayCount ?: 0,
                pendingPayments = pendingPayments ?: 0,
                lowStockItems = lowStockItems ?: 0,
            )

        private fun ProductDto.toInventoryProduct(
            categories: Map<String?, String>,
            branch: Branch,
        ): InventoryProduct =
            InventoryProduct(
                id = id.orEmpty(),
                name = name.orEmpty(),
                categoryName = categories[categoryId] ?: "Uncategorized",
                branchName = branch.name,
                stock = stockQuantity ?: 0,
                priceCents = priceCents ?: 0,
            )

        private fun DeviceDto.toDeviceInfo(): DeviceInfo =
            DeviceInfo(
                id = deviceId.orEmpty(),
                name = name.orEmpty(),
                code = deviceCode.orEmpty(),
                isRevoked = isRevoked ?: false,
            )

        private companion object {
            const val BRANCHES_TTL_MS = 3_000L
            const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        }
    }
