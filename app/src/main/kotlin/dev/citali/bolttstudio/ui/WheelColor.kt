package dev.citali.bolttstudio.ui

import kotlin.math.*

/** Pure math shared by wheel drawing, touch mapping and JVM tests. Colors remain opaque. */
data class WheelColor(val hue: Float, val saturation: Float, val value: Float) {
    fun argb(): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val s = saturation.coerceIn(0f, 1f); val v = value.coerceIn(0f, 1f)
        val c = v * s; val x = c * (1f - abs(h % 2f - 1f)); val m = v - c
        val rgb = when (h.toInt()) {
            0 -> floatArrayOf(c, x, 0f); 1 -> floatArrayOf(x, c, 0f)
            2 -> floatArrayOf(0f, c, x); 3 -> floatArrayOf(0f, x, c)
            4 -> floatArrayOf(x, 0f, c); else -> floatArrayOf(c, 0f, x)
        }
        val components = rgb.map { ((it + m) * 255f).roundToInt().coerceIn(0, 255) }
        return (0xff shl 24) or (components[0] shl 16) or (components[1] shl 8) or components[2]
    }
    companion object {
        fun fromArgb(color: Int): WheelColor {
            val r = (color ushr 16 and 255) / 255f; val g = (color ushr 8 and 255) / 255f; val b = (color and 255) / 255f
            val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
            val h = if (d == 0f) 0f else when (max) {
                r -> 60f * ((g - b) / d % 6f)
                g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }
            return WheelColor((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
        }
    }
}

class WheelGeometry(val size: Float) {
    val center = size / 2f
    val radius = size * .46f
    val ringRadius = radius * .85f
    val ringWidth = radius * .22f
    val halfSide = radius * .66f / sqrt(2f)
    fun isRing(x: Float, y: Float): Boolean = hypot(x - center, y - center) in radius * .72f..radius * 1.04f
    private fun local(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - center; val dy = y - center
        return (dx + dy) / sqrt(2f) to (dy - dx) / sqrt(2f)
    }
    fun isDiamond(x: Float, y: Float): Boolean = local(x, y).let { abs(it.first) <= halfSide && abs(it.second) <= halfSide }
    fun hue(x: Float, y: Float): Float = ((atan2(y - center, x - center) * 180f / PI.toFloat()) + 360f) % 360f
    fun sv(x: Float, y: Float): Pair<Float, Float> = local(x, y).let {
        ((it.first + halfSide) / (2 * halfSide)).coerceIn(0f, 1f) to
            (1f - (it.second + halfSide) / (2 * halfSide)).coerceIn(0f, 1f)
    }
    fun point(s: Float, v: Float): Pair<Float, Float> {
        val x = (s * 2f - 1f) * halfSide; val y = (1f - v * 2f) * halfSide
        return center + (x - y) / sqrt(2f) to center + (x + y) / sqrt(2f)
    }
}
