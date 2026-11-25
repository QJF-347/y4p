package org.whoismacy.mainconsumapp.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.whoismacy.mainconsumapp.data.local.OrangeDatabase
import org.whoismacy.mainconsumapp.data.local.SaleEntity
import org.whoismacy.mainconsumapp.data.remote.BackendApi
import org.whoismacy.mainconsumapp.data.remote.ProductPush
import org.whoismacy.mainconsumapp.data.remote.PushRequest
import org.whoismacy.mainconsumapp.data.remote.SaleItemPush
import org.whoismacy.mainconsumapp.data.remote.SalePush
import org.whoismacy.mainconsumapp.data.repository.SettingsRepository
import org.whoismacy.mainconsumapp.util.UuidV7
import kotlin.math.roundToLong

data class PushOutcome(
    val configured: Boolean,
    val attempted: Int,
    val synced: Int,
    val failures: List<String>,
)

data class PullOutcome(
    val configured: Boolean,
    val serverVersion: Long,
    val products: Int,
    val salesUpdated: Int,
    val tombstonesApplied: Int,
)

/**
 * Offline-first sync against API_CONTRACT §4. Local rows are pushed as a batch
 * with per-row acks, and the catalog + server-owned M-Pesa status is pulled by
 * server_version watermark.
 */
