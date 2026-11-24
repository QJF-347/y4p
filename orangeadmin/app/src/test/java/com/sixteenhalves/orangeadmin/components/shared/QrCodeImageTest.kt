package com.sixteenhalves.orangeadmin.components.shared

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeImageTest {

    private val payload =
        "Backend URL: https://y4p.onrender.com\n" +
            "Device token: dvc_YIqqVyitSCgJN2RkVK3jmHgMGnkl9J-lcGf3wKkw0TE\n" +
            "Device id: 01a20f1c-b2a5-716e-8064-771151394852\n" +
            "Branch id: 01a20f06-a990-744a-8340-73d63096d5fc"

    @Test
    fun encodedQrDecodesBackToTheDeviceDetails() {
        val matrix = encodeQrMatrix(payload)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val source = RGBLuminanceSource(width, height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))

        assertEquals(payload, decoded.text)
    }
}
