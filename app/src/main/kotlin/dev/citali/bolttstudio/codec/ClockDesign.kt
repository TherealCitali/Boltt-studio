package dev.citali.bolttstudio.codec

import kotlin.math.*

data class ClockGroup(val x: Int, val y: Int, val width: Int = 42, val height: Int = 66, val gap: Int = 8) {
    val span get() = width * 2 + gap
    fun bounded(): ClockGroup {
        val g = gap.coerceIn(0, 24)
        val w = width.coerceIn(8, (240 - g) / 2)
        val h = height.coerceIn(16, 240)
        return copy(x = x.coerceIn(0, 240 - w * 2 - g), y = y.coerceIn(0, 296 - h), width = w, height = h, gap = g)
    }
}
data class ClockDesign(val hours: ClockGroup = ClockGroup(74, 41), val minutes: ClockGroup = ClockGroup(74, 121)) {
    val positions get() = listOf(Point(hours.x, hours.y), Point(hours.x + hours.width + hours.gap, hours.y),
        Point(minutes.x, minutes.y), Point(minutes.x + minutes.width + minutes.gap, minutes.y))
    fun move(hour: Boolean, independent: Boolean, dx: Int, dy: Int): ClockDesign {
        if (independent) return if (hour) copy(hours = hours.copy(x = hours.x + dx, y = hours.y + dy).bounded())
            else copy(minutes = minutes.copy(x = minutes.x + dx, y = minutes.y + dy).bounded())
        val left = min(hours.x, minutes.x); val top = min(hours.y, minutes.y)
        val right = max(hours.x + hours.span, minutes.x + minutes.span); val bottom = max(hours.y + hours.height, minutes.y + minutes.height)
        val x = dx.coerceIn(-left, 240 - right); val y = dy.coerceIn(-top, 296 - bottom)
        return copy(hours = hours.copy(x = hours.x + x, y = hours.y + y), minutes = minutes.copy(x = minutes.x + x, y = minutes.y + y))
    }
    fun resized(hour: Boolean, independent: Boolean, width: Int, height: Int, gap: Int): ClockDesign {
        fun size(g: ClockGroup) = g.copy(width = width, height = height, gap = gap).bounded()
        return copy(hours = if (hour || !independent) size(hours) else hours,
            minutes = if (!hour || !independent) size(minutes) else minutes)
    }
    fun arrange(stacked: Boolean): ClockDesign {
        fun fit(g: ClockGroup) = if (stacked) g.copy(height = g.height.coerceAtMost(141)).bounded()
            else g.copy(width = g.width.coerceAtMost((112 - g.gap) / 2)).bounded()
        val h = fit(hours); val m = fit(minutes)
        return if (stacked) {
            val top = (296 - h.height - m.height - 14) / 2
            ClockDesign(h.copy(x = (240 - h.span) / 2, y = top), m.copy(x = (240 - m.span) / 2, y = top + h.height + 14))
        } else {
            val left = (240 - h.span - m.span - 16) / 2
            ClockDesign(h.copy(x = left, y = (296 - h.height) / 2), m.copy(x = left + h.span + 16, y = (296 - m.height) / 2))
        }
    }
    val overlaps get() = hours.x < minutes.x + minutes.span && minutes.x < hours.x + hours.span &&
        hours.y < minutes.y + minutes.height && minutes.y < hours.y + hours.height
}

/** Screen-space retained-alpha mask: 255 shows the clock, 0 reveals the background. */
object ClockMask {
    fun empty() = ByteArray(240 * 296) { -1 }
    fun stroke(mask: ByteArray, fromX: Float, fromY: Float, toX: Float, toY: Float, radius: Float, restore: Boolean, feather: Boolean): ByteArray {
        require(mask.size == 240 * 296 && radius in 1f..60f)
        val result = mask.clone()
        val dx = toX - fromX; val dy = toY - fromY; val length = dx * dx + dy * dy
        val left = floor(min(fromX, toX) - radius).toInt().coerceAtLeast(0)
        val right = ceil(max(fromX, toX) + radius).toInt().coerceAtMost(239)
        val top = floor(min(fromY, toY) - radius).toInt().coerceAtLeast(0)
        val bottom = ceil(max(fromY, toY) + radius).toInt().coerceAtMost(295)
        for (y in top..bottom) for (x in left..right) {
            val t = if (length == 0f) 0f else (((x - fromX) * dx + (y - fromY) * dy) / length).coerceIn(0f, 1f)
            val distance = hypot(x - fromX - t * dx, y - fromY - t * dy)
            if (distance > radius) continue
            val amount = if (feather) ((radius - distance) / max(1f, radius * .35f)).coerceIn(0f, 1f) else 1f
            val desired = (255 * (if (restore) amount else 1f - amount)).roundToInt()
            val old = result[y * 240 + x].toInt() and 255
            result[y * 240 + x] = (if (restore) max(old, desired) else min(old, desired)).toByte()
        }
        return result
    }
    fun apply(glyph: FaceImage, position: Point, mask: ByteArray): FaceImage {
        require(mask.size == 240 * 296 && position.x >= 0 && position.y >= 0 && position.x + glyph.width <= 240 && position.y + glyph.height <= 296)
        return FaceImage(glyph.width, glyph.height, IntArray(glyph.pixels.size) { i ->
            val pixel = glyph.pixels[i]
            val keep = mask[(position.y + i / glyph.width) * 240 + position.x + i % glyph.width].toInt() and 255
            val alpha = ((pixel ushr 24) * keep + 127) / 255
            if (alpha == 0) 0 else (alpha shl 24) or (pixel and 0xffffff)
        })
    }
}
