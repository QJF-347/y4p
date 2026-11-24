package org.whoismacy.mainconsumapp.util

import kotlin.random.Random

/**
 * Minimal RFC 9562 UUID v7 generator (48-bit unix-ms timestamp + random tail).
 * DATA_CONTRACT §1 requires client-created ids to be UUID v7/v5.
 */
object UuidV7 {
    fun new(timestampMillis: Long = currentTimeMillis()): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        for (i in 0 until 6) {
            bytes[i] = ((timestampMillis ushr (8 * (5 - i))) and 0xFF).toByte()
        }
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return format(bytes)
    }

    private fun format(bytes: ByteArray): String {
        val hex = buildString(36) {
            for ((index, byte) in bytes.withIndex()) {
                if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
                append(HEX[(byte.toInt() shr 4) and 0x0F])
                append(HEX[byte.toInt() and 0x0F])
            }
        }
        return hex
    }

    private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private const val HEX = "0123456789abcdef"
}
