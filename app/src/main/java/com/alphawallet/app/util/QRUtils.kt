package com.alphawallet.app.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.alphawallet.app.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

object QRUtils {
    @JvmStatic
    fun createQRImage(context: Context, address: String?, imageSize: Int): Bitmap? {
        try {
            val bitMatrix = MultiFormatWriter().encode(
                address,
                BarcodeFormat.QR_CODE,
                imageSize,
                imageSize,
                null
            )
            val barcodeEncoder = BarcodeEncoder()
            return barcodeEncoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.error_fail_generate_qr),
                Toast.LENGTH_SHORT
            )
                .show()
        }
        return null
    }
}
