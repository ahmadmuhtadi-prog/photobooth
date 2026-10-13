package id.photobooth.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/** Layar sambutan (attract screen). Tekan lama pojok kanan atas = menu operator. */
class HomeActivity : BaseActivity() {

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(this, "Izin kamera diperlukan agar photobooth bisa berjalan", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.btnStart).setOnClickListener { startBooth() }
        findViewById<android.view.View>(R.id.root).setOnClickListener { startBooth() }
        findViewById<android.view.View>(R.id.settingsHotspot).setOnLongClickListener {
            askPin(); true
        }
        if (!hasCamera()) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.txtTitle).text = prefs.eventTitle.ifBlank { "Photobooth" }
        findViewById<TextView>(R.id.txtSubtitle).text = prefs.eventSubtitle.ifBlank { "SELAMAT DATANG" }.uppercase()
        findViewById<TextView>(R.id.txtDate).text = prefs.eventDate
        CloudSync.syncAll(this)
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startBooth() {
        if (!hasCamera()) {
            cameraPermission.launch(Manifest.permission.CAMERA); return
        }
        startActivity(Intent(this, BoothActivity::class.java))
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
