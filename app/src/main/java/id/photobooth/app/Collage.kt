package id.photobooth.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min

/**
 * Format cetak pada 300 DPI.
 * STRIP_2X6  : 600 x 1800 px  (2 x 6 inci) — dicetak 2 strip berdampingan di kertas 4x6.
 * GRID_4X6   : 1200 x 1800 px (4 x 6 inci, potret).
 */
enum class LayoutType(val label: String, val width: Int, val height: Int) {
    STRIP_2X6("Strip vertikal 2x6 inci", 600, 1800),
    GRID_4X6("Kartu 4x6 inci", 1200, 1800)
}

class CollageSpec(
    val layout: LayoutType,
    val frameColor: Int,
    val textColor: Int,
    val title: String,
    val subtitle: String,
    val date: String,
    val serif: Boolean,
    val logo: Bitmap?,
    val overlay: Bitmap?,
    val frame: FrameStyle = FrameStyle.POLOS
) {
    fun recycle() {
        logo?.recycle(); overlay?.recycle()
    }

    companion object {
        fun fromPrefs(ctx: Context, p: Prefs, event: EventType, frame: FrameStyle) = CollageSpec(
            layout = p.layout,
            frameColor = Palette.frame(p.frameColorIndex),
            textColor = Palette.text(p.textColorIndex),
            title = p.titleFor(event),
            subtitle = p.subtitleFor(event),
            date = p.eventDate,
            serif = p.fontSerif,
            logo = Storage.loadUriBitmap(ctx, p.logoUri, 900),
            overlay = if (frame == FrameStyle.CUSTOM) Storage.loadUriBitmap(ctx, p.overlayUri, 1800) else null,
            frame = frame
        )
    }
}

object Collage {

    private class Geometry(val slots: List<RectF>, val footer: RectF)

    private fun geometry(layout: LayoutType, n: Int, extra: Float): Geometry {
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()
        return when (layout) {
            LayoutType.STRIP_2X6 -> {
                val m = 36f; val gap = 20f; val footerH = 330f
                val top = m; val bottom = h - footerH
                val slotH = (bottom - top - gap * (n - 1)) / n
                val slots = (0 until n).map { i ->
                    val y = top + i * (slotH + gap)
                    RectF(m + extra, y, w - m - extra, y + slotH)
                }
                Geometry(slots, RectF(m, bottom + 10f, w - m, h - m / 2))
            }
            LayoutType.GRID_4X6 -> {
                val m = 50f; val gap = 30f; val footerH = 360f
                val area = RectF(m + extra, m, w - m - extra, h - footerH)
                val cw = (area.width() - gap) / 2f
                val ch = (area.height() - gap) / 2f
                val slots = when (n) {
                    1 -> listOf(RectF(area))
                    2 -> listOf(
                        RectF(area.left, area.top, area.right, area.top + ch),
                        RectF(area.left, area.top + ch + gap, area.right, area.bottom)
                    )
                    3 -> listOf(
                        RectF(area.left, area.top, area.right, area.top + ch),
                        RectF(area.left, area.top + ch + gap, area.left + cw, area.bottom),
                        RectF(area.left + cw + gap, area.top + ch + gap, area.right, area.bottom)
                    )
                    else -> listOf(
                        RectF(area.left, area.top, area.left + cw, area.top + ch),
                        RectF(area.left + cw + gap, area.top, area.right, area.top + ch),
                        RectF(area.left, area.top + ch + gap, area.left + cw, area.bottom),
                        RectF(area.left + cw + gap, area.top + ch + gap, area.right, area.bottom)
                    )
                }
                Geometry(slots, RectF(m, h - footerH + 10f, w - m, h - m / 2))
            }
        }
    }

