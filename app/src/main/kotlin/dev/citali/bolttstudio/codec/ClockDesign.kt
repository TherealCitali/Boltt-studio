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
