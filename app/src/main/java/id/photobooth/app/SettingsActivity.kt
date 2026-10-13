package id.photobooth.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Menu operator: semua pengaturan acara, template, kamera, cetak, dan berbagi. */
class SettingsActivity : BaseActivity() {
    override val immersive = false

    private lateinit var col: LinearLayout
    private val fg = 0xFFF7F2EA.toInt()
    private val muted = 0xFFB3AAB8.toInt()

    // Kontrol
    private lateinit var etHomeTitle: EditText
    private lateinit var swMenu: SwitchCompat
    private lateinit var spEvent: Spinner
    private val eventSwitches = mutableMapOf<EventType, SwitchCompat>()
    private lateinit var spTextEvent: Spinner
    private var textEvent = EventType.PERNIKAHAN
    private lateinit var swGuestFrame: SwitchCompat
    private lateinit var swAllFrames: SwitchCompat
    private lateinit var etLabel: EditText
    private lateinit var swTwoStrips: SwitchCompat
    private lateinit var txtFont: TextView
    private lateinit var etTitle: EditText
    private lateinit var etSubtitle: EditText
    private lateinit var etDate: EditText
    private lateinit var spFont: Spinner
    private lateinit var spLayout: Spinner
    private lateinit var spShots: Spinner
    private lateinit var spFrame: Spinner
    private lateinit var spText: Spinner
    private lateinit var txtLogo: TextView
    private lateinit var txtOverlay: TextView
    private lateinit var swFront: SwitchCompat
    private lateinit var swMirror: SwitchCompat
    private lateinit var spCountdown: Spinner
    private lateinit var spOrientation: Spinner
    private lateinit var spTimeout: Spinner
    private lateinit var spPrint: Spinner
    private lateinit var swLocal: SwitchCompat
    private lateinit var etPort: EditText
    private lateinit var etSsid: EditText
    private lateinit var etPass: EditText
    private lateinit var swCloud: SwitchCompat
    private lateinit var etCloudUrl: EditText
    private lateinit var etCloudKey: EditText
    private lateinit var txtCloudStatus: TextView
    private lateinit var swGallery: SwitchCompat
    private lateinit var etPin: EditText
    private lateinit var txtServer: TextView

    private var logoUri = ""
    private var overlayUri = ""

    private val countdownOpts = listOf(3, 5, 10)
    private val shotOpts = listOf(1, 2, 3, 4)
    private val timeoutOpts = listOf(30, 60, 90, 120)
    private val printOpts = listOf(0, 1, 2, 3, 5)

