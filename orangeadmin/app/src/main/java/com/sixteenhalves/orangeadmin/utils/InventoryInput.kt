package com.sixteenhalves.orangeadmin.utils

import java.math.BigDecimal

/** Pure parsing rules for the new-product form, kept free of Android types so they are unit-testable. */
object InventoryInput {
    private val pricePattern = Regex("""^\d{1,9}(\.\d{1,2})?$""")
    private val quantityPattern = Regex("""^\d{1,9}$""")

    /** Accepts "1200", "1,200", "12.5", "12.50"; returns null for anything else or a zero price. */
    fun priceCentsOrNull(raw: String): Long? {
        val normalized = raw.trim().replace(",", "")
        if (!pricePattern.matches(normalized)) return null
        val cents = BigDecimal(normalized).movePointRight(2)
        if (cents <= BigDecimal.ZERO) return null
        return runCatching { cents.longValueExact() }.getOrNull()
    }

    /** Accepts non-negative whole numbers up to 9 digits. */
    fun quantityOrNull(raw: String): Int? {
        val normalized = raw.trim()
        if (!quantityPattern.matches(normalized)) return null
        return normalized.toIntOrNull()
    }

    /** Quantity field may be left blank, which means zero. */
    fun quantityOrDefault(raw: String): Int? = if (raw.isBlank()) 0 else quantityOrNull(raw)
}
