package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Sale(
    val id: String,
    val receiptNumber: String,
    val subtotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val paymentMethod: PaymentMethod,
    val amountReceived: Double,
    val changeAmount: Double,
    val cashAmount: Double = 0.0,
    val mpesaRef: String? = null,
    val cashierName: String? = null,
    val createdAt: String = "",
    val synced: Boolean = false,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val serverVersion: Long? = null,
)