    private val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); logoUri = uri.toString(); refreshFileLabels() }
    }
    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = Fonts.install(this, uri)
            toast(if (ok) "Font tulisan tangan terpasang" else "File bukan font .ttf/.otf yang valid")
            refreshFileLabels()
        }
    }
    private val pickOverlay = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); overlayUri = uri.toString(); refreshFileLabels() }
    }

    private fun persist(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(ContextCompat.getColor(context, R.color.bg)) }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(48))
        }
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        val p = prefs
        logoUri = p.logoUri
        overlayUri = p.overlayUri

        heading("Pengaturan Photobooth")
        note("Tekan SIMPAN di bawah setelah mengubah pengaturan.")

        section("Menu Acara di Layar Awal")
        etHomeTitle = edit("Judul layar awal (mis. nama vendor / instansi)", p.homeTitle)
        swMenu = switch("Tampilkan menu pilihan acara di layar awal", p.eventMenu)
        note("Acara yang tampil di menu:")
        val enabled = p.enabledEvents
        EventType.values().forEach { ev ->
            eventSwitches[ev] = switch("${ev.emoji}  ${p.labelFor(ev)}  (${ev.frames.size} bingkai)", ev in enabled)
        }
        spEvent = spinner("Acara langsung (dipakai jika menu dimatikan)", EventType.values().map { it.label }, p.eventType.ordinal)
        swGuestFrame = switch("Tamu boleh memilih bingkai di layar foto", p.guestChooseFrame)
        swAllFrames = switch("Tampilkan semua bingkai di setiap acara (bukan hanya yang sesuai tema)", p.showAllFrames)

        section("Teks Bingkai per Acara")
        textEvent = p.eventType
        spTextEvent = spinner("Pilih acara yang ingin diubah", EventType.values().map { "${it.emoji}  ${p.labelFor(it)}" }, textEvent.ordinal)
        etLabel = edit("Nama acara di menu layar awal (mis. Wisuda UNM 2026 / CFD Pantai Losari)", p.labelFor(textEvent))
        etTitle = edit("Judul (mis. nama pengantin / nama kampus / nama komunitas)", p.titleFor(textEvent))
        etSubtitle = edit("Sub judul (mis. The Wedding of / Selamat Wisuda / Car Free Day)", p.subtitleFor(textEvent))
        row(
            button("Pratinjau Bingkai Acara Ini") { save(false); previewAll() }
        )
        etDate = edit("Tanggal acara (berlaku untuk semua acara)", p.eventDate)
        spFont = spinner("Gaya huruf (untuk bingkai Polos & Bingkai Saya)", listOf("Elegan (Serif)", "Modern (Sans)"), if (p.fontSerif) 0 else 1)
        note("Bingkai Sketsa Bunga menulis nama dengan huruf tulisan tangan. Untuk nama pengantin, tulis dengan tanda &, mis. \"Nasrina & Achmad\".")
        row(
            button("Pilih Font Tulisan Tangan") { pickFont.launch(arrayOf("*/*")) },
            button("Pakai Font Bawaan") { Fonts.scriptFile(this).delete(); refreshFileLabels() }
        )
        txtFont = note("")
        row(
            button("Pilih Logo") { pickLogo.launch(arrayOf("image/*")) },
            button("Hapus Logo") { logoUri = ""; refreshFileLabels() }
        )
        txtLogo = note("")

        section("Template & Tata Letak")
        spLayout = spinner("Format kolase", LayoutType.values().map { it.label }, p.layout.ordinal)
        spShots = spinner("Jumlah jepretan per sesi", shotOpts.map { "$it foto" }, shotOpts.indexOf(p.shotCount).coerceAtLeast(0))
        swTwoStrips = switch("Format strip: foto 2 kali agar 2 strip di lembar cetak berbeda", p.twoStrips)
        note("Jika aktif, setelah strip pertama tamu berfoto sekali lagi (bingkai boleh sama atau berbeda). Lembar 4x6 berisi 2 strip yang berbeda.")
        spFrame = spinner("Warna latar (untuk bingkai Polos & Bingkai Saya)", Palette.frameColors.map { it.first }, p.frameColorIndex)
        spText = spinner("Warna teks (untuk bingkai Polos & Bingkai Saya)", Palette.textColors.map { it.first }, p.textColorIndex)
        row(
            button("Pilih Bingkai PNG") { pickOverlay.launch(arrayOf("image/png", "image/*")) },
            button("Hapus Bingkai") { overlayUri = ""; refreshFileLabels() }
        )
        txtOverlay = note("")
        note("Bingkai PNG muncul sebagai pilihan \"Bingkai Saya (PNG)\". Bagian foto harus transparan. Ukuran ideal: 600x1800 px (strip) atau 1200x1800 px (4x6).")

        section("Kamera & Alur Foto")
        swFront = switch("Gunakan kamera depan", p.useFrontCamera)
        swMirror = switch("Cerminkan hasil foto (seperti cermin)", p.mirrorPhoto)
        spCountdown = spinner("Hitung mundur", countdownOpts.map { "$it detik" }, countdownOpts.indexOf(p.countdownSec).coerceAtLeast(0))
        spOrientation = spinner("Orientasi layar", listOf("Lanskap (mendatar)", "Potret (tegak)"), if (p.landscape) 0 else 1)
        spTimeout = spinner("Layar hasil kembali otomatis setelah", timeoutOpts.map { "$it detik" }, timeoutOpts.indexOf(p.resultTimeoutSec).coerceAtLeast(1))

        section("Cetak")
        spPrint = spinner("Batas cetak per sesi", printOpts.map { if (it == 0) "Nonaktifkan cetak" else "$it lembar" }, printOpts.indexOf(p.printLimit).coerceAtLeast(0))
        note("Printer harus terpasang di Android lewat plugin layanan cetak (mis. Canon PRINT, Epson iPrint, HP Print Service, atau Mopria). Pilih kertas 4x6 di dialog cetak.")

        section("Berbagi Digital")
        swLocal = switch("QR unduh via Wi-Fi lokal (tanpa internet)", p.localShare)
        etPort = edit("Port server", p.serverPort.toString(), InputType.TYPE_CLASS_NUMBER)
        txtServer = note("")
        etSsid = edit("Nama Wi-Fi untuk tamu (opsional, ditampilkan sebagai QR Wi-Fi)", p.wifiSsid)
        etPass = edit("Sandi Wi-Fi tamu", p.wifiPass)
        swCloud = switch("Unggah otomatis ke Google Drive", p.cloudEnabled)
        etCloudUrl = edit("URL Web App Google Apps Script (https://script.google.com/...)", p.cloudUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        etCloudKey = edit("Kunci rahasia (harus sama dengan SECRET di skrip)", p.cloudKey)
        row(
            button("Tes Unggah") { save(false); testCloud() },
            button("Unggah yang Tertunda") { save(false); CloudSync.syncAll(this); toast("Mengunggah di latar...") }
        )
        txtCloudStatus = note("")
        swGallery = switch("Simpan juga kolase ke Galeri tablet", p.saveToGallery)

        section("Keamanan")
        etPin = edit("PIN operator", p.pin, InputType.TYPE_CLASS_NUMBER)

        section("")
        row(
            button("SIMPAN") { save(true) },
            button("Folder Foto") { showFolder() }
        )

        refreshFileLabels()
        refreshStatus()

        var lastText = textEvent.ordinal
        spTextEvent.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == lastText) return
                // Simpan teks acara sebelumnya, lalu tampilkan teks acara yang baru dipilih.
                prefs.setTextFor(textEvent, etLabel.text.toString().trim(), etTitle.text.toString().trim(), etSubtitle.text.toString().trim())
                lastText = position
                textEvent = EventType.values()[position]
                etLabel.setText(prefs.labelFor(textEvent))
                etTitle.setText(prefs.titleFor(textEvent))
                etSubtitle.setText(prefs.subtitleFor(textEvent))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ---------- Simpan ----------

    private fun save(close: Boolean) {
        val p = prefs
        p.homeTitle = etHomeTitle.text.toString().trim()
        p.eventMenu = swMenu.isChecked
        p.enabledEvents = eventSwitches.filterValues { it.isChecked }.keys
        p.setTextFor(textEvent, etLabel.text.toString().trim(), etTitle.text.toString().trim(), etSubtitle.text.toString().trim())
        p.twoStrips = swTwoStrips.isChecked
        p.eventDate = etDate.text.toString().trim()
        p.fontSerif = spFont.selectedItemPosition == 0
        p.eventType = EventType.values()[spEvent.selectedItemPosition]
        p.guestChooseFrame = swGuestFrame.isChecked
        p.showAllFrames = swAllFrames.isChecked
        p.logoUri = logoUri
        p.overlayUri = overlayUri
        p.layout = LayoutType.values()[spLayout.selectedItemPosition]
        p.shotCount = shotOpts[spShots.selectedItemPosition]
        p.frameColorIndex = spFrame.selectedItemPosition
        p.textColorIndex = spText.selectedItemPosition
        p.useFrontCamera = swFront.isChecked
        p.mirrorPhoto = swMirror.isChecked
        p.countdownSec = countdownOpts[spCountdown.selectedItemPosition]
        p.landscape = spOrientation.selectedItemPosition == 0
        p.resultTimeoutSec = timeoutOpts[spTimeout.selectedItemPosition]
        p.printLimit = printOpts[spPrint.selectedItemPosition]
        p.localShare = swLocal.isChecked
        p.serverPort = etPort.text.toString().toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8080
        p.wifiSsid = etSsid.text.toString().trim()
        p.wifiPass = etPass.text.toString()
        p.cloudEnabled = swCloud.isChecked
        p.cloudUrl = etCloudUrl.text.toString().trim()
        p.cloudKey = etCloudKey.text.toString().trim()
        p.saveToGallery = swGallery.isChecked
        p.pin = etPin.text.toString().ifBlank { "1234" }
        BoothApp.instance.applySharing()
        refreshStatus()
        if (close) {
            toast("Pengaturan disimpan")
            finish()
        }
    }

    private fun refreshFileLabels() {
        txtLogo.text = if (logoUri.isBlank()) "Logo: (tidak ada)" else "Logo: terpilih ✓"
        txtOverlay.text = if (overlayUri.isBlank()) "Bingkai PNG: (tidak ada)" else "Bingkai PNG: terpilih ✓"
        txtFont.text = if (Fonts.scriptFile(this).exists()) "Font tulisan tangan: file sendiri ✓" else "Font tulisan tangan: bawaan tablet"
    }

    private fun refreshStatus() {
        val s = BoothApp.instance.server
        val ip = NetUtil.localIp()
        txtServer.text = when {
            !s.isRunning -> "Server lokal: mati"
            ip == null -> "Server lokal: aktif, tetapi tablet belum terhubung ke Wi-Fi / hotspot"
            else -> "Server lokal aktif di http://$ip:${s.port}"
        }
        txtCloudStatus.text = "Sesi menunggu unggah: ${CloudSync.pendingCount(this)}"
    }

    // ---------- Aksi ----------

    private fun preview() {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val spec = CollageSpec.fromPrefs(this@SettingsActivity, prefs, textEvent, Frames.available(prefs, textEvent).first())
                val photos = Collage.samplePhotos(prefs.shotCount)
                val c = Collage.render(spec, photos)
                photos.forEach { it.recycle() }
                spec.recycle()
                c
            }
            val iv = ImageView(this@SettingsActivity).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Pratinjau Template")
                .setView(iv)
                .setPositiveButton("Tutup", null)
                .show()
        }
    }

    /** Pratinjau semua bingkai yang akan ditawarkan ke tamu, berjajar ke samping. */
    private fun previewAll() {
        toast("Menyiapkan pratinjau...")
        lifecycleScope.launch {
            val strip = withContext(Dispatchers.Default) {
                val frames = Frames.available(prefs, textEvent)
                val photos = Collage.samplePhotos(prefs.shotCount)
                val L = prefs.layout
                val th = 600
                val tw = th * L.width / L.height
                val gap = 24
                val labelH = 56
                val out = android.graphics.Bitmap.createBitmap(frames.size * (tw + gap) + gap, th + labelH + gap * 2, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out)
                c.drawColor(0xFF221D2B.toInt())
                val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFF7F2EA.toInt(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER
                }
                frames.forEachIndexed { i, f ->
                    val spec = CollageSpec.fromPrefs(this@SettingsActivity, prefs, textEvent, f)
                    val full = Collage.render(spec, photos)
                    spec.recycle()
                    val x = gap + i * (tw + gap)
                    c.drawBitmap(full, null, android.graphics.Rect(x, gap, x + tw, gap + th), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                    full.recycle()
                    c.drawText(f.label, x + tw / 2f, gap + th + 38f, tp)
                }
                photos.forEach { it.recycle() }
                out
            }
            val iv = ImageView(this@SettingsActivity).apply {
                setImageBitmap(strip)
                adjustViewBounds = true
            }
            val hs = android.widget.HorizontalScrollView(this@SettingsActivity).apply {
                addView(iv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(420)))
            }
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Bingkai untuk acara ${textEvent.label}")
                .setView(hs)
                .setPositiveButton("Tutup", null)
                .show()
        }
    }

    private fun testCloud() {
        if (!prefs.cloudReady) {
            toast("Aktifkan unggah Google Drive dan isi URL https:// terlebih dahulu"); return
        }
        txtCloudStatus.text = "Mengunggah gambar uji..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(cacheDir, "uji_photobooth.jpg")
                    val photos = Collage.samplePhotos(1)
                    Storage.saveJpeg(photos[0], f, 80)
                    photos[0].recycle()
                    CloudUploader.upload(prefs.cloudUrl, prefs.cloudKey, f, "uji_photobooth.jpg", prefs.homeTitle.ifBlank { "Photobooth" })
                }
            }
            txtCloudStatus.text = result.fold(
                { "Berhasil ✓ $it" },
                { "Gagal: ${it.message}" }
            )
        }
    }

    private fun showFolder() {
        val root = Storage.sessionsRoot(this)
        val count = root.listFiles()?.count { it.isDirectory } ?: 0
        AlertDialog.Builder(this)
            .setTitle("Folder Foto")
            .setMessage("Jumlah sesi: $count\n\nLokasi:\n${root.absolutePath}\n\nKolase juga tersalin ke Galeri › Pictures/Photobooth (jika diaktifkan).")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- Pembangun UI ----------

    private fun lp(top: Int = 6) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
    }

    private fun heading(t: String) {
        col.addView(TextView(this).apply {
            text = t; textSize = 26f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        }, lp(0))
    }

    private fun section(t: String) {
        col.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(24) })
        if (t.isNotBlank()) col.addView(TextView(this).apply {
            text = t; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.accent))
        }, lp(14))
    }

    private fun note(t: String): TextView {
        val tv = TextView(this).apply { text = t; textSize = 13f; setTextColor(muted) }
        col.addView(tv, lp(4))
        return tv
    }

    private fun addLabel(t: String) {
        col.addView(TextView(this).apply { text = t; textSize = 14f; setTextColor(muted) }, lp(12))
    }

    private fun edit(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        addLabel(label)
        val et = EditText(this).apply {
            setText(value); inputType = type; setTextColor(fg); textSize = 17f
            setSingleLine(true)
        }
        col.addView(et, lp(0))
        return et
    }

    private fun spinner(label: String, items: List<String>, selected: Int): Spinner {
        addLabel(label)
        val sp = Spinner(this)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { (it as TextView).setTextColor(fg); it.textSize = 17f }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        sp.setSelection(selected.coerceIn(0, items.size - 1))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(sp, lp(2))
        return sp
    }

    private fun switch(label: String, checked: Boolean): SwitchCompat {
        val sw = SwitchCompat(this).apply {
            text = label; isChecked = checked; setTextColor(fg); textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
        }
        col.addView(sw, lp(8))
        return sw
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label
            setBackgroundResource(R.drawable.btn_primary)
            setTextColor(0xFF14111A.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener { onClick() }
        }
        col.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)).apply { topMargin = dp(10) })
        return b
    }

    /** Memindahkan beberapa tombol yang baru ditambahkan ke satu baris. */
    private fun row(vararg buttons: Button) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        buttons.forEachIndexed { i, b ->
            col.removeView(b)
            r.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)).apply {
                if (i > 0) marginStart = dp(12)
            })
        }
        col.addView(r, lp(10))
    }
}