class SyncService(
    private val database: OrangeDatabase,
    private val api: BackendApi,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    suspend fun pushPending(): PushOutcome = mutex.withLock {
        withContext(Dispatchers.Default) { pushLocked() }
    }

    /**
     * One full background cycle: upload local changes, download server changes,
     * then report liveness. A failure in one step must not stop the others, and
     * an unprovisioned till just skips the heartbeat.
     */
    suspend fun syncCycle() {
        runCatching { pushPending() }
        runCatching { pull() }
        runCatching { heartbeat() }
    }

    /** Sends the heartbeat with the current outbox depth for the admin device register. */
    suspend fun heartbeat() {
        val pending =
            database.productQueries.selectUnsynced().executeAsList().size +
                database.saleQueries.selectUnsynced().executeAsList().size
        api.heartbeat(outboxPending = pending)
    }

    suspend fun pull(): PullOutcome = mutex.withLock {
        withContext(Dispatchers.Default) { pullLocked() }
    }

    private suspend fun pushLocked(): PushOutcome {
        val config = settings.current()
            ?: return PushOutcome(configured = false, attempted = 0, synced = 0, failures = emptyList())

        val products = database.productQueries.selectUnsynced().executeAsList().take(MAX_PRODUCTS)
        val sales = database.saleQueries.selectUnsynced().executeAsList().take(MAX_SALES)
        if (products.isEmpty() && sales.isEmpty()) {
            return PushOutcome(configured = true, attempted = 0, synced = 0, failures = emptyList())
        }

        // Selling decrements the local counter. The backend re-applies that decrement when
        // it applies the sale, so a product whose sale is in this same batch has to be
        // pushed with the pre-sale counter or the server ends up short by those units.
        val soldByProduct = buildMap<String, Int> {
            for (sale in sales) {
                for (item in database.saleItemQueries.selectBySaleId(sale.id).executeAsList()) {
                    put(item.product_id, (get(item.product_id) ?: 0) + item.quantity.toInt())
                }
            }
        }
        // Only categories that came from the server carry a uuid this backend accepts.
        val serverCategoryIds = database.categoryQueries.selectAll()
            .executeAsList()
            .filter { UUID.matches(it.id) }
            .associate { it.name to it.id }

        val request = PushRequest(
            batchId = UuidV7.new(),
            deviceId = config.deviceId,
            branchId = config.branchId,
            products = products.map { row ->
                ProductPush(
                    id = row.id,
                    categoryId = serverCategoryIds[row.category],
                    name = row.name,
                    barcode = row.barcode,
                    priceCents = row.price.toCents(),
                    taxRateBp = DEFAULT_TAX_RATE_BP,
                    costPriceCents = row.cost_price?.toCents(),
                    stockQuantity = row.stock_quantity.toInt() + (soldByProduct[row.id] ?: 0),
                    imageUrl = row.image_url,
                    isActive = row.is_active == 1L,
                    createdAt = utcIso(row.created_at),
                    updatedAt = utcIso(row.updated_at),
                )
            },
            sales = sales.mapNotNull { sale -> sale.toPush(config.cashierId, config.cashierName, config.deviceId) },
        )

        val response = api.push(request)
        var synced = 0
        val failures = mutableListOf<String>()

        for (result in response.results) {
            when (result.status) {
                "SYNCED", "ALREADY_APPLIED" -> {
                    synced += 1
                    when (result.entity) {
                        "product" -> database.productQueries.markSynced(result.id)
                        "sale" -> {
                            database.saleQueries.markSynced(result.serverVersion, result.id)
                            result.paymentStatus?.let { status ->
                                database.saleQueries.updatePayment(status, null, result.id)
                            }
                        }
                    }
                }
                else -> failures += "${result.entity} ${result.id}: ${result.code ?: result.status}"
            }
        }

        return PushOutcome(
            configured = true,
            attempted = products.size + sales.size,
            synced = synced,
            failures = failures,
        )
    }

    private suspend fun pullLocked(): PullOutcome {
        var config = settings.current()
            ?: return PullOutcome(configured = false, serverVersion = 0, products = 0, salesUpdated = 0, tombstonesApplied = 0)

        var since = config.lastServerVersion
        var products = 0
        var salesUpdated = 0
        var tombstones = 0
        var pages = 0

        while (pages < MAX_PULL_PAGES) {
            pages += 1
            val page = api.pull(since)

            if (page.changes.categories.isNotEmpty()) {
                for (category in page.changes.categories) {
                    database.categoryQueries.upsertByName(
                        id = category.id,
                        name = category.name,
                        sort_order = category.sortOrder.toLong(),
                    )
                }
            }
            // Build from the whole local table (not just this page) so products pulled in a
            // later page still resolve categories pulled earlier.
            val categoryNames = database.categoryQueries.selectAll()
                .executeAsList()
                .associate { it.id to it.name }

            for (product in page.changes.products) {
                val existing = database.productQueries.selectById(product.id).executeAsOneOrNull()
                if (existing != null && existing.synced == 0L) continue // Local edit wins until it is pushed.

                database.productQueries.insert(
                    id = product.id,
                    name = product.name,
                    description = existing?.description ?: "",
                    barcode = product.barcode,
                    price = product.priceCents / 100.0,
                    cost_price = product.costPriceCents?.let { it / 100.0 },
                    category = product.categoryId?.let { categoryNames[it] }
                        ?: existing?.category
                        ?: "General",
                    image_url = product.imageUrl,
                    stock_quantity = product.stockQuantity.toLong(),
                    is_active = if (product.isActive) 1L else 0L,
                    created_at = existing?.created_at ?: product.updatedAt,
                    updated_at = product.updatedAt,
                    synced = 1L,
                )
                products += 1
            }

            for (sale in page.changes.sales) {
                val existing = database.saleQueries.selectById(sale.id).executeAsOneOrNull() ?: continue
                if (existing.payment_status == sale.paymentStatus && existing.mpesa_ref == sale.mpesaReceipt) continue
                database.saleQueries.updatePayment(sale.paymentStatus, sale.mpesaReceipt, sale.id)
                salesUpdated += 1
            }

            for (tombstone in page.tombstones) {
                when (tombstone.entity) {
                    "product" -> database.productQueries.markDeletedFromServer(tombstone.id)
                    "category" -> database.categoryQueries.delete(tombstone.id)
                    else -> continue
                }
                tombstones += 1
            }

            since = maxOf(since, page.serverVersion)
            settings.setLastServerVersion(since)
            if (!page.hasMore || page.serverVersion <= config.lastServerVersion) break
            config = config.copy(lastServerVersion = since)
        }

        return PullOutcome(
            configured = true,
            serverVersion = since,
            products = products,
            salesUpdated = salesUpdated,
            tombstonesApplied = tombstones,
        )
    }

    private fun SaleEntity.toPush(cashierId: String, cashierName: String, deviceId: String): SalePush? {
        val items = database.saleItemQueries.selectBySaleId(id).executeAsList()
        if (items.isEmpty()) return null
        val createdAt = utcIso(created_at)
        return SalePush(
            id = id,
            receiptNumber = receipt_number,
            deviceId = deviceId,
            cashierId = cashierId,
            cashierName = cashier_name ?: cashierName,
            paymentMethod = payment_method,
            subtotalCents = subtotal.toCents(),
            taxAmountCents = tax_amount.toCents(),
            totalAmountCents = total_amount.toCents(),
            amountReceivedCents = amount_received.toCents(),
            changeAmountCents = change_amount.toCents(),
            paymentStatus = payment_status,
            mpesaReceipt = mpesa_ref,
            createdAt = createdAt,
            updatedAt = createdAt,
            items = items.map { item ->
                SaleItemPush(
                    id = item.id,
                    productId = item.product_id,
                    productName = item.product_name,
                    quantity = item.quantity.toInt(),
                    unitPriceCents = item.unit_price.toCents(),
                    lineTotalCents = item.total_price.toCents(),
                )
            },
        )
    }

    private fun Double.toCents(): Long = (this * 100).roundToLong()

    private fun utcIso(localIso: String): String =
        runCatching {
            LocalDateTime.parse(localIso).toInstant(TimeZone.currentSystemDefault()).toString()
        }.getOrElse { localIso }

    private companion object {
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        const val MAX_PRODUCTS = 200
        const val MAX_SALES = 25
        const val MAX_PULL_PAGES = 20
        const val DEFAULT_TAX_RATE_BP = 1600
    }
}
