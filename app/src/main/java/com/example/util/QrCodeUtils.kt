package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeUtils {

    /**
     * Decodifica o texto/link contido na imagem de um QR Code a partir de seus bytes binários.
     */
    fun decodeQrCodeFromBytes(bytes: ByteArray): String? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converte o array de bytes em um Bitmap do Android para exibição.
     */
    fun decodeBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gera uma imagem Bitmap de QR Code a partir de um texto/URL.
     */
    fun generateQrBitmap(content: String, size: Int = 400): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
