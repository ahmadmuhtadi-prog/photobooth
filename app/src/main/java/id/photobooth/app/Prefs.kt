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

    // --- Layar awal & teks per acara ---
    var homeTitle: String get() = str("home_title", "Photobooth"); set(v) = putStr("home_title", v)
    fun titleFor(ev: EventType): String = str("title_" + ev.name, ev.titleDefault)
    fun subtitleFor(ev: EventType): String = str("subtitle_" + ev.name, ev.subtitle)
    /** Nama acara yang tampil di menu layar awal (bisa diganti operator). */
    fun labelFor(ev: EventType): String = str("label_" + ev.name, ev.label).ifBlank { ev.label }
    fun setTextFor(ev: EventType, label: String, title: String, subtitle: String) {
        sp.edit()
            .putString("label_" + ev.name, label)
            .putString("title_" + ev.name, title)
            .putString("subtitle_" + ev.name, subtitle)
            .apply()
    }

    var eventDate: String
        get() = str("date", SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(Date()))
        set(v) = putStr("date", v)
    var fontSerif: Boolean get() = bool("font_serif", true); set(v) = putBool("font_serif", v)
    var logoUri: String get() = str("logo", ""); set(v) = putStr("logo", v)
    var overlayUri: String get() = str("overlay", ""); set(v) = putStr("overlay", v)
    var frameColorIndex: Int get() = int("frame_color", 0); set(v) = putInt("frame_color", v)
    var textColorIndex: Int get() = int("text_color", 0); set(v) = putInt("text_color", v)

    // --- Menu acara & bingkai ---
    /** Tampilkan menu pilihan acara di layar awal. */
    var eventMenu: Boolean get() = bool("event_menu", true); set(v) = putBool("event_menu", v)
    /** Acara yang dipakai langsung jika menu dimatikan. */
    var eventType: EventType
        get() = EventType.fromName(str("event_type", EventType.PERNIKAHAN.name)) ?: EventType.PERNIKAHAN
        set(v) = putStr("event_type", v.name)
    var enabledEvents: Set<EventType>
        get() {
            val names = sp.getStringSet("enabled_events", null) ?: return EventType.values().toSet()
            return EventType.values().filter { it.name in names }.toSet()
        }
        set(v) = sp.edit().putStringSet("enabled_events", v.map { it.name }.toSet()).apply()
    var guestChooseFrame: Boolean get() = bool("guest_frame", true); set(v) = putBool("guest_frame", v)
    var showAllFrames: Boolean get() = bool("all_frames", false); set(v) = putBool("all_frames", v)

    /** Tampilkan formulir isi tulisan (nama, dll) setiap tamu masuk ke layar foto. */
    var askText: Boolean get() = bool("ask_text", true); set(v) = putBool("ask_text", v)

    /** Format strip: foto 2 kali agar 2 strip di lembar cetak berbeda. */
    var twoStrips: Boolean get() = bool("two_strips", true); set(v) = putBool("two_strips", v)

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
