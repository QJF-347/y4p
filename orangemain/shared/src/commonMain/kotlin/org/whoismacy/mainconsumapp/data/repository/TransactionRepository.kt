package org.whoismacy.mainconsumapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.whoismacy.mainconsumapp.data.local.OrangeDatabase
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.PaymentStatus
import org.whoismacy.mainconsumapp.data.model.Sale
import org.whoismacy.mainconsumapp.data.model.SaleItem
import org.whoismacy.mainconsumapp.util.UuidV7
import kotlin.time.Clock

class TransactionRepository(
    private val database: OrangeDatabase,
    private val clock: Clock
) {

    private val saleQueries get() = database.saleQueries
    private val saleItemQueries get() = database.saleItemQueries
    private val productQueries get() = database.productQueries

    fun getAllSales(): Flow<List<Sale>> {
        return saleQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toSale() } }
    }

    fun getSaleById(id: String): Flow<Sale?> {
        return saleQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toSale() }
    }

    fun getSaleItems(saleId: String): Flow<List<SaleItem>> {
        return saleItemQueries.selectBySaleId(saleId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities ->
                entities.map { entity ->
                    SaleItem(
                        id = entity.id,
                        saleId = entity.sale_id,
                        productId = entity.product_id,
                        productName = entity.product_name,
                        quantity = entity.quantity.toInt(),
                        unitPrice = entity.unit_price,
                        totalPrice = entity.total_price,
                    )
                }
            }
    }

    fun completeSale(
        saleId: String,
        cartItems: List<CartItem>,
        paymentMethod: PaymentMethod,
        amountReceived: Double,
        mpesaRef: String? = null,
        cashierName: String? = null,
        taxRate: Double = 0.16,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
    ): Sale {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val subtotal = cartItems.sumOf { it.totalPrice }
        val taxAmount = subtotal * taxRate
        val totalAmount = subtotal + taxAmount
        val changeAmount = if (paymentMethod == PaymentMethod.CASH) amountReceived - totalAmount else 0.0
        val receiptNumber = generateReceiptNumber(now.date)

        val sale = Sale(
            id = saleId,
            receiptNumber = receiptNumber,
            subtotal = subtotal,
            taxAmount = taxAmount,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            amountReceived = amountReceived,
            changeAmount = changeAmount,
            mpesaRef = mpesaRef,
            cashierName = cashierName,
            createdAt = now.toString(),
            paymentStatus = paymentStatus,
        )

        database.transaction {
            saleQueries.insert(
                id = sale.id,
                receipt_number = sale.receiptNumber,
                subtotal = sale.subtotal,
                tax_amount = sale.taxAmount,
                total_amount = sale.totalAmount,
                payment_method = sale.paymentMethod.name,
                amount_received = sale.amountReceived,
                change_amount = sale.changeAmount,
                mpesa_ref = sale.mpesaRef,
                cashier_name = sale.cashierName,
                created_at = sale.createdAt,
                synced = 0L,
                payment_status = sale.paymentStatus.name,
            )

            cartItems.forEach { cartItem ->
                saleItemQueries.insert(
                    id = UuidV7.new(),
                    sale_id = saleId,
                    product_id = cartItem.product.id,
                    product_name = cartItem.product.name,
                    quantity = cartItem.quantity.toLong(),
                    unit_price = cartItem.product.price,
                    total_price = cartItem.totalPrice,
                )
            }

            // Deduct sold quantities in the same transaction as the sale, so stock can never
            // be recorded without its matching sale (and vice versa). Negative stock is allowed
            // offline per DATA_CONTRACT.md §6.4 and is flagged server-side after sync.
            cartItems.forEach { cartItem ->
                productQueries.decrementStock(
                    cartItem.quantity.toLong(),
                    sale.createdAt,
                    cartItem.product.id,
                )
            }
        }

        return sale
    }

    /** Applies the server-owned M-Pesa outcome (API_CONTRACT §4.1). */
    fun applyPaymentOutcome(saleId: String, status: PaymentStatus, mpesaRef: String?) {
        saleQueries.updatePayment(status.name, mpesaRef, saleId)
    }

    /** True once the backend has acknowledged this sale, so an STK push is safe to start. */
    fun isSaleSynced(saleId: String): Boolean =
        saleQueries.selectById(saleId).executeAsOneOrNull()?.synced == 1L

    private fun generateReceiptNumber(date: LocalDate): String {
        val dateStr = "${date.year}${date.month.number.toString().padStart(2, '0')}${date.day.toString().padStart(2, '0')}"
        val lastReceipt = saleQueries.getLastReceiptNumber().executeAsOneOrNull()
        val sequence = if (lastReceipt != null && lastReceipt.contains(dateStr)) {
            val lastSeq = lastReceipt.substringAfterLast("-").toIntOrNull() ?: 0
            lastSeq + 1
        } else {
            1
        }
        return "ORG-$dateStr-${sequence.toString().padStart(3, '0')}"
    }
}

private fun org.whoismacy.mainconsumapp.data.local.SaleEntity.toSale(): Sale {
    return Sale(
        id = id,
        receiptNumber = receipt_number,
        subtotal = subtotal,
        taxAmount = tax_amount,
        totalAmount = total_amount,
        paymentMethod = PaymentMethod.valueOf(payment_method),
        amountReceived = amount_received,
        changeAmount = change_amount,
        mpesaRef = mpesa_ref,
        cashierName = cashier_name,
        createdAt = created_at,
        synced = synced == 1L,
        paymentStatus = runCatching { PaymentStatus.valueOf(payment_status) }.getOrDefault(PaymentStatus.PAID),
        serverVersion = server_version,
    )
}
