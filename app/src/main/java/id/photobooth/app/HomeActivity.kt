package id.photobooth.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Layar awal: menu pilihan acara (Pernikahan, Wisuda, CFD, dll).
 * Tekan lama pojok kanan atas = menu operator.
 */
class HomeActivity : BaseActivity() {

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(this, "Izin kamera diperlukan agar photobooth bisa berjalan", Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        findViewById<View>(R.id.settingsHotspot).setOnLongClickListener {
            askPin(); true
        }
        if (!hasCamera()) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        buildScreen()
        CloudSync.syncAll(this)
    }

    private fun buildScreen() {
        val p = prefs
        val title = findViewById<TextView>(R.id.txtTitle)
        val subtitle = findViewById<TextView>(R.id.txtSubtitle)
        val hint = findViewById<TextView>(R.id.txtHint)
        val grid = findViewById<LinearLayout>(R.id.eventGrid)
        val scroll = findViewById<View>(R.id.menuScroll)
        val btnStart = findViewById<Button>(R.id.btnStart)

        val events = EventType.values().filter { it in p.enabledEvents }
        title.text = p.homeTitle.ifBlank { "Photobooth" }
        subtitle.text = "SELAMAT DATANG"

        // Mode satu acara: menu dimatikan, atau hanya satu acara yang aktif.
        if (!p.eventMenu || events.size <= 1) {
            val ev = if (!p.eventMenu || events.isEmpty()) p.eventType else events.first()
            subtitle.text = p.subtitleFor(ev).uppercase()
            title.text = p.titleFor(ev).ifBlank { p.homeTitle }
            hint.text = p.eventDate
            scroll.visibility = View.GONE
            btnStart.visibility = View.VISIBLE
            btnStart.setOnClickListener { startBooth(ev) }
            return
        }

        hint.text = "Pilih jenis acara untuk mulai berfoto"
        scroll.visibility = View.VISIBLE
        btnStart.visibility = View.GONE
        grid.removeAllViews()

        val columns = if (p.landscape) 4 else 2
        events.chunked(columns).forEach { rowEvents ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowEvents.forEach { ev -> row.addView(tile(ev), LinearLayout.LayoutParams(0, dp(170), 1f).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }) }
            // Isi sisa kolom agar ukuran kotak tetap sama.
            repeat(columns - rowEvents.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(170), 1f).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun tile(ev: EventType): View {
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ev.color, darken(ev.color))
        ).apply { cornerRadius = dp(22).toFloat() }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                    scaleX = 1f; scaleY = 1f
                    startBooth(ev)
                }.start()
            }
        }
        box.addView(TextView(this).apply {
            text = ev.emoji
            textSize = 46f
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = ev.label
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        val n = Frames.available(prefs, ev).size
        box.addView(TextView(this).apply {
            text = "$n pilihan bingkai"
            textSize = 14f
            setTextColor(0xDDFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        return box
    }

    private fun darken(c: Int): Int {
        val f = 0.7f
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * f).toInt(),
            (android.graphics.Color.green(c) * f).toInt(),
            (android.graphics.Color.blue(c) * f).toInt()
        )
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startBooth(ev: EventType) {
        if (!hasCamera()) {
            cameraPermission.launch(Manifest.permission.CAMERA); return
        }
        startActivity(Intent(this, BoothActivity::class.java).putExtra(BoothActivity.EXTRA_EVENT, ev.name))
    }

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN operator"
        }
        AlertDialog.Builder(this)
            .setTitle("Menu Operator")
            .setView(input)
            .setPositiveButton("Masuk") { _, _ ->
                if (input.text.toString() == prefs.pin) startActivity(Intent(this, SettingsActivity::class.java))
                else Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
