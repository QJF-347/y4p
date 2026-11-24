package org.whoismacy.mainconsumapp.util

import kotlin.math.roundToInt

object CurrencyFormatter {
    private const val CURRENCY_CODE = "KES"

    fun format(amount: Double): String {
        val rounded = (amount * 100).roundToInt() / 100.0
        val parts = rounded.toString().split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2) else "00"

        val formattedInteger = buildString {
            integerPart.reversed().forEachIndexed { index, c ->
                if (index > 0 && index % 3 == 0) append(',')
                append(c)
            }
        }.reversed()

        return "$CURRENCY_CODE $formattedInteger.$decimalPart"
    }
}
