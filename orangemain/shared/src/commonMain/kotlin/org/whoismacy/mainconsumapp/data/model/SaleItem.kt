package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SaleItem(
    val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
)
