package id.photobooth.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** Live preview + hitung mundur + multishot otomatis. */
class BoothActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENT = "event"
    }

    private lateinit var previewView: PreviewView
    private lateinit var countdownText: TextView
    private lateinit var infoText: TextView
    private lateinit var flashView: View
    private lateinit var thumbView: ImageView
    private lateinit var controls: View
    private lateinit var filterRow: LinearLayout
    private lateinit var frameRow: LinearLayout
    private var selectedFrame = FrameStyle.POLOS
    private lateinit var event: EventType
    private var frameOptions: List<FrameStyle> = emptyList()

    private var imageCapture: ImageCapture? = null
    private var usingFront = true
    private var filter = PhotoFilter.NORMAL
    private var running = false
    private val shots = mutableListOf<Bitmap>()
    private lateinit var cameraExecutor: ExecutorService

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleTimeout = Runnable { if (!running) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booth)
        cameraExecutor = Executors.newSingleThreadExecutor()
        event = EventType.fromName(intent.getStringExtra(EXTRA_EVENT)) ?: prefs.eventType

        previewView = findViewById(R.id.previewView)
        countdownText = findViewById(R.id.countdownText)
        infoText = findViewById(R.id.infoText)
        flashView = findViewById(R.id.flashView)
        thumbView = findViewById(R.id.thumbView)
        controls = findViewById(R.id.controls)
        filterRow = findViewById(R.id.filterRow)
        frameRow = findViewById(R.id.frameRow)

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        buildFilterChips()
        buildFrameChips()
        infoText.text = "Atur posisi, pilih bingkai & filter, lalu tekan MULAI"
        findViewById<Button>(R.id.btnStart).setOnClickListener { startSession() }
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        startCamera()
        resetIdle()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdle()
    }

    private fun resetIdle() {
        idleHandler.removeCallbacks(idleTimeout)
        idleHandler.postDelayed(idleTimeout, 90_000L)
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(idleTimeout)
        cameraExecutor.shutdown()
        thumbView.setImageDrawable(null)
        shots.forEach { if (!it.isRecycled) it.recycle() }
        shots.clear()
        super.onDestroy()
    }

    // ---------- Bingkai ----------

    private fun buildFrameChips() {
        frameOptions = Frames.available(prefs, event)
        findViewById<TextView>(R.id.frameLabel).text = "${event.emoji} ${event.label} \u2014 pilih bingkai"
        selectedFrame = frameOptions.first()
        val show = prefs.guestChooseFrame && frameOptions.size > 1
        findViewById<View>(R.id.frameLabel).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.frameScroll).visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        val layout = prefs.layout
        val thumbH = dp(118)
        val thumbW = thumbH * layout.width / layout.height
        frameRow.removeAllViews()
        val images = mutableListOf<ImageView>()
        frameOptions.forEach { f ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.frame_chip)
                setPadding(dp(8), dp(8), dp(8), dp(6))
                tag = f
                setOnClickListener { selectFrame(f) }
            }
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            box.addView(iv, LinearLayout.LayoutParams(thumbW, thumbH))
            box.addView(TextView(this).apply {
                text = f.label
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text))
                gravity = android.view.Gravity.CENTER
                maxWidth = maxOf(thumbW, dp(96))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(10)
            frameRow.addView(box, lp)
            images += iv
        }
        selectFrame(selectedFrame)

        // Gambar pratinjau setiap bingkai di latar belakang.
        val tw = 120 * layout.width / layout.height * 3
        val th = 360
        lifecycleScope.launch {
            val samples = withContext(Dispatchers.Default) { Collage.samplePhotos(prefs.shotCount) }
            frameOptions.forEachIndexed { i, f ->
                val thumb = withContext(Dispatchers.Default) {
                    runCatching {
                        val spec = CollageSpec.fromPrefs(this@BoothActivity, prefs, event, f)
                        val full = Collage.render(spec, samples)
                        spec.recycle()
                        val t = Bitmap.createScaledBitmap(full, tw, th, true)
                        if (t !== full) full.recycle()
                        t
                    }.getOrNull()
                }
                if (thumb != null) images[i].setImageBitmap(thumb)
            }
            samples.forEach { it.recycle() }
        }
    }

    private fun selectFrame(f: FrameStyle) {
        selectedFrame = f
        for (i in 0 until frameRow.childCount) {
            val v = frameRow.getChildAt(i)
            v.isSelected = v.tag == f
        }
    }

    // ---------- Filter ----------

    private fun buildFilterChips() {
        filterRow.removeAllViews()
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        PhotoFilter.values().forEach { f ->
            val b = TextView(this).apply {
                text = f.label
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setBackgroundResource(R.drawable.chip)
                setPadding(dp(22), dp(12), dp(22), dp(12))
                isSelected = f == filter
                setOnClickListener { selectFilter(f) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(10)
            filterRow.addView(b, lp)
        }
        selectFilter(filter)
    }

    private fun selectFilter(f: PhotoFilter) {
        filter = f
        for (i in 0 until filterRow.childCount) {
            val v = filterRow.getChildAt(i) as TextView
            v.isSelected = v.text == f.label
            v.setTextColor(if (v.isSelected) 0xFF14111A.toInt() else ContextCompat.getColor(this, R.color.text))
        }
        val m = f.matrix()
        if (m == null) previewView.setLayerType(View.LAYER_TYPE_NONE, null)
        else previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
    }

    // ---------- Kamera ----------

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                @Suppress("DEPRECATION")
                val rotation = windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0

                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(selector)
                    .setTargetRotation(rotation)
                    .build()

                val wantFront = prefs.useFrontCamera
                val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
                usingFront = (wantFront && hasFront) || !hasBack
                val cam = if (usingFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(this, cam, preview, capture)
                imageCapture = capture
                selectFilter(filter)
            } catch (e: Exception) {
                infoText.text = "Kamera gagal dibuka: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        val ic = imageCapture
        if (ic == null) {
            cont.resumeWithException(IllegalStateException("Kamera belum siap"))
            return@suspendCancellableCoroutine
        }
        ic.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bmp = toUprightBitmap(image)
                    if (cont.isActive) cont.resume(bmp) else bmp.recycle()
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

    /** Putar sesuai sensor, cerminkan (jika kamera depan), dan perkecil ke maks 1600 px. */
    private fun toUprightBitmap(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val deg = image.imageInfo.rotationDegrees
        val s = min(1f, 1600f / max(raw.width, raw.height))
        val m = Matrix().apply {
            postRotate(deg.toFloat())
            if (usingFront && prefs.mirrorPhoto) postScale(-1f, 1f)
            postScale(s, s)
        }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    }

    // ---------- Alur sesi ----------

    private fun startSession() {
        if (running) return
        if (imageCapture == null) {
            Toast.makeText(this, "Kamera belum siap, tunggu sebentar", Toast.LENGTH_SHORT).show(); return
        }
        running = true
        controls.visibility = View.GONE
        idleHandler.removeCallbacks(idleTimeout)

        lifecycleScope.launch {
            try {
                val n = prefs.shotCount
                for (i in 1..n) {
                    infoText.text = "Foto $i dari $n"
                    countdown(prefs.countdownSec)
                    val bmp = capture()
                    flash()
                    shots += bmp
                    thumbView.setImageBitmap(bmp)
                    thumbView.visibility = View.VISIBLE
                    if (i < n) {
                        infoText.text = "Bagus! Bersiap untuk foto berikutnya..."
                        delay(1200)
                    }
                }
                countdownText.text = ""
                infoText.text = "Memproses foto..."
                val sessionId = withContext(Dispatchers.Default) { processSession() }
                startActivity(Intent(this@BoothActivity, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_SESSION, sessionId))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@BoothActivity, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_LONG).show()
                thumbView.setImageDrawable(null)
                shots.forEach { it.recycle() }
                shots.clear()
                thumbView.visibility = View.GONE
                countdownText.text = ""
                infoText.text = "Atur posisi, pilih bingkai & filter, lalu tekan MULAI"
                controls.visibility = View.VISIBLE
                running = false
                resetIdle()
            }
        }
    }

    private suspend fun countdown(sec: Int) {
        for (s in sec downTo 1) {
            countdownText.text = s.toString()
            countdownText.alpha = 1f
            countdownText.scaleX = 1.6f
            countdownText.scaleY = 1.6f
            countdownText.animate().scaleX(1f).scaleY(1f).alpha(0.35f).setDuration(900).start()
            delay(1000)
        }
        countdownText.animate().cancel()
        countdownText.alpha = 1f
        countdownText.scaleX = 1f
        countdownText.scaleY = 1f
        countdownText.text = ""
    }

    private fun flash() {
        flashView.animate().cancel()
        flashView.alpha = 0.9f
        flashView.animate().alpha(0f).setDuration(450).start()
    }

    /** Berjalan di thread latar: filter, simpan, susun kolase. */
    private fun processSession(): String {
        val id = Storage.newSessionId()
        val dir = Storage.sessionDir(this, id)
        val filtered = mutableListOf<Bitmap>()
        shots.forEachIndexed { i, b ->
            val f = filter.apply(b)
            Storage.saveJpeg(f, File(dir, "foto_${i + 1}.jpg"), 90)
            filtered += f
        }
        val spec = CollageSpec.fromPrefs(this, prefs, event, selectedFrame)
        val collage = Collage.render(spec, filtered)
        val collageFile = File(dir, Storage.COLLAGE)
        Storage.saveJpeg(collage, collageFile, 95)
        if (prefs.saveToGallery) runCatching { Storage.exportToGallery(this, collageFile, "photobooth_$id.jpg") }

        collage.recycle()
        spec.recycle()
        filtered.forEachIndexed { i, f -> if (f !== shots[i]) f.recycle() }
        return id
    }
}
