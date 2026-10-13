package id.photobooth.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Desain bingkai bawaan. Semua digambar langsung oleh kode (tanpa file gambar),
 * sehingga tajam di ukuran cetak apa pun.
 * bg/text = null berarti memakai warna dari menu operator.
 */
enum class FrameStyle(
    val label: String,
    val bg: Int?,
    val text: Int?,
    val serif: Boolean?,
    val sideIcons: Boolean = false
) {
    POLOS("Polos", null, null, null),
    ELEGAN_EMAS("Elegan Emas", 0xFFFBF6EC.toInt(), 0xFF8A6A2F.toInt(), true),
    BUNGA("Bunga", 0xFFF8E8EA.toInt(), 0xFF7A3B4A.toInt(), true, true),
    ROMANTIS("Romantis Hati", 0xFFFFF0F3.toInt(), 0xFF9D2F4C.toInt(), true, true),
    PESTA("Pesta Konfeti", 0xFF1E1B3A.toInt(), 0xFFFFFFFF.toInt(), false),
    BALON("Balon Ceria", 0xFFFFF4D6.toInt(), 0xFF2B2D42.toInt(), false, true),
    WISUDA("Wisuda Navy", 0xFF14213D.toInt(), 0xFFE9C46A.toInt(), true, true),
    WISUDA_MARUN("Wisuda Marun", 0xFF5C1A26.toInt(), 0xFFE9C46A.toInt(), true, true),
    SPORTY("Sporty CFD", 0xFFFFFFFF.toInt(), 0xFF0B2545.toInt(), false),
    ALAM("Hijau Segar", 0xFFEAF4E4.toInt(), 0xFF2F5D34.toInt(), false, true),
    POLAROID("Polaroid", 0xFFF1EEE8.toInt(), 0xFF333333.toInt(), true),
    FILM("Film Klasik", 0xFF111111.toInt(), 0xFFF2F2F2.toInt(), false),
    RETRO("Retro 70-an", 0xFFF4E3C1.toInt(), 0xFF3D2C29.toInt(), false),
    ISLAMI("Lebaran / Islami", 0xFF0F5132.toInt(), 0xFFF2D27A.toInt(), true, true),
    KORPORAT("Korporat Modern", 0xFFFFFFFF.toInt(), 0xFF1F2A44.toInt(), false),
    CUSTOM("Bingkai Saya (PNG)", null, null, null);

    /** Tambahan margin kiri-kanan (untuk lubang film). */
    fun extraSide(layout: LayoutType): Float =
        if (this == FILM) (if (layout == LayoutType.STRIP_2X6) 34f else 50f) else 0f
}

