// SPDX-License-Identifier: GPL-3.0-only
package dev.citali.bolttstudio.codec

/** Deterministic firmware experiment, not a claim of photo-depth support. No frozen-time fallback. */
object DepthProbe {
    const val BACKGROUND = 0xff102031.toInt()
    const val STRIPE = 0xffff00ff.toInt()
    data class Result(val face: MoyFace.Face, val simulatedPreview: FaceImage, val bytes: ByteArray)

    /** Trim transparent padding without changing the screen registration of the layer. */
    fun cropForeground(canvas: FaceImage): ImageLayer? {
        require(canvas.width == MoyFace.WIDTH && canvas.height == MoyFace.HEIGHT)
        var left = canvas.width; var top = canvas.height; var right = -1; var bottom = -1
        canvas.pixels.forEachIndexed { i, p ->
            if ((p ushr 24) != 0) {
                val x = i % canvas.width; val y = i / canvas.width
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        val width = right - left + 1; val height = bottom - top + 1
        val pixels = IntArray(width * height) { i -> canvas.pixels[(top + i / width) * canvas.width + left + i % width] }
        return ImageLayer(FaceImage(width, height, pixels), Point(left, top))
    }

    fun create(includeForeground: Boolean = true): Result {
        // Large, deterministic seven-segment digits avoid depending on imported/system font metrics.
        val segments = listOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")
        val rectangles = listOf(
            intArrayOf(6, 0, 36, 6), intArrayOf(36, 6, 42, 30), intArrayOf(36, 36, 42, 60),
            intArrayOf(6, 60, 36, 66), intArrayOf(0, 36, 6, 60), intArrayOf(0, 6, 6, 30), intArrayOf(6, 30, 36, 36))
        val digits = segments.map { active ->
            val pixels = IntArray(42 * 66)
            active.forEach { segment ->
                val r = rectangles[segment - 'a']
                for (y in r[1] until r[3]) for (x in r[0] until r[2]) pixels[y * 42 + x] = -1
            }
            FaceImage(42, 66, pixels)
        }
        val background = FaceImage(240, 296, IntArray(240 * 296) { BACKGROUND })
        val mask = IntArray(240 * 296)
        for (y in 139..156) for (x in 12..227) {
            // A clear window through the stripe crosses the second digit's left segment.
            if (x in 72..75) continue
            val edge = minOf(y - 139, 156 - y)
            val alpha = when { edge < 2 -> 64; edge < 4 -> 128; else -> 255 }
            mask[y * 240 + x] = (alpha shl 24) or (STRIPE and 0xffffff)
        }
        val foreground = if (includeForeground) cropForeground(FaceImage(240, 296, mask)) else null
        val positions = listOf(Point(19, 115), Point(69, 115), Point(129, 115), Point(179, 115))
        val composite = background.pixels.clone()
        fun draw(image: FaceImage, point: Point) {
            image.pixels.forEachIndexed { i, pixel ->
                val target = (point.y + i / image.width) * 240 + point.x + i % image.width
                val alpha = pixel ushr 24; val below = composite[target]
                fun channel(shift: Int) = ((((pixel ushr shift) and 255) * alpha + ((below ushr shift) and 255) * (255 - alpha) + 127) / 255) shl shift
                composite[target] = 0xff000000.toInt() or channel(16) or channel(8) or channel(0)
            }
        }
        listOf(1, 0, 0, 9).forEachIndexed { i, digit -> draw(digits[digit], positions[i]) }
        foreground?.let { draw(it.image, it.position) }
        // Only the thumbnail and app simulation show fixed 10:09. Exported background and
        // digit tables stay separate; all four display positions use the live TimeNum element.
        val preview = FaceImage(140, 163, IntArray(140 * 163) { i -> composite[(i / 140 * 296 / 163) * 240 + (i % 140 * 240 / 140)] })
        val face = MoyFace.Face(background, listOf(digits), positions, listOf(0, 0, 0, 0), preview,
            FaceImage(1, 1, intArrayOf(0)), foreground)
        return Result(face, FaceImage(240, 296, composite), MoyFace.build(face))
    }
    private val editorStripe: ImageLayer by lazy { requireNotNull(create().face.foreground) }

    /** Use the actual cropped editor background and LIVE glyph tables, never a flattened clock. */
    fun fromEditor(source: MoyFace.Face, digits: List<Int>, includeForeground: Boolean, stripeY: Int = 139): Result {
        require(digits.size == 4 && digits.all { it in 0..9 })
        require(stripeY in 0..278)
        val foreground = if (includeForeground) {
            val prototype = editorStripe
            prototype.copy(position = Point(prototype.position.x, stripeY))
        } else null
        val pixels = source.background.pixels.clone()
        fun draw(image: FaceImage, point: Point) {
            require(point.x >= 0 && point.y >= 0 && point.x + image.width <= 240 && point.y + image.height <= 296)
            image.pixels.forEachIndexed { i, pixel ->
                val at = (point.y + i / image.width) * 240 + point.x + i % image.width
                val alpha = pixel ushr 24; val below = pixels[at]
                fun channel(shift: Int) = ((((pixel ushr shift) and 255) * alpha + ((below ushr shift) and 255) * (255 - alpha) + 127) / 255) shl shift
                pixels[at] = 0xff000000.toInt() or channel(16) or channel(8) or channel(0)
            }
        }
        digits.forEachIndexed { i, digit -> draw(source.digits[source.sets[i]][digit], source.positions[i]) }
        foreground?.let { draw(it.image, it.position) }
        val thumbnail = FaceImage(140, 163, IntArray(140 * 163) { i -> pixels[(i / 140 * 296 / 163) * 240 + i % 140 * 240 / 140] })
        val face = source.copy(preview = thumbnail, foreground = foreground)
        return Result(face, FaceImage(240, 296, pixels), MoyFace.build(face))
    }

}
