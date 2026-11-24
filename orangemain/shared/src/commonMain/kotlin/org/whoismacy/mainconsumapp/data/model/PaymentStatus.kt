package org.whoismacy.mainconsumapp.data.model

import kotlinx.serialization.Serializable

/**
 * Server-owned payment state (DATA_CONTRACT §4.1): in v1 PENDING moves to
 * PAID or FAILED and both outcomes are terminal.
 */
@Serializable
enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
}
