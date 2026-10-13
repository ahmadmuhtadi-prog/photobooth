package id.photobooth.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Hasil kolase + QR unduh + tombol cetak (dengan batas cetak per sesi). */
class ResultActivity : BaseActivity() {

    companion object {
        const val EXTRA_SESSION = "session"
    }

    private lateinit var sessionId: String
    private lateinit var dir: File
    private var printsUsed = 0
    private var secondsLeft = 60

    private lateinit var btnPrint: Button
    private lateinit var txtTimer: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            secondsLeft--
            if (secondsLeft <= 0) {
                goHome(); return
            }
            txtTimer.text = "Kembali ke awal dalam $secondsLeft detik"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        sessionId = intent.getStringExtra(EXTRA_SESSION) ?: run { finish(); return }
        dir = Storage.sessionDir(this, sessionId)
        printsUsed = savedInstanceState?.getInt("prints", 0) ?: 0

        // Potret: kolase di atas, panel di bawah. Lanskap: berdampingan.
        if (!prefs.landscape) {
            findViewById<LinearLayout>(R.id.root).orientation = LinearLayout.VERTICAL
            listOf(R.id.collageView, R.id.panelScroll).forEach { id ->
                val v = findViewById<View>(id)
                v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
        }

        btnPrint = findViewById(R.id.btnPrint)
        txtTimer = findViewById(R.id.txtTimer)
        findViewById<Button>(R.id.btnDone).setOnClickListener { goHome() }
        btnPrint.setOnClickListener { doPrint() }
        updatePrintButton()

        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val sheet = File(dir, Storage.SHEET)
                Storage.decodeFile(if (sheet.exists()) sheet else File(dir, Storage.COLLAGE), 1400)
            }
            findViewById<ImageView>(R.id.collageView).setImageBitmap(bmp)
        }
        setupQr()

        secondsLeft = prefs.resultTimeoutSec
        handler.postDelayed(tick, 1000)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("prints", printsUsed)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        secondsLeft = prefs.resultTimeoutSec
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun goHome() {
        handler.removeCallbacks(tick)
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // ---------- QR ----------

    private fun setupQr() {
        val qrImage = findViewById<ImageView>(R.id.qrImage)
        val qrProgress = findViewById<ProgressBar>(R.id.qrProgress)
        val qrCaption = findViewById<TextView>(R.id.qrCaption)
        val title = findViewById<TextView>(R.id.txtQrTitle)

        val ip = if (BoothApp.instance.server.isRunning) NetUtil.localIp() else null
        val localUrl = ip?.let { BoothApp.instance.server.sessionUrl(it, sessionId) }

        fun showLocal() {
            if (localUrl == null) {
                qrProgress.visibility = View.GONE
                title.text = "Foto tersimpan"
                qrCaption.text = "Kode QR tidak tersedia (tablet belum terhubung Wi-Fi/hotspot)."
                return
            }
            showQr(qrImage, qrProgress, localUrl)
            title.text = if (prefs.wifiSsid.isNotBlank()) "1. Sambung Wi-Fi  •  2. Pindai foto" else "Pindai untuk mengunduh foto"
            qrCaption.text = if (prefs.wifiSsid.isNotBlank()) "Unduh foto (setelah tersambung ke Wi-Fi \"${prefs.wifiSsid}\")"
            else "HP harus tersambung ke Wi-Fi yang sama dengan tablet"
            if (prefs.wifiSsid.isNotBlank()) {
                findViewById<View>(R.id.wifiBox).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.wifiQr).setImageBitmap(QrUtil.make(QrUtil.wifiPayload(prefs.wifiSsid, prefs.wifiPass), 400))
                findViewById<TextView>(R.id.wifiCaption).text = "Sambung ke Wi-Fi \"${prefs.wifiSsid}\""
            }
        }

        if (!prefs.cloudReady) {
            showLocal(); return
        }

        qrCaption.text = "Mengunggah ke cloud..."
        lifecycleScope.launch {
            // Unggah berjalan terpisah; jika lewat 30 detik kita pakai QR lokal, unggahan tetap diteruskan.
            val appCtx = applicationContext
            val upload = CoroutineScope(Dispatchers.IO).async {
                runCatching { CloudSync.uploadSession(appCtx, dir) }.getOrNull()
            }
            val url = withTimeoutOrNull(30_000L) { upload.await() }
            if (url != null) {
                showQr(qrImage, qrProgress, url)
                title.text = "Pindai untuk mengunduh foto"
                qrCaption.text = "Tautan Google Drive — bisa dibuka dari jaringan mana saja"
            } else {
                showLocal()
                if (localUrl != null) Toast.makeText(this@ResultActivity, "Cloud belum terjangkau, memakai Wi-Fi lokal. Foto akan diunggah otomatis nanti.", Toast.LENGTH_LONG).show()
                CloudSync.syncAll(this@ResultActivity)
            }
        }
    }

    private fun showQr(iv: ImageView, progress: ProgressBar, text: String) {
        lifecycleScope.launch {
            val bmp: Bitmap = withContext(Dispatchers.Default) { QrUtil.make(text, 600) }
            iv.setImageBitmap(bmp)
            progress.visibility = View.GONE
        }
    }

    // ---------- Cetak ----------

    private fun updatePrintButton() {
        val limit = prefs.printLimit
        if (limit <= 0 || !PrintHelper.systemSupportsPrint()) {
            btnPrint.visibility = View.GONE; return
        }
        val left = (limit - printsUsed).coerceAtLeast(0)
        btnPrint.isEnabled = left > 0
        btnPrint.text = if (left > 0) "CETAK (sisa $left)" else "BATAS CETAK TERCAPAI"
    }

    private fun doPrint() {
        if (printsUsed >= prefs.printLimit) return
        printsUsed++
        updatePrintButton()
        lifecycleScope.launch {
            val sheet = withContext(Dispatchers.IO) {
                // Dua strip berbeda sudah digabung saat sesi foto.
                val pair = File(dir, Storage.SHEET)
                if (pair.exists()) return@withContext Storage.decodeFile(pair, 1800)
                val full = Storage.decodeFile(File(dir, Storage.COLLAGE), 1800) ?: return@withContext null
                val layout = if (full.width * 2 <= full.height) LayoutType.STRIP_2X6 else LayoutType.GRID_4X6
                val s = Collage.printSheet(full, layout)
                if (s !== full) full.recycle()
                s
            }
            if (sheet == null) {
                Toast.makeText(this@ResultActivity, "File kolase tidak ditemukan", Toast.LENGTH_SHORT).show(); return@launch
            }
            PrintHelper(this@ResultActivity).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                orientation = PrintHelper.ORIENTATION_PORTRAIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
            }.printBitmap("Photobooth-$sessionId", sheet)
        }
    }
}
