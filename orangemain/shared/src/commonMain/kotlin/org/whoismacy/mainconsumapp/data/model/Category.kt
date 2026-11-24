package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
)
