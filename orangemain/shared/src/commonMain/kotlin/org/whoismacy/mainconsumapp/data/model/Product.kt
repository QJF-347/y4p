package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val description: String = "",
    val barcode: String? = null,
    val price: Double,
    val costPrice: Double? = null,
    val category: String = "General",
    val imageUrl: String? = null,
    val stockQuantity: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
    val synced: Boolean = false,
)
