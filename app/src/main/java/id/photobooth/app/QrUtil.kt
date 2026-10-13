package id.photobooth.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrUtil {
    fun make(text: String, size: Int = 600): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    /** Format standar QR untuk bergabung ke Wi-Fi. */
    fun wifiPayload(ssid: String, pass: String): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        return if (pass.isBlank()) "WIFI:T:nopass;S:${esc(ssid)};;"
        else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(pass)};;"
    }
}