/** Jenis acara yang tampil di menu layar awal, masing-masing dengan bingkai sesuai tema. */
enum class EventType(
    val label: String,
    val emoji: String,
    val color: Int,
    val subtitle: String,
    val titleDefault: String,
    val frames: List<FrameStyle>
) {
    PERNIKAHAN(
        "Pernikahan", "\uD83D\uDC8D", 0xFFB8892F.toInt(), "The Wedding of", "Nama & Nama",
        listOf(FrameStyle.ELEGAN_EMAS, FrameStyle.BUNGA, FrameStyle.ROMANTIS, FrameStyle.POLAROID, FrameStyle.POLOS)
    ),
    WISUDA(
        "Wisuda", "\uD83C\uDF93", 0xFF2A4A8A.toInt(), "Selamat Wisuda", "Wisuda 2026",
        listOf(FrameStyle.WISUDA, FrameStyle.WISUDA_MARUN, FrameStyle.ELEGAN_EMAS, FrameStyle.POLAROID, FrameStyle.FILM)
    ),
    CFD(
        "Car Free Day", "\uD83D\uDEB4", 0xFF00A6A6.toInt(), "Car Free Day", "CFD Minggu Pagi",
        listOf(FrameStyle.SPORTY, FrameStyle.ALAM, FrameStyle.RETRO, FrameStyle.POLAROID)
    ),
    ULANG_TAHUN(
        "Ulang Tahun", "\uD83C\uDF82", 0xFFEF476F.toInt(), "Happy Birthday", "Nama",
        listOf(FrameStyle.BALON, FrameStyle.PESTA, FrameStyle.POLAROID, FrameStyle.RETRO)
    ),
    KORPORAT(
        "Acara Kantor", "\uD83D\uDCBC", 0xFF2563EB.toInt(), "Annual Gathering", "Nama Perusahaan",
        listOf(FrameStyle.KORPORAT, FrameStyle.FILM, FrameStyle.POLAROID, FrameStyle.POLOS)
    ),
    LEBARAN(
        "Halal Bihalal", "\uD83C\uDF19", 0xFF2D8A57.toInt(), "Halal Bihalal", "Keluarga Besar",
        listOf(FrameStyle.ISLAMI, FrameStyle.ELEGAN_EMAS, FrameStyle.POLAROID, FrameStyle.POLOS)
    ),
    SEKOLAH(
        "Perpisahan Sekolah", "\uD83C\uDFEB", 0xFFE76F51.toInt(), "Kenangan Kita", "Nama Sekolah",
        listOf(FrameStyle.RETRO, FrameStyle.PESTA, FrameStyle.POLAROID, FrameStyle.FILM)
    ),
    PESTA(
        "Pesta & Festival", "\uD83C\uDF89", 0xFF7B2CBF.toInt(), "Let's Party", "Nama Acara",
        listOf(FrameStyle.PESTA, FrameStyle.FILM, FrameStyle.RETRO, FrameStyle.BALON)
    ),
    UMUM(
        "Lainnya", "\u2728", 0xFF6C757D.toInt(), "Photobooth", "Nama Acara",
        FrameStyle.values().filter { it != FrameStyle.CUSTOM }
    );

    companion object {
        fun fromName(n: String?): EventType? = values().firstOrNull { it.name == n }
    }
}

object Frames {

    /** Daftar bingkai untuk satu acara (ditambah Bingkai Saya jika ada PNG). */
    fun available(p: Prefs, ev: EventType): List<FrameStyle> {
        val hasPng = p.overlayUri.isNotBlank()
        val list = (if (p.showAllFrames) FrameStyle.values().toList() else ev.frames).toMutableList()
        if (hasPng && FrameStyle.CUSTOM !in list) list += FrameStyle.CUSTOM
        return list.filter { it != FrameStyle.CUSTOM || hasPng }.ifEmpty { listOf(FrameStyle.POLOS) }
    }

    private fun fill(color: Int, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; this.alpha = alpha; style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; this.alpha = alpha; style = Paint.Style.STROKE; strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }

    /** Digambar sebelum foto (latar & bayangan). */
    fun background(st: FrameStyle, c: Canvas, w: Float, h: Float, slots: List<RectF>, k: Float) {
        when (st) {
            FrameStyle.PESTA -> confetti(c, w, h, k, if (w > 700) 260 else 150, 7)
            FrameStyle.BALON -> confetti(c, w, h, k, if (w > 700) 90 else 55, 21, alpha = 120)
            FrameStyle.WISUDA, FrameStyle.WISUDA_MARUN -> sparkles(c, w, h, k, 0xFFE9C46A.toInt(), 11)
            FrameStyle.ROMANTIS -> {
                val rnd = Random(5)
                repeat(if (w > 700) 50 else 30) {
                    heart(c, rnd.nextFloat() * w, rnd.nextFloat() * h, (5f + rnd.nextFloat() * 7f) * k, fill(0xFFF4A6B7.toInt(), 120))
                }
            }
            FrameStyle.ISLAMI -> diamondPattern(c, w, h, k)
            FrameStyle.POLAROID -> {
                val p = fill(Color.WHITE).apply { setShadowLayer(12f * k, 0f, 4f * k, 0x40000000) }
                slots.forEach { r -> c.drawRect(r.left - 10f * k, r.top - 10f * k, r.right + 10f * k, r.bottom + 10f * k, p) }
            }
            else -> {}
        }
    }

