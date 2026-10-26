package com.example.core.network

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    /**
     * Generates a high-contrast QR Bitmap from the given string.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 400): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            val fallback = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            fallback.eraseColor(Color.LTGRAY)
            fallback
        }
    }
}
