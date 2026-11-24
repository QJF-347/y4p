package com.sixteenhalves.orangeadmin.components.shared

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code. The POS app scans this from its device setup
 * screen, so the payload is the same labelled text "Copy details" produces.
 */
@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(content) { runCatching { encodeQr(content) }.getOrNull() } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Device setup QR code",
        modifier = modifier.background(Color.White).padding(8.dp),
    )
}

private const val QR_SIZE = 640
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Separated from [encodeQr] so the payload can be decoded back in a JVM test. */
internal fun encodeQrMatrix(
    content: String,
    size: Int = QR_SIZE,
): BitMatrix {
    val hints =
        mapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 4,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
    return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
}

private fun encodeQr(content: String): Bitmap {
    val matrix = encodeQrMatrix(content)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix[x, y]) BLACK else WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