    /** Digambar setelah foto, sebelum teks. */
    fun foreground(st: FrameStyle, c: Canvas, w: Float, h: Float, slots: List<RectF>, footer: RectF, k: Float) {
        when (st) {
            FrameStyle.ELEGAN_EMAS -> {
                val gold = 0xFFC9A55A.toInt()
                doubleBorder(c, w, h, k, gold)
                divider(c, footer, k, gold)
            }
            FrameStyle.BUNGA -> {
                flowerCluster(c, 14f * k, 14f * k, k, 1f)
                flowerCluster(c, w - 14f * k, h - 18f * k, k, -1f)
                flower(c, w - 20f * k, 22f * k, 14f * k, 0xFFF9C9A8.toInt())
                flower(c, 22f * k, h - 26f * k, 14f * k, 0xFFF9C9A8.toInt())
                divider(c, footer, k, 0xFFD98A9B.toInt())
            }
            FrameStyle.ROMANTIS -> {
                val rose = 0xFFD9577A.toInt()
                c.drawRect(14f * k, 14f * k, w - 14f * k, h - 14f * k, stroke(rose, 2f * k))
                heart(c, footer.left + 32f * k, footer.centerY(), 22f * k, fill(rose))
                heart(c, footer.right - 32f * k, footer.centerY(), 22f * k, fill(rose))
                heart(c, footer.left + 52f * k, footer.centerY() - 22f * k, 10f * k, fill(0xFFF4A6B7.toInt()))
                heart(c, footer.right - 52f * k, footer.centerY() - 22f * k, 10f * k, fill(0xFFF4A6B7.toInt()))
            }
            FrameStyle.SPORTY -> {
                val cols = intArrayOf(0xFFFF6B35.toInt(), 0xFF00A6A6.toInt(), 0xFF0B2545.toInt())
                cols.forEachIndexed { i, col ->
                    val a = (70f + i * 24f) * k
                    val b = a + 13f * k
                    c.drawPath(Path().apply { moveTo(0f, a); lineTo(a, 0f); lineTo(b, 0f); lineTo(0f, b); close() }, fill(col))
                    c.drawPath(Path().apply { moveTo(w, h - a); lineTo(w - a, h); lineTo(w - b, h); lineTo(w, h - b); close() }, fill(col))
                }
                // Garis finis kotak-kotak di atas teks
                val sq = 9f * k
                var x = footer.left
                var i = 0
                while (x < footer.right) {
                    val right = minOf(x + sq, footer.right)
                    c.drawRect(x, footer.top, right, footer.top + sq, fill(if (i % 2 == 0) 0xFF0B2545.toInt() else Color.WHITE))
                    c.drawRect(x, footer.top + sq, right, footer.top + 2 * sq, fill(if (i % 2 == 0) Color.WHITE else 0xFF0B2545.toInt()))
                    x += sq; i++
                }
            }
            FrameStyle.ALAM -> {
                sun(c, footer.left + 32f * k, footer.centerY(), 16f * k)
                leaf(c, footer.right - 30f * k, footer.centerY() - 14f * k, 34f * k, -35f)
                leaf(c, footer.right - 40f * k, footer.centerY() + 12f * k, 30f * k, 25f)
                leaf(c, footer.right - 18f * k, footer.centerY() + 6f * k, 26f * k, 70f)
                leaf(c, 22f * k, 16f * k, 40f * k, 35f)
                leaf(c, 14f * k, 34f * k, 30f * k, 75f)
                leaf(c, w - 22f * k, 16f * k, 40f * k, -35f)
                leaf(c, w - 14f * k, 34f * k, 30f * k, -75f)
            }
            FrameStyle.PESTA -> {
                val panel = RectF(footer.left + 6f * k, footer.top + 6f * k, footer.right - 6f * k, footer.bottom - 6f * k)
                c.drawRoundRect(panel, 18f * k, 18f * k, fill(Color.BLACK, 110))
            }
            FrameStyle.BALON -> {
                balloon(c, footer.left + 26f * k, footer.centerY() - 10f * k, k, 0xFFEF476F.toInt())
                balloon(c, footer.left + 50f * k, footer.centerY() + 12f * k, k * 0.8f, 0xFF118AB2.toInt())
                balloon(c, footer.right - 26f * k, footer.centerY() - 10f * k, k, 0xFFFFD166.toInt())
                balloon(c, footer.right - 50f * k, footer.centerY() + 12f * k, k * 0.8f, 0xFF06D6A0.toInt())
            }
            FrameStyle.WISUDA, FrameStyle.WISUDA_MARUN -> {
                val gold = 0xFFE9C46A.toInt()
                c.drawRect(12f * k, 12f * k, w - 12f * k, h - 12f * k, stroke(gold, 2.5f * k))
                gradCap(c, footer.left + 34f * k, footer.centerY(), 26f * k, gold)
                gradCap(c, footer.right - 34f * k, footer.centerY(), 26f * k, gold)
            }
            FrameStyle.FILM -> {
                val side = slots.minOfOrNull { it.left } ?: (36f * k)
                sprockets(c, side / 2f, h, k)
                sprockets(c, w - side / 2f, h, k)
            }
            FrameStyle.RETRO -> {
                val colors = intArrayOf(0xFFE76F51.toInt(), 0xFFF4A261.toInt(), 0xFFE9C46A.toInt(), 0xFF2A9D8F.toInt())
                val sh = 5f * k
                colors.forEachIndexed { i, col ->
                    c.drawRect(0f, 4f * k + i * sh, w, 4f * k + (i + 1) * sh, fill(col))
                    c.drawRect(0f, h - 4f * k - (4 - i) * sh, w, h - 4f * k - (3 - i) * sh, fill(col))
                }
            }
            FrameStyle.ISLAMI -> {
                val gold = 0xFFF2D27A.toInt()
                doubleBorder(c, w, h, k, gold)
                crescent(c, footer.left + 34f * k, footer.centerY(), 22f * k, gold, 0xFF0F5132.toInt())
                ketupat(c, footer.right - 34f * k, footer.centerY(), 24f * k, gold)
            }
            FrameStyle.KORPORAT -> {
                val accent = 0xFF2563EB.toInt()
                c.drawRect(0f, 0f, w, 10f * k, fill(accent))
                c.drawRect(0f, h - 16f * k, w, h, fill(accent))
                val tri = Path().apply {
                    moveTo(w, h - 16f * k - 70f * k); lineTo(w, h - 16f * k); lineTo(w - 70f * k, h - 16f * k); close()
                }
                c.drawPath(tri, fill(accent, 110))
                c.drawRect(footer.left, footer.top + 4f * k, footer.left + 70f * k, footer.top + 8f * k, fill(accent))
            }
            else -> {}
        }
    }

