package id.photobooth.app

import android.content.Context
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Unggah ke Google Drive melalui Google Apps Script Web App.
 * Tidak butuh Google Play Services, jadi berjalan di tablet Huawei.
 */
object CloudUploader {
    fun upload(scriptUrl: String, key: String, file: File, name: String, folder: String): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("key", key)
            .put("folder", folder)
            .put("filename", name)
            .put("mimeType", "image/jpeg")
            .put("data", b64)
            .toString()

        var conn = (URL(scriptUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 90000
            setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        var code = conn.responseCode
        var hops = 0
        // Apps Script membalas 302 ke googleusercontent.com — ikuti secara manual dengan GET.
        while (code in 300..399 && hops < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = (URL(loc).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 60000
            }
            code = conn.responseCode
            hops++
        }
        val text = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
        if (code !in 200..299) throw IOException("Server cloud membalas HTTP $code")
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("Balasan bukan JSON. Pastikan Web App di-deploy dengan akses \"Siapa saja\" (Anyone).")
        }
        if (!json.optBoolean("ok")) throw IOException(json.optString("error", "Unggah gagal"))
        return json.getString("url")
    }
}

/** Antrean unggah otomatis: sesi yang gagal diunggah akan dicoba ulang. */
object CloudSync {
    private val exec = Executors.newSingleThreadExecutor()
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private const val MARKER = "cloud_url.txt"

    fun cloudUrl(dir: File): String? =
        File(dir, MARKER).takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }

    fun pendingCount(ctx: Context): Int =
        Storage.sessionsRoot(ctx).listFiles()?.count { it.isDirectory && File(it, Storage.COLLAGE).exists() && cloudUrl(it) == null } ?: 0

    /** Unggah satu sesi (dipanggil dari thread latar). Melempar exception jika gagal. */
    fun uploadSession(ctx: Context, dir: File): String {
        cloudUrl(dir)?.let { return it }
        val p = BoothApp.instance.prefs
        if (!inFlight.add(dir.name)) {
            repeat(120) {
                Thread.sleep(500)
                cloudUrl(dir)?.let { return it }
                if (!inFlight.contains(dir.name)) return cloudUrl(dir) ?: throw IOException("Unggah gagal")
            }
            throw IOException("Unggah terlalu lama")
        }
        try {
            val folder = p.homeTitle.ifBlank { "Photobooth" }
            // Jika ada lembar berisi 2 strip, itu yang diunggah (berisi kedua foto).
            val sheet = File(dir, Storage.SHEET)
            val src = if (sheet.exists()) sheet else File(dir, Storage.COLLAGE)
            val url = CloudUploader.upload(p.cloudUrl, p.cloudKey, src, "${dir.name}.jpg", folder)
            File(dir, MARKER).writeText(url)
            return url
        } finally {
            inFlight.remove(dir.name)
        }
    }

    /** Coba unggah semua sesi yang belum terunggah (berjalan di latar). */
    fun syncAll(ctx: Context) {
        val app = ctx.applicationContext
        if (!BoothApp.instance.prefs.cloudReady) return
        exec.execute {
            val dirs = Storage.sessionsRoot(app).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: return@execute
            for (dir in dirs) {
                if (!File(dir, Storage.COLLAGE).exists() || cloudUrl(dir) != null || inFlight.contains(dir.name)) continue
                // Lewati sesi yang mungkin masih berlangsung (menunggu strip ke-2).
                if (System.currentTimeMillis() - dir.lastModified() < 5 * 60_000L) continue
                try {
                    uploadSession(app, dir)
                } catch (e: Exception) {
                    break // kemungkinan tidak ada internet; coba lagi nanti
                }
            }
        }
    }
}
