package id.photobooth.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Storage {
    const val COLLAGE = "kolase.jpg"
    private val rnd = SecureRandom()
    private const val CHARS = "abcdefghijkmnpqrstuvwxyz23456789"

    fun sessionsRoot(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir, "sesi").apply { mkdirs() }

    /** ID sesi = waktu + kode acak, agar tamu tidak bisa menebak foto orang lain. */
    fun newSessionId(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val code = (1..6).map { CHARS[rnd.nextInt(CHARS.length)] }.joinToString("")
        return "$ts-$code"
    }

    fun sessionDir(ctx: Context, id: String): File = File(sessionsRoot(ctx), id).apply { mkdirs() }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = 92) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** Salin ke Galeri (Pictures/Photobooth) — Android 10 ke atas. */
    fun exportToGallery(ctx: Context, src: File, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
    }

    fun decodeFile(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight, maxSide) }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun loadUriBitmap(ctx: Context, uriStr: String, maxSide: Int): Bitmap? {
        if (uriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight, maxSide) }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    private fun sample(w: Int, h: Int, maxSide: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxSide || h / (s * 2) >= maxSide) s *= 2
        return s
    }
}