    // ---------- Elemen dekorasi ----------

    private fun doubleBorder(c: Canvas, w: Float, h: Float, k: Float, color: Int) {
        c.drawRect(10f * k, 10f * k, w - 10f * k, h - 10f * k, stroke(color, 3f * k))
        c.drawRect(18f * k, 18f * k, w - 18f * k, h - 18f * k, stroke(color, 1.2f * k))
        val d = 7f * k
        listOf(18f * k to 18f * k, w - 18f * k to 18f * k, 18f * k to h - 18f * k, w - 18f * k to h - 18f * k).forEach { (x, y) ->
            diamond(c, x, y, d, fill(color))
        }
    }

    private fun divider(c: Canvas, footer: RectF, k: Float, color: Int) {
        val y = footer.top + 6f * k
        val half = footer.width() * 0.22f
        c.drawLine(footer.centerX() - half, y, footer.centerX() - 10f * k, y, stroke(color, 1.5f * k))
        c.drawLine(footer.centerX() + 10f * k, y, footer.centerX() + half, y, stroke(color, 1.5f * k))
        diamond(c, footer.centerX(), y, 5f * k, fill(color))
    }

    private fun diamond(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
        c.drawPath(path, p)
    }

    private fun confetti(c: Canvas, w: Float, h: Float, k: Float, count: Int, seed: Int, alpha: Int = 255) {
        val colors = intArrayOf(0xFFFF5D8F.toInt(), 0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF4CC9F0.toInt(), 0xFFF72585.toInt(), 0xFFFFFFFF.toInt())
        val rnd = Random(seed)
        repeat(count) {
            val p = fill(colors[rnd.nextInt(colors.size)], alpha)
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            if (rnd.nextBoolean()) {
                c.drawCircle(x, y, (3f + rnd.nextFloat() * 5f) * k, p)
            } else {
                c.save()
                c.rotate(rnd.nextFloat() * 180f, x, y)
                c.drawRect(x - 3f * k, y - 7f * k, x + 3f * k, y + 7f * k, p)
                c.restore()
            }
        }
    }

