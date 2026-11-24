package com.sixteenhalves.orangeadmin.utils

import java.util.Locale

/**
 * Formats integer KES minor units (cents) for display, per DATA_CONTRACT.md:
 * money is stored as integer cents, never floating point.
 *
 * 425080L -> "KSh 4,250.80"
 */
fun formatKes(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return String.format(Locale.US, "%sKSh %,d.%02d", sign, abs / 100, abs % 100)
}