    /** Menggabungkan foto ke dalam satu bingkai sesuai tata letak. */
    fun render(spec: CollageSpec, photos: List<Bitmap>): Bitmap {
        val L = spec.layout
        val out = Bitmap.createBitmap(L.width, L.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val st = spec.frame
        c.drawColor(st.bg ?: spec.frameColor)

        val w = L.width.toFloat()
        val h = L.height.toFloat()
        val k = w / 600f
        val geo = geometry(L, photos.size.coerceIn(1, 4), st.extraSide(L))
        Frames.background(st, c, w, h, geo.slots, k)
        val radius = when (st) {
            FrameStyle.POLAROID, FrameStyle.FILM, FrameStyle.KORPORAT -> 2f
            else -> if (L == LayoutType.STRIP_2X6) 10f else 16f
        }
        geo.slots.forEachIndexed { i, r ->
            val photo = photos.getOrNull(i) ?: return@forEachIndexed
            drawCropped(c, photo, r, radius)
        }
        Frames.foreground(st, c, w, h, geo.slots, geo.footer, k)

        drawFooter(c, spec, geo.footer)

        if (st == FrameStyle.CUSTOM) spec.overlay?.let { ov ->
            c.drawBitmap(ov, null, Rect(0, 0, L.width, L.height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /** Pemotongan otomatis: crop tengah agar rasio pas dengan slot (tidak gepeng). */
    fun drawCropped(c: Canvas, src: Bitmap, dst: RectF, radius: Float) {
        val dstRatio = dst.width() / dst.height()
        val srcRatio = src.width.toFloat() / src.height
        val crop = if (srcRatio > dstRatio) {
            val cw = (src.height * dstRatio).toInt()
            val x = (src.width - cw) / 2
            Rect(x, 0, x + cw, src.height)
        } else {
            val chh = (src.width / dstRatio).toInt()
            val y = (src.height - chh) / 2
            Rect(0, y, src.width, y + chh)
        }
        c.save()
        val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
        c.clipPath(path)
        c.drawBitmap(src, crop, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
    }

    private fun drawFooter(c: Canvas, spec: CollageSpec, area: RectF) {
        val scale = if (spec.layout == LayoutType.STRIP_2X6) 1f else 1.45f
        val st = spec.frame
        val family = if (st.serif ?: spec.serif) Typeface.SERIF else Typeface.SANS_SERIF
        val textColor = st.text ?: spec.textColor

        val lines = mutableListOf<Pair<String, Paint>>()
        fun paint(size: Float, style: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.alpha = alpha
            textSize = size * scale
            typeface = Typeface.create(family, style)
            textAlign = Paint.Align.CENTER
        }
        if (spec.subtitle.isNotBlank()) lines += spec.subtitle to paint(30f, Typeface.ITALIC, 220)
        if (spec.title.isNotBlank()) lines += spec.title to paint(54f, Typeface.BOLD)
        if (spec.date.isNotBlank()) lines += spec.date to paint(26f, Typeface.NORMAL, 210).apply { letterSpacing = 0.12f }

        val maxW = area.width() * if (st.sideIcons) 0.64f else 0.94f
        lines.forEach { (text, p) ->
            while (p.measureText(text) > maxW && p.textSize > 12f) p.textSize *= 0.93f
        }

        // Logo
        var logoH = 0f
        val logo = spec.logo
        if (logo != null) {
            logoH = area.height() * if (lines.isEmpty()) 0.8f else 0.36f
        }
        val gap = 10f * scale
        val textH = lines.sumOf { (it.second.fontSpacing * 1.05f).toDouble() }.toFloat()
        val total = logoH + (if (logoH > 0 && lines.isNotEmpty()) gap * 1.5f else 0f) + textH
        var y = area.top + (area.height() - total) / 2f

        if (logo != null && logoH > 0f) {
            val lw = min(maxW, logoH * logo.width / logo.height)
            val lh = lw * logo.height / logo.width
            val left = area.centerX() - lw / 2f
            c.drawBitmap(logo, null, RectF(left, y + (logoH - lh) / 2f, left + lw, y + (logoH + lh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
            y += logoH + if (lines.isNotEmpty()) gap * 1.5f else 0f
        }
        lines.forEach { (text, p) ->
            val lineH = p.fontSpacing * 1.05f
            val baseline = y + (lineH - (p.descent() + p.ascent())) / 2f
            c.drawText(text, area.centerX(), baseline, p)
            y += lineH
        }
    }

    /**
     * Lembar siap cetak 4x6 (1200x1800). Strip 2x6 digandakan berdampingan
     * dengan garis potong di tengah, sehingga rasio selalu pas dengan kertas.
     */
    fun printSheet(collage: Bitmap, layout: LayoutType): Bitmap {
        if (layout == LayoutType.GRID_4X6) return collage
        val sheet = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(Color.WHITE)
        c.drawBitmap(collage, null, Rect(0, 0, 600, 1800), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawBitmap(collage, null, Rect(600, 0, 1200, 1800), Paint(Paint.FILTER_BITMAP_FLAG))
        val cut = Paint().apply {
            color = Color.argb(110, 128, 128, 128); strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        c.drawLine(600f, 0f, 600f, 24f, cut)
        c.drawLine(600f, 1776f, 600f, 1800f, cut)
        return sheet
    }

    /** Foto contoh untuk pratinjau template di pengaturan. */
    fun samplePhotos(n: Int): List<Bitmap> = (1..n).map { i ->
        val b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val hue = floatArrayOf((i * 67f) % 360f, 0.35f, 0.75f)
        val hue2 = floatArrayOf((i * 67f + 40f) % 360f, 0.45f, 0.45f)
        c.drawRect(0f, 0f, 800f, 600f, Paint().apply {
            shader = LinearGradient(0f, 0f, 800f, 600f, Color.HSVToColor(hue), Color.HSVToColor(hue2), Shader.TileMode.CLAMP)
        })
        c.drawText("Foto $i", 400f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 80f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        b
    }
}
