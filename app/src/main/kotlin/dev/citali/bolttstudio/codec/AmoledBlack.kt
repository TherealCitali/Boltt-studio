package dev.citali.bolttstudio.codec

/** Background-only correction, applied after scaling so interpolation cannot reintroduce greys.
 * Nearly neutral pixels below the cutoff become exact RGB zero; opacity is preserved. */
object AmoledBlack {
    const val DEFAULT_CUTOFF = 24
    const val MAX_CUTOFF = 48
    const val NEUTRAL_TOLERANCE = 8
    fun correct(argb: Int, cutoff: Int = DEFAULT_CUTOFF): Int {
        val r = argb ushr 16 and 255; val g = argb ushr 8 and 255; val b = argb and 255
        val maximum = maxOf(r, g, b); val minimum = minOf(r, g, b)
        return if (maximum <= cutoff.coerceIn(0, MAX_CUTOFF) && maximum - minimum <= NEUTRAL_TOLERANCE)
            argb and 0xff000000.toInt() else argb
    }
    fun apply(pixels: IntArray, cutoff: Int = DEFAULT_CUTOFF) {
        for (i in pixels.indices) pixels[i] = correct(pixels[i], cutoff)
    }
}