    private fun sparkles(c: Canvas, w: Float, h: Float, k: Float, color: Int, seed: Int) {
        val rnd = Random(seed)
        repeat(if (w > 700) 70 else 40) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = (3f + rnd.nextFloat() * 6f) * k
            star4(c, x, y, r, fill(color, 90 + rnd.nextInt(120)))
        }
    }

    private fun star4(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val s = r * 0.28f
        val path = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + s, cy - s); lineTo(cx + r, cy); lineTo(cx + s, cy + s)
            lineTo(cx, cy + r); lineTo(cx - s, cy + s); lineTo(cx - r, cy); lineTo(cx - s, cy - s); close()
        }
        c.drawPath(path, p)
    }

    private fun diamondPattern(c: Canvas, w: Float, h: Float, k: Float) {
        val p = fill(0xFFF2D27A.toInt(), 38)
        val step = 56f * k
        var row = 0
        var y = 0f
        while (y < h + step) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < w + step) {
                diamond(c, x, y, 7f * k, p)
                x += step
            }
            y += step / 2f
            row++
        }
    }

    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float, petal: Int) {
        val pp = fill(petal)
        for (i in 0 until 5) {
            val a = Math.toRadians((i * 72.0) - 90.0)
            c.drawCircle(cx + (cos(a) * r * 0.55f).toFloat(), cy + (sin(a) * r * 0.55f).toFloat(), r * 0.48f, pp)
        }
        c.drawCircle(cx, cy, r * 0.3f, fill(0xFFF6C453.toInt()))
    }

    private fun leaf(c: Canvas, cx: Float, cy: Float, len: Float, angle: Float) {
        c.save()
        c.rotate(angle, cx, cy)
        c.drawOval(RectF(cx - len / 2f, cy - len * 0.18f, cx + len / 2f, cy + len * 0.18f), fill(0xFF8DB58A.toInt()))
        c.restore()
    }

    private fun flowerCluster(c: Canvas, x: Float, y: Float, k: Float, dir: Float) {
        leaf(c, x + 30f * k * dir, y + 34f * k * dir, 40f * k, 50f)
        leaf(c, x + 46f * k * dir, y + 8f * k * dir, 36f * k, -20f)
        flower(c, x + 14f * k * dir, y + 14f * k * dir, 30f * k, 0xFFF4A6B7.toInt())
        flower(c, x + 52f * k * dir, y + 30f * k * dir, 18f * k, 0xFFF9C9A8.toInt())
        flower(c, x + 22f * k * dir, y + 54f * k * dir, 14f * k, 0xFFE98AA0.toInt())
    }

    private fun balloon(c: Canvas, cx: Float, cy: Float, k: Float, color: Int) {
        val rw = 18f * k
        val rh = 23f * k
        c.drawLine(cx, cy + rh, cx + 4f * k, cy + rh + 40f * k, stroke(0xFF8D99AE.toInt(), 1.5f * k))
        c.drawOval(RectF(cx - rw, cy - rh, cx + rw, cy + rh), fill(color))
        val knot = Path().apply { moveTo(cx - 4f * k, cy + rh + 5f * k); lineTo(cx + 4f * k, cy + rh + 5f * k); lineTo(cx, cy + rh - 1f * k); close() }
        c.drawPath(knot, fill(color))
        c.drawOval(RectF(cx - rw * 0.55f, cy - rh * 0.7f, cx - rw * 0.15f, cy - rh * 0.2f), fill(Color.WHITE, 110))
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val path = Path().apply {
            moveTo(cx, cy + s * 0.9f)
            cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 1.0f, cx, cy - s * 0.35f)
            cubicTo(cx + s * 0.6f, cy - s * 1.0f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
            close()
        }
        c.drawPath(path, p)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        val ray = stroke(0xFFF9A03F.toInt(), r * 0.18f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(
                cx + (cos(a) * r * 1.35f).toFloat(), cy + (sin(a) * r * 1.35f).toFloat(),
                cx + (cos(a) * r * 1.8f).toFloat(), cy + (sin(a) * r * 1.8f).toFloat(), ray
            )
        }
        c.drawCircle(cx, cy, r, fill(0xFFF9C74F.toInt()))
    }

    private fun gradCap(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val dark = Color.rgb((Color.red(color) * 0.75f).toInt(), (Color.green(color) * 0.75f).toInt(), (Color.blue(color) * 0.75f).toInt())
        c.drawRect(cx - s * 0.55f, cy + s * 0.05f, cx + s * 0.55f, cy + s * 0.5f, fill(dark))
        val top = Path().apply {
            moveTo(cx - s, cy); lineTo(cx, cy - s * 0.45f); lineTo(cx + s, cy); lineTo(cx, cy + s * 0.45f); close()
        }
        c.drawPath(top, fill(color))
        c.drawLine(cx, cy, cx + s * 0.8f, cy + s * 0.15f, stroke(color, s * 0.07f))
        c.drawLine(cx + s * 0.8f, cy + s * 0.15f, cx + s * 0.8f, cy + s * 0.75f, stroke(color, s * 0.07f))
        c.drawCircle(cx + s * 0.8f, cy + s * 0.8f, s * 0.09f, fill(color))
    }

    private fun sprockets(c: Canvas, cx: Float, h: Float, k: Float) {
        val p = fill(0xFFE8E8E8.toInt())
        val hw = 8f * k
        val hh = 11f * k
        var y = 24f * k
        while (y < h - 20f * k) {
            c.drawRoundRect(RectF(cx - hw, y - hh, cx + hw, y + hh), 4f * k, 4f * k, p)
            y += 44f * k
        }
    }

    private fun crescent(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, bg: Int) {
        c.drawCircle(cx, cy, r, fill(color))
        c.drawCircle(cx + r * 0.38f, cy - r * 0.22f, r * 0.85f, fill(bg))
        star4(c, cx + r * 0.75f, cy - r * 0.55f, r * 0.32f, fill(color))
    }

    private fun ketupat(c: Canvas, cx: Float, cy: Float, r: Float, gold: Int) {
        val body = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
        c.drawPath(body, fill(0xFF6A994E.toInt()))
        c.drawPath(body, stroke(gold, 2f))
        val line = stroke(gold, 1.6f)
        for (t in listOf(-0.33f, 0.33f)) {
            c.drawLine(cx - r * 0.5f + t * r, cy - r * 0.5f - t * r, cx + r * 0.5f + t * r, cy + r * 0.5f - t * r, line)
            c.drawLine(cx + r * 0.5f + t * r, cy - r * 0.5f + t * r, cx - r * 0.5f + t * r, cy + r * 0.5f + t * r, line)
        }
        c.drawLine(cx, cy + r, cx - r * 0.25f, cy + r * 1.5f, stroke(gold, 2f))
        c.drawLine(cx, cy + r, cx + r * 0.25f, cy + r * 1.5f, stroke(gold, 2f))
    }
}
