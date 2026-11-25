package org.whoismacy.mainconsumapp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.whoismacy.mainconsumapp.data.repository.DeviceSettings

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message)

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(val code: String = "HTTP_ERROR", val message: String = "Request failed")

// ---- Sync push (API_CONTRACT §4) ----

@Serializable
data class PushRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("branch_id") val branchId: String,
    val categories: List<CategoryPush> = emptyList(),
    val products: List<ProductPush> = emptyList(),
    val sales: List<SalePush> = emptyList(),
    val tombstones: List<TombstonePush> = emptyList(),
)

@Serializable
data class CategoryPush(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ProductPush(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("tax_rate_bp") val taxRateBp: Int,
    @SerialName("cost_price_cents") val costPriceCents: Long? = null,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SalePush(
    val id: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("cashier_name") val cashierName: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("subtotal_cents") val subtotalCents: Long,
    @SerialName("tax_amount_cents") val taxAmountCents: Long,
    @SerialName("total_amount_cents") val totalAmountCents: Long,
    @SerialName("amount_received_cents") val amountReceivedCents: Long,
    @SerialName("change_amount_cents") val changeAmountCents: Long,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("mpesa_receipt") val mpesaReceipt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val items: List<SaleItemPush>,
)

@Serializable
data class SaleItemPush(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("line_total_cents") val lineTotalCents: Long,
)

@Serializable
data class TombstonePush(
    val entity: String,
    val id: String,
    @SerialName("deleted_at") val deletedAt: String,
)

@Serializable
data class PushResponse(val results: List<PushResult> = emptyList())

@Serializable
data class PushResult(
    val entity: String,
    val id: String,
    val status: String,
    @SerialName("server_version") val serverVersion: Long? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    val code: String? = null,
    val message: String? = null,
)

// ---- Sync pull ----

@Serializable
data class PullResponse(
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("has_more") val hasMore: Boolean = false,
    val changes: PullChanges = PullChanges(),
    val tombstones: List<TombstoneRow> = emptyList(),
)

@Serializable
data class PullChanges(
    val categories: List<CategoryRow> = emptyList(),
    val products: List<ProductRow> = emptyList(),
    val sales: List<SaleRow> = emptyList(),
)

@Serializable
data class CategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ProductRow(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("price_cents") val priceCents: Long,
    @SerialName("cost_price_cents") val costPriceCents: Long? = null,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SaleRow(
    val id: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("mpesa_receipt") val mpesaReceipt: String? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
)

@Serializable
data class TombstoneRow(
    val entity: String,
    val id: String,
    @SerialName("deleted_at") val deletedAt: String,
)

// ---- Payments (API_CONTRACT §5) ----

@Serializable
data class StkPushRequest(
    @SerialName("sale_id") val saleId: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("amount_cents") val amountCents: Long? = null,
)

@Serializable
data class StkPushResponse(
    @SerialName("payment_transaction_id") val paymentTransactionId: String,
    @SerialName("checkout_request_id") val checkoutRequestId: String,
    // The 202 handler echoes the transaction, not the sale; the sale is bound server-side.
    @SerialName("sale_id") val saleId: String? = null,
    val status: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("poll_after_ms") val pollAfterMs: Long? = null,
)

@Serializable
data class PaymentStatusResponse(
    @SerialName("payment_transaction_id") val paymentTransactionId: String,
    val status: String,
    @SerialName("result_desc") val resultDesc: String? = null,
    @SerialName("mpesa_receipt_number") val mpesaReceiptNumber: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("poll_after_ms") val pollAfterMs: Long? = null,
    val sale: PaymentSaleSummary,
)

@Serializable
private data class HeartbeatRequest(
    @SerialName("outbox_pending") val outboxPending: Int,
)

@Serializable
private data class HeartbeatResponse(
    @SerialName("server_time") val serverTime: String? = null,
)

@Serializable
data class PaymentSaleSummary(
    val id: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("mpesa_receipt") val mpesaReceipt: String? = null,
)

/**
 * Thin client for the y4p backend. Every call re-reads the device settings so a
 * freshly provisioned token takes effect without restarting the app.
 */
class BackendApi(
    private val http: HttpClient,
    private val settingsProvider: suspend () -> DeviceSettings?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun configured(): DeviceSettings =
        settingsProvider() ?: throw ApiException(0, "NOT_CONFIGURED", "This till has not been set up yet")

    private fun DeviceSettings.root() = baseUrl.trimEnd('/')

    suspend fun push(request: PushRequest): PushResponse {
        val s = configured()
        val res = http.post("${s.root()}/api/v1/sync/push") {
            header("x-device-token", s.deviceToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return res.decode()
    }

    suspend fun pull(since: Long): PullResponse {
        val s = configured()
        val res = http.get("${s.root()}/api/v1/sync/pull") {
            header("x-device-token", s.deviceToken)
            parameter("since", since)
        }
        return res.decode()
    }

    suspend fun stkPush(saleId: String, phoneNumber: String): StkPushResponse {
        val s = configured()
        val res = http.post("${s.root()}/api/v1/payments/stk-push") {
            header("x-device-token", s.deviceToken)
            header("idempotency-key", "stk-$saleId")
            contentType(ContentType.Application.Json)
            setBody(StkPushRequest(saleId = saleId, phoneNumber = phoneNumber))
        }
        return res.decode()
    }

    /**
     * Reports liveness and the outbox depth so the admin's device register can
     * flag a till that has stopped syncing. Only the call succeeding matters.
     */
    suspend fun heartbeat(outboxPending: Int) {
        val s = configured()
        val res = http.post("${s.root()}/api/v1/devices/heartbeat") {
            header("x-device-token", s.deviceToken)
            contentType(ContentType.Application.Json)
            setBody(HeartbeatRequest(outboxPending))
        }
        if (res.status.value !in 200..299) res.decode<HeartbeatResponse>()
    }

    suspend fun paymentStatus(paymentTransactionId: String): PaymentStatusResponse {
        val s = configured()
        val res = http.get("${s.root()}/api/v1/payments/$paymentTransactionId") {
            header("x-device-token", s.deviceToken)
        }
        return res.decode()
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (status.value in 200..299) return body()
        val text = body<String>()
        val parsed = runCatching { json.decodeFromString<ApiErrorEnvelope>(text) }.getOrNull()
        throw ApiException(
            status = status.value,
            code = parsed?.error?.code ?: "HTTP_${status.value}",
            message = parsed?.error?.message ?: fallbackMessage(status),
        )
    }

    private fun fallbackMessage(status: HttpStatusCode): String = when (status.value) {
        401, 403 -> "This till is not authorised. Check the device token in Device setup."
        404 -> "The backend could not find that record."
        409 -> "The record changed on the server. Sync and try again."
        422 -> "The backend rejected the request."
        else -> "Backend request failed (${status.value})."
    }
}
