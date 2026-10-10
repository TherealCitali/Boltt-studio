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
    private data class ScaleKey(val glyph: GlyphKey, val hourWidth: Int, val hourHeight: Int, val minuteWidth: Int, val minuteHeight: Int)
    private var scaleKey: ScaleKey? = null
    private var scaledGlyphs = emptyList<List<FaceImage>>()
    data class Frame(val image: Bitmap, val face: MoyFace.Face, val maxOcclusion: Float = 0f)
    fun Bitmap.faceImage(): FaceImage {
        val data = IntArray(width * height); getPixels(data, 0, width, 0, 0, width, height)
        return FaceImage(width, height, data)
    }
    fun render(background: Bitmap?, x: Int, y: Int, stacked: Boolean, family: String,
        hourColor: Int, minuteColor: Int, outline: Boolean, zoom: Float, panX: Float, panY: Float, customTypeface: Typeface? = null,
        design: ClockDesign? = null, mask: ByteArray? = null, previewDigits: List<Int> = listOf(1, 0, 0, 9)): Frame {
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
            val big = Bitmap.createBitmap(252, 396, Bitmap.Config.ARGB_8888); val c = Canvas(big)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = customTypeface ?: Typeface.create(family, Typeface.BOLD); textSize = 132 * 2.2f; textAlign = Paint.Align.CENTER
                strokeJoin = Paint.Join.ROUND
            }
            val baseline = 198 - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2
            if (outline) { p.style = Paint.Style.STROKE; p.strokeWidth = 132 * .12f; p.color = 0x8c000000.toInt(); c.drawText(n.toString(), 126f, baseline, p) }
            p.style = Paint.Style.FILL; p.color = color; c.drawText(n.toString(), 126f, baseline, p)
            val pixels = IntArray(252 * 396); big.getPixels(pixels, 0, 252, 0, 0, 252, 396)
            var left = 252; var top = 396; var right = -1; var bottom = -1
            pixels.forEachIndexed { i, value -> if ((value ushr 24) > 8) {
                left = min(left, i % 252); right = max(right, i % 252); top = min(top, i / 252); bottom = max(bottom, i / 252)
            } }
            val result = Bitmap.createBitmap(126, 198, Bitmap.Config.ARGB_8888)
            if (right >= left) {
                val w = right - left + 1; val h = bottom - top + 1; val scale = min(126f / w, 198f / h)
                val dx = (126 - w * scale) / 2; val dy = (198 - h * scale) / 2
                Canvas(result).drawBitmap(big, Rect(left, top, right + 1, bottom + 1), RectF(dx, dy, dx + w * scale, dy + h * scale), paint)
            }
            big.recycle(); return result
        }
        val key = GlyphKey(family, customTypeface, hourColor, minuteColor, outline)
        if (glyphKey != key) {
            glyphs.flatten().forEach { it.recycle() }
            glyphs = listOf(hourColor, minuteColor).map { color -> (0..9).map { digit(it, color) } }
            glyphKey = key
        }
        val layout = design ?: ClockDesign().let {
            if (stacked) ClockDesign(ClockGroup(x, y).bounded(), ClockGroup(x, y + 80).bounded())
            else ClockDesign(ClockGroup(x, y).bounded(), ClockGroup(x + 111, y).bounded())
        }
        val groups = listOf(layout.hours, layout.minutes)
        val desiredScale = ScaleKey(key, layout.hours.width, layout.hours.height, layout.minutes.width, layout.minutes.height)
        if (scaleKey != desiredScale) {
            scaledGlyphs = groups.mapIndexed { index, group -> glyphs[index].map { bitmap ->
                val sized = Bitmap.createScaledBitmap(bitmap, group.width, group.height, true)
                val result = sized.faceImage()
                if (sized !== bitmap) sized.recycle()
                result
            } }
            scaleKey = desiredScale
        }
        val scaled = scaledGlyphs
        val positions = layout.positions
        val activeMask = mask?.takeIf { data -> data.any { (it.toInt() and 255) != 255 } }
        // Each position needs its OWN table: a screen-space hole must not repeat at another slot.
        val images = if (activeMask == null) scaled else positions.mapIndexed { i, point ->
            scaled[i / 2].map { ClockMask.apply(it, point, activeMask) }
        }
        val maxOcclusion = if (activeMask == null) 0f else positions.indices.maxOf { i ->
            (0..9).maxOf { digit ->
                val before = scaled[i / 2][digit].pixels.sumOf { it ushr 24 }
                val after = images[i][digit].pixels.sumOf { it ushr 24 }
                if (before == 0) 0f else 1f - after.toFloat() / before
            }
        }
        val sets = if (activeMask == null) listOf(0, 0, 1, 1) else listOf(0, 1, 2, 3)
        val preview = bg.copy(Bitmap.Config.ARGB_8888, true); val pc = Canvas(preview)
        previewDigits.forEachIndexed { i, digit ->
            val img = images[sets[i]][digit]
            val bitmap = Bitmap.createBitmap(img.pixels, img.width, img.height, Bitmap.Config.ARGB_8888)
            pc.drawBitmap(bitmap, positions[i].x.toFloat(), positions[i].y.toFloat(), paint)
            bitmap.recycle()
        }
        val small = Bitmap.createScaledBitmap(preview, 140, 163, true)
        val face = MoyFace.Face(bg.faceImage(), images, positions, sets, small.faceImage(), FaceImage(1, 1, intArrayOf(0)))
        bg.recycle(); small.recycle()
        return Frame(preview, face, maxOcclusion)
    }
}
