package id.photobooth.app

import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Server HTTP mini di dalam tablet. Tamu yang terhubung ke Wi-Fi / hotspot yang sama
 * memindai QR dan membuka http://IP:PORT/s/<id-sesi> untuk mengunduh foto.
 * Tidak memerlukan internet.
 */
class PhotoServer(private val root: File, private val titleProvider: () -> String) {
    @Volatile private var socket: ServerSocket? = null
    @Volatile var port: Int = 8080
        private set
    private val pool = Executors.newFixedThreadPool(6)
    private val safe = Regex("^[A-Za-z0-9_.-]+$")

    val isRunning: Boolean get() = socket?.isClosed == false

    @Synchronized
    fun start(p: Int) {
        if (isRunning && port == p) return
        stop()
        port = p
        val ss = try {
            ServerSocket(p)
        } catch (e: Exception) {
            Log.e("PhotoServer", "Gagal membuka port $p", e); return
        }
        socket = ss
        thread(isDaemon = true, name = "photo-server") {
            try {
                while (!ss.isClosed) {
                    val client = ss.accept()
                    pool.execute { handle(client) }
                }
            } catch (e: Exception) {
                Log.i("PhotoServer", "Server berhenti: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun sessionUrl(ip: String, sessionId: String) = "http://$ip:$port/s/$sessionId"

    private fun handle(client: Socket) {
        client.use { s ->
            try {
                s.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                while (true) {
                    val h = reader.readLine() ?: break
                    if (h.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val target = parts[1]
                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "")
                val seg = path.trim('/').split('/')
                val out = BufferedOutputStream(s.getOutputStream())
                when {
                    seg.size == 2 && seg[0] == "s" && validName(seg[1]) -> page(out, seg[1])
                    seg.size == 3 && seg[0] == "f" && validName(seg[1]) && validName(seg[2]) ->
                        file(out, seg[1], seg[2], query.contains("dl=1"))
                    else -> respond(out, 404, "text/html; charset=utf-8", notFound().toByteArray())
                }
                out.flush()
            } catch (e: Exception) {
                Log.w("PhotoServer", "Permintaan gagal: ${e.message}")
            }
        }
    }

    private fun validName(n: String) = n != "." && n != ".." && !n.startsWith(".") && safe.matches(n)

    private fun resolve(sessionId: String, name: String? = null): File? {
        val dir = File(root, sessionId)
        val f = if (name == null) dir else File(dir, name)
        val canonRoot = root.canonicalPath + File.separator
        return if (f.canonicalPath.startsWith(canonRoot) && f.exists()) f else null
    }

    private fun page(out: OutputStream, sessionId: String) {
        val dir = resolve(sessionId)
        if (dir == null || !dir.isDirectory) {
            respond(out, 404, "text/html; charset=utf-8", notFound().toByteArray()); return
        }
        val files = dir.listFiles(java.io.FileFilter { f -> f.name.endsWith(".jpg") })?.sortedWith(
            compareBy<File>({ it.name != Storage.COLLAGE }, { it.name })
        ) ?: emptyList()
        val title = esc(titleProvider())
        val items = files.joinToString("\n") { f ->
            val label = if (f.name == Storage.COLLAGE) "Kolase" else f.nameWithoutExtension.replace('_', ' ').replaceFirstChar { it.uppercase() }
            """<div class="card"><img src="/f/$sessionId/${f.name}" alt="$label" loading="lazy">
               <a class="btn" href="/f/$sessionId/${f.name}?dl=1" download="${f.name}">Unduh $label</a></div>"""
        }
        val html = """<!doctype html><html lang="id"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title — Foto Anda</title>
<style>
body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#14111a;color:#f7f2ea;text-align:center}
h1{font-family:Georgia,serif;margin:28px 16px 4px;font-size:26px}
p{color:#b3aab8;margin:0 16px 20px;font-size:14px}
.card{max-width:520px;margin:0 auto 26px;padding:0 16px}
img{width:100%;border-radius:12px;display:block;margin-bottom:10px;background:#222}
.btn{display:block;background:#e8b04b;color:#14111a;font-weight:700;padding:14px;border-radius:30px;text-decoration:none}
</style></head><body>
<h1>$title</h1><p>Terima kasih sudah berfoto! Ketuk tombol untuk menyimpan.<br>iPhone: tekan lama gambar lalu pilih "Simpan ke Foto".</p>
$items
</body></html>"""
        respond(out, 200, "text/html; charset=utf-8", html.toByteArray())
    }

    private fun file(out: OutputStream, sessionId: String, name: String, download: Boolean) {
        val f = resolve(sessionId, name)
        if (f == null || !f.isFile) {
            respond(out, 404, "text/html; charset=utf-8", notFound().toByteArray()); return
        }
        val headers = StringBuilder()
            .append("HTTP/1.1 200 OK\r\n")
            .append("Content-Type: image/jpeg\r\n")
            .append("Content-Length: ${f.length()}\r\n")
            .append("Cache-Control: no-store\r\n")
        if (download) headers.append("Content-Disposition: attachment; filename=\"$name\"\r\n")
        headers.append("Connection: close\r\n\r\n")
        out.write(headers.toString().toByteArray())
        f.inputStream().use { it.copyTo(out, 64 * 1024) }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = if (code == 200) "OK" else "Not Found"
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
    }

    private fun notFound() = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:sans-serif;background:#14111a;color:#f7f2ea;text-align:center;padding:40px">
<h2>Foto tidak ditemukan</h2><p>Pastikan HP terhubung ke Wi-Fi photobooth, lalu pindai ulang kode QR.</p></body></html>"""

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
