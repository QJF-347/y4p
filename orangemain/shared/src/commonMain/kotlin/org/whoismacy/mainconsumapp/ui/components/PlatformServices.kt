package org.whoismacy.mainconsumapp.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the platform QR scanner and hands the decoded text to [onResult].
 * The Android app provides an implementation; on targets without a scanner
 * the local is null and the UI hides the scan action.
 */
fun interface QrScanner {
    fun scan(onResult: (String) -> Unit)
}

/** Reads text the user copied, e.g. the admin app's "Copy credentials" output. */
fun interface ClipboardReader {
    fun readText(): String?
}

val LocalQrScanner = staticCompositionLocalOf<QrScanner?> { null }

val LocalClipboardReader = staticCompositionLocalOf<ClipboardReader?> { null }
