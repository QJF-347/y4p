package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethod {
    CASH,
    MPESA,
}
