package com.sixteenhalves.orangeadmin.ntwk

import com.google.gson.annotations.SerializedName
import com.sixteenhalves.orangeadmin.domain.User
import com.sixteenhalves.orangeadmin.viewmodels.AuthLoginFlowData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val user: User? = null,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class DeleteAccountRequest(val password: String)

data class UserEnvelope(val user: User?)

data class CreateUserRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    @SerializedName("branch_ids") val branchIds: List<String> = emptyList(),
)

interface NetworkService {
    @GET("/api/v1/auth/me")
    suspend fun getUser(): Response<UserEnvelope>

    // Invite-only: requires an OWNER session (API_CONTRACT §1.1).
    @POST("/api/v1/auth/users")
    suspend fun createUser(
        @Body request: CreateUserRequest,
    ): Response<UserEnvelope>

    @POST("/api/v1/auth/login")
    suspend fun loginUser(
        @Body request: AuthLoginFlowData,
    ): Response<AuthResponse>

    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshRequest,
    ): Response<AuthResponse>

    @POST("/api/v1/auth/logout")
    suspend fun logoutUser(
        @Body request: RefreshRequest,
    ): Response<Unit>

    @DELETE("/api/v1/auth/account")
    suspend fun deleteUser(
        @Body request: DeleteAccountRequest,
    ): Response<Unit>

    // --- Catalog (API_CONTRACT §4) ---

    @GET("/api/v1/branches")
    suspend fun getBranches(): Response<DataEnvelope<List<BranchDto>>>

    @POST("/api/v1/branches")
    suspend fun createBranch(
        @Body request: CreateBranchRequest,
    ): Response<BranchDto>

    @GET("/api/v1/branches/{id}")
    suspend fun getBranch(
        @Path("id") id: String,
    ): Response<BranchDto>

    @GET("/api/v1/branches/{id}/stats/today")
    suspend fun getBranchStatsToday(
        @Path("id") id: String,
    ): Response<BranchStatsDto>

    @GET("/api/v1/branches/{id}/categories")
    suspend fun getBranchCategories(
        @Path("id") id: String,
    ): Response<DataEnvelope<List<CategoryDto>>>

    @POST("/api/v1/branches/{id}/categories")
    suspend fun createCategory(
        @Path("id") id: String,
        @Body request: CreateCategoryRequest,
    ): Response<CategoryDto>

    @GET("/api/v1/branches/{id}/products")
    suspend fun getBranchProducts(
        @Path("id") id: String,
    ): Response<DataEnvelope<List<ProductDto>>>

    @POST("/api/v1/branches/{id}/products")
    suspend fun createProduct(
        @Path("id") id: String,
        @Body request: CreateProductRequest,
    ): Response<ProductDto>

    @POST("/api/v1/products/{id}/restock")
    suspend fun restockProduct(
        @Path("id") id: String,
        @Body request: RestockRequest,
    ): Response<ProductDto>

    @DELETE("/api/v1/products/{id}")
    suspend fun deleteProduct(
        @Path("id") id: String,
    ): Response<Unit>

    @GET("/api/v1/branches/{id}/devices")
    suspend fun getBranchDevices(
        @Path("id") id: String,
    ): Response<DataEnvelope<List<DeviceDto>>>

    @POST("/api/v1/branches/{id}/devices")
    suspend fun createDevice(
        @Path("id") id: String,
        @Body request: CreateDeviceRequest,
    ): Response<CreatedDeviceDto>

    @DELETE("/api/v1/devices/{deviceId}")
    suspend fun revokeDevice(
        @Path("deviceId") deviceId: String,
    ): Response<Unit>

    @GET("/api/v1/dashboard/summary")
    suspend fun getDashboardSummary(): Response<DashboardSummaryDto>
}

data class DataEnvelope<T>(
    val data: T? = null,
)

data class BranchDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    val location: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
)

data class CategoryDto(
    val id: String? = null,
    val name: String? = null,
)

data class ProductDto(
    val id: String? = null,
    @SerializedName("branch_id") val branchId: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    val name: String? = null,
    @SerializedName("price_cents") val priceCents: Long? = null,
    @SerializedName("stock_quantity") val stockQuantity: Int? = null,
)

data class DeviceDto(
    @SerializedName("device_id") val deviceId: String? = null,
    val name: String? = null,
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("is_revoked") val isRevoked: Boolean? = null,
)

data class TopSellerDto(
    @SerializedName("product_id") val productId: String? = null,
    val name: String? = null,
    @SerializedName("quantity_sold") val quantitySold: Int? = null,
    @SerializedName("revenue_cents") val revenueCents: Long? = null,
)

data class BranchStatsDto(
    @SerializedName("sales_total_cents") val salesTotalCents: Long? = null,
    @SerializedName("sales_count") val salesCount: Int? = null,
    @SerializedName("low_stock_count") val lowStockCount: Int? = null,
    @SerializedName("devices_count") val devicesCount: Int? = null,
    @SerializedName("top_sellers") val topSellers: List<TopSellerDto>? = null,
)

data class DashboardSummaryDto(
    @SerializedName("branches_count") val branchesCount: Int? = null,
    @SerializedName("active_devices") val activeDevices: Int? = null,
    @SerializedName("sales_today_cents") val salesTodayCents: Long? = null,
    @SerializedName("sales_today_count") val salesTodayCount: Int? = null,
    @SerializedName("pending_payments") val pendingPayments: Int? = null,
    @SerializedName("low_stock_items") val lowStockItems: Int? = null,
)

data class CreateBranchRequest(
    val name: String,
    val code: String,
    val location: String,
)

data class CreateCategoryRequest(
    val name: String,
)

data class CreateProductRequest(
    val name: String,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("price_cents") val priceCents: Long,
    @SerializedName("stock_quantity") val stockQuantity: Int,
)

data class RestockRequest(
    @SerializedName("stock_quantity") val stockQuantity: Int,
    val reason: String? = null,
)

data class CreateDeviceRequest(
    val name: String,
)

data class CreatedDeviceDto(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("device_token") val deviceToken: String? = null,
    @SerializedName("branch_id") val branchId: String? = null,
)
