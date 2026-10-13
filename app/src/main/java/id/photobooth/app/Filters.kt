package id.photobooth.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max

/** Filter warna. matrix() dipakai untuk live preview, apply() untuk foto akhir. */
enum class PhotoFilter(val label: String) {
    NORMAL("Normal"),
    BW("Hitam Putih"),
    SEPIA("Sepia"),
    BEAUTY("Beauty"),
    WARM("Hangat");

    fun matrix(): ColorMatrix? = when (this) {
        NORMAL -> null
        BW -> ColorMatrix().apply { setSaturation(0f) }.also {
            it.postConcat(contrast(1.12f))
        }
        SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        BEAUTY -> ColorMatrix(
            floatArrayOf(
                1.04f, 0f, 0f, 0f, 14f,
                0f, 1.02f, 0f, 0f, 12f,
                0f, 0f, 1.0f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        ).also { it.postConcat(ColorMatrix().apply { setSaturation(1.05f) }) }
        WARM -> ColorMatrix(
            floatArrayOf(
                1.10f, 0f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 0.88f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /** Mengembalikan bitmap baru (atau src apa adanya untuk NORMAL). */
    fun apply(src: Bitmap): Bitmap {
        var base = src
        if (this == BEAUTY) base = smoothSkin(src)
        val m = matrix() ?: return base
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) }
        Canvas(out).drawBitmap(base, 0f, 0f, p)
        if (base !== src) base.recycle()
        return out
    }

    companion object {
        private fun contrast(c: Float): ColorMatrix {
            val t = (1f - c) * 128f
            return ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        /** Penghalusan kulit sederhana: blur (turun-naik skala) lalu dicampur tipis ke foto asli. */
        private fun smoothSkin(src: Bitmap): Bitmap {
            var small = src
            var w = src.width
            var h = src.height
            repeat(3) {
                w = max(1, w / 2); h = max(1, h / 2)
                val next = Bitmap.createScaledBitmap(small, w, h, true)
                if (small !== src && next !== small) small.recycle()
                small = next
            }
            repeat(3) {
                w = if (it == 2) src.width else w * 2
                h = if (it == 2) src.height else h * 2
                val next = Bitmap.createScaledBitmap(small, w, h, true)
                if (small !== src && next !== small) small.recycle()
                small = next
            }
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(out).drawBitmap(small, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 95 })
            if (small !== src) small.recycle()
            return out
        }
    }
}
