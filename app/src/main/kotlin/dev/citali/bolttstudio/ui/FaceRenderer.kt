package dev.citali.bolttstudio.ui

import android.graphics.*
import dev.citali.bolttstudio.codec.*
import dev.citali.bolttstudio.codec.Point
import kotlin.math.max
import kotlin.math.min

object FaceRenderer {
    // Rendering runs on the Compose UI thread. Reuse glyphs while dragging/cropping.
    private data class GlyphKey(val family: String, val custom: Typeface?, val hours: Int, val minutes: Int, val outline: Boolean)
    private var glyphKey: GlyphKey? = null
    private var glyphs = emptyList<List<Bitmap>>()
    private var glyphImages = emptyList<List<FaceImage>>()
    data class Frame(val image: Bitmap, val face: MoyFace.Face)
    fun Bitmap.faceImage(): FaceImage {
        val data = IntArray(width * height); getPixels(data, 0, width, 0, 0, width, height)
        return FaceImage(width, height, data)
    }
    fun render(background: Bitmap?, x: Int, y: Int, stacked: Boolean, family: String,
        hourColor: Int, minuteColor: Int, outline: Boolean, zoom: Float, panX: Float, panY: Float, customTypeface: Typeface? = null): Frame {
        val bg = Bitmap.createBitmap(240, 296, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bg); canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (background != null) {
            val scale = max(240f / background.width, 296f / background.height) * zoom.coerceIn(1f, 3f)
            val w = background.width * scale; val h = background.height * scale
            val left = (240 - w) / 2 + panX * (w - 240) / 2; val top = (296 - h) / 2 + panY * (h - 296) / 2
            canvas.drawBitmap(background, null, RectF(left, top, left + w, top + h), paint)
        } else {
            paint.shader = LinearGradient(0f, 0f, 0f, 296f, 0xff2b3a55.toInt(), 0xff0d1220.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 240f, 296f, paint); paint.shader = null
        }
        fun digit(n: Int, color: Int): Bitmap {
            val big = Bitmap.createBitmap(126, 198, Bitmap.Config.ARGB_8888); val c = Canvas(big)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = customTypeface ?: Typeface.create(family, Typeface.BOLD); textSize = 66 * 2.2f; textAlign = Paint.Align.CENTER
                strokeJoin = Paint.Join.ROUND
            }
            val baseline = 99 - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2
            if (outline) { p.style = Paint.Style.STROKE; p.strokeWidth = 66 * .12f; p.color = 0x8c000000.toInt(); c.drawText(n.toString(), 63f, baseline, p) }
            p.style = Paint.Style.FILL; p.color = color; c.drawText(n.toString(), 63f, baseline, p)
            val pixels = IntArray(126 * 198); big.getPixels(pixels, 0, 126, 0, 0, 126, 198)
            var left = 126; var top = 198; var right = -1; var bottom = -1
            pixels.forEachIndexed { i, value -> if ((value ushr 24) > 8) {
                left = min(left, i % 126); right = max(right, i % 126); top = min(top, i / 126); bottom = max(bottom, i / 126)
            } }
            val result = Bitmap.createBitmap(42, 66, Bitmap.Config.ARGB_8888)
            if (right >= left) {
                val w = right - left + 1; val h = bottom - top + 1; val scale = min(42f / w, 66f / h)
                val dx = (42 - w * scale) / 2; val dy = (66 - h * scale) / 2
                Canvas(result).drawBitmap(big, Rect(left, top, right + 1, bottom + 1), RectF(dx, dy, dx + w * scale, dy + h * scale), paint)
            }
            big.recycle(); return result
        }
        val key = GlyphKey(family, customTypeface, hourColor, minuteColor, outline)
        if (glyphKey != key) {
            glyphs.flatten().forEach { it.recycle() }
            glyphs = listOf(hourColor, minuteColor).map { color -> (0..9).map { digit(it, color) } }
            glyphImages = glyphs.map { set -> set.map { it.faceImage() } }
            glyphKey = key
        }
        val digits = glyphs
        val offsets = if (stacked) listOf(Point(0, 0), Point(50, 0), Point(0, 80), Point(50, 80))
            else listOf(Point(0, 0), Point(50, 0), Point(111, 0), Point(161, 0))
        val px = x.coerceIn(0, if (stacked) 148 else 37); val py = y.coerceIn(0, if (stacked) 150 else 230)
        val positions = offsets.map { Point(it.x + px, it.y + py) }; val sets = listOf(0, 0, 1, 1)
        val preview = bg.copy(Bitmap.Config.ARGB_8888, true); val pc = Canvas(preview)
        listOf(1, 0, 0, 9).forEachIndexed { i, digit -> pc.drawBitmap(digits[sets[i]][digit], positions[i].x.toFloat(), positions[i].y.toFloat(), paint) }
        val small = Bitmap.createScaledBitmap(preview, 140, 163, true)
        val face = MoyFace.Face(bg.faceImage(), glyphImages, positions, sets, small.faceImage(), FaceImage(1, 1, intArrayOf(0)))
        bg.recycle(); small.recycle()
        return Frame(preview, face)
    }
}
