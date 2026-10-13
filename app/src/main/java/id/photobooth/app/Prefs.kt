package id.photobooth.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Semua pengaturan operator, disimpan di SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("booth_prefs", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    // --- Acara / watermark ---
    var eventTitle: String get() = str("title", "Nama & Nama"); set(v) = putStr("title", v)
    var eventSubtitle: String get() = str("subtitle", "The Wedding of"); set(v) = putStr("subtitle", v)
    var eventDate: String
        get() = str("date", SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date()))
        set(v) = putStr("date", v)
    var fontSerif: Boolean get() = bool("font_serif", true); set(v) = putBool("font_serif", v)
    var logoUri: String get() = str("logo", ""); set(v) = putStr("logo", v)
    var overlayUri: String get() = str("overlay", ""); set(v) = putStr("overlay", v)
    var frameColorIndex: Int get() = int("frame_color", 0); set(v) = putInt("frame_color", v)
    var textColorIndex: Int get() = int("text_color", 0); set(v) = putInt("text_color", v)

    // --- Jenis acara & bingkai ---
    var eventType: EventType
        get() = runCatching { EventType.valueOf(str("event_type", EventType.PERNIKAHAN.name)) }.getOrDefault(EventType.PERNIKAHAN)
        set(v) = putStr("event_type", v.name)
    var frameStyle: FrameStyle
        get() = runCatching { FrameStyle.valueOf(str("frame_style", FrameStyle.ELEGAN_EMAS.name)) }.getOrDefault(FrameStyle.ELEGAN_EMAS)
        set(v) = putStr("frame_style", v.name)
    var guestChooseFrame: Boolean get() = bool("guest_frame", true); set(v) = putBool("guest_frame", v)
    var showAllFrames: Boolean get() = bool("all_frames", false); set(v) = putBool("all_frames", v)

    // --- Tata letak ---
    var layout: LayoutType
        get() = runCatching { LayoutType.valueOf(str("layout", LayoutType.STRIP_2X6.name)) }.getOrDefault(LayoutType.STRIP_2X6)
        set(v) = putStr("layout", v.name)
    var shotCount: Int get() = int("shots", 4).coerceIn(1, 4); set(v) = putInt("shots", v)

    // --- Kamera & alur ---
    var countdownSec: Int get() = int("countdown", 3); set(v) = putInt("countdown", v)
    var useFrontCamera: Boolean get() = bool("front", true); set(v) = putBool("front", v)
    var mirrorPhoto: Boolean get() = bool("mirror", true); set(v) = putBool("mirror", v)
    var landscape: Boolean get() = bool("landscape", true); set(v) = putBool("landscape", v)
    var resultTimeoutSec: Int get() = int("result_timeout", 60); set(v) = putInt("result_timeout", v)

    // --- Cetak ---
    var printLimit: Int get() = int("print_limit", 2); set(v) = putInt("print_limit", v)

    // --- Berbagi ---
    var localShare: Boolean get() = bool("local_share", true); set(v) = putBool("local_share", v)
    var serverPort: Int get() = int("port", 8080); set(v) = putInt("port", v)
    var wifiSsid: String get() = str("wifi_ssid", ""); set(v) = putStr("wifi_ssid", v)
    var wifiPass: String get() = str("wifi_pass", ""); set(v) = putStr("wifi_pass", v)
    var cloudEnabled: Boolean get() = bool("cloud", false); set(v) = putBool("cloud", v)
    var cloudUrl: String get() = str("cloud_url", ""); set(v) = putStr("cloud_url", v)
    var cloudKey: String get() = str("cloud_key", ""); set(v) = putStr("cloud_key", v)
    var saveToGallery: Boolean get() = bool("gallery", true); set(v) = putBool("gallery", v)

    // --- Keamanan ---
    var pin: String get() = str("pin", "1234"); set(v) = putStr("pin", v)

    val cloudReady: Boolean get() = cloudEnabled && cloudUrl.startsWith("https://")
}

object Palette {
    val frameColors = listOf(
        "Putih" to 0xFFFFFFFF.toInt(),
        "Hitam" to 0xFF111111.toInt(),
        "Krem" to 0xFFF3EBDD.toInt(),
        "Merah Muda" to 0xFFF6DDE1.toInt(),
        "Biru Navy" to 0xFF1F2A44.toInt(),
        "Hijau Sage" to 0xFFC9D5C0.toInt(),
        "Emas" to 0xFFD9B46A.toInt(),
        "Marun" to 0xFF5C1A26.toInt()
    )
    val textColors = listOf(
        "Hitam" to 0xFF1A1A1A.toInt(),
        "Putih" to 0xFFFFFFFF.toInt(),
        "Emas" to 0xFFB8892F.toInt(),
        "Abu Tua" to 0xFF4A4A4A.toInt(),
        "Navy" to 0xFF1F2A44.toInt(),
        "Marun" to 0xFF6B1F2C.toInt()
    )

    fun frame(i: Int) = frameColors[i.coerceIn(0, frameColors.size - 1)].second
    fun text(i: Int) = textColors[i.coerceIn(0, textColors.size - 1)].second
}
