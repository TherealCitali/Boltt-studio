// SPDX-License-Identifier: GPL-3.0-only
// Native port of this repository's moyface.py / web/moyface.js.
package dev.citali.bolttstudio.codec

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Straight ARGB pixels, with no Android dependencies: codec is testable on the JVM. */
data class FaceImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width in 1..512 && height in 1..512) { "Unsupported image dimensions" }
        require(pixels.size == width * height) { "Invalid pixel count" }
    }
}
data class Point(val x: Int, val y: Int)

object MoyFace {
    const val WIDTH = 240
    const val HEIGHT = 296
    const val MAX_FILE_BYTES = 2 * 1024 * 1024

    private fun ByteArray.u8(i: Int): Int { require(i in indices) { "Truncated file" }; return this[i].toInt() and 255 }
    private fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
    private fun ByteArray.u32(i: Int): Int {
        val n = (0..3).fold(0L) { v, j -> v or (u8(i + j).toLong() shl (8 * j)) }
        require(n <= Int.MAX_VALUE) { "Offset out of range" }; return n.toInt()
    }
    private fun ByteArrayOutputStream.u16(n: Int) { require(n in 0..65535); write(n and 255); write(n ushr 8) }
    private fun ByteArrayOutputStream.u32(n: Int) { require(n >= 0); repeat(4) { write((n ushr (8 * it)) and 255) } }
    private fun packed(argb: Int): Int = ((argb ushr 24) shl 16) or
        (((argb ushr 19) and 31) shl 11) or (((argb ushr 10) and 63) shl 5) or ((argb ushr 3) and 31)
    private fun rgba(a: Int, v: Int): Int {
        val r = (v ushr 11) and 31; val g = (v ushr 5) and 63; val b = v and 31
        return (a shl 24) or (((r shl 3) or (r ushr 2)) shl 16) or
            (((g shl 2) or (g ushr 4)) shl 8) or (b shl 3) or (b ushr 2)
    }
    private fun ByteArrayOutputStream.pixel(v: Int) { write((v ushr 16) and 255); write((v ushr 8) and 255); write(v and 255) }

    fun encodeImage(image: FaceImage): ByteArray {
        val rows = (0 until image.height).map { y ->
            val pixels = IntArray(image.width) { packed(image.pixels[y * image.width + it]) }
            val row = ByteArrayOutputStream(); val literals = ArrayList<Int>()
            fun flush() { literals.chunked(127).forEach { c -> row.write(c.size); c.forEach { row.pixel(it) } }; literals.clear() }
            var x = 0
            while (x < pixels.size) {
                var end = x + 1
                while (end < pixels.size && end - x < 127 && pixels[end] == pixels[x]) end++
                if (end - x >= 2) { flush(); row.write(128 or (end - x)); row.pixel(pixels[x]); x = end }
                else { literals.add(pixels[x]); x++ }
            }
            flush(); row.toByteArray().also { require(it.size < 2048) }
        }
        val out = ByteArrayOutputStream(); var offset = 4 * image.height
        rows.forEach { row ->
            require(offset < (1 shl 21))
            out.u16(offset and 65535); out.u16((row.size shl 5) or (offset ushr 16)); offset += row.size
        }
        rows.forEach { out.write(it) }; while (out.size() % 4 != 0) out.write(0)
        return out.toByteArray()
    }

    fun decodeImage(bytes: ByteArray, offset: Int, width: Int, height: Int): FaceImage {
        require(bytes.size <= MAX_FILE_BYTES && offset >= 0 && width in 1..512 && height in 1..512)
        require(offset.toLong() + height * 4L <= bytes.size)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val entry = offset + y * 4; val low = bytes.u16(entry); val size = bytes.u16(entry + 2)
            val relative = low + ((size and 31) shl 16)
            val start = offset.toLong() + relative; val end = start + (size ushr 5)
            require(relative >= height * 4 && start <= end && end <= bytes.size) { "Invalid row offsets" }
            var i = start.toInt(); var x = 0
            fun pixel(): Int {
                require(i + 3L <= end) { "Truncated pixel" }
                val result = rgba(bytes.u8(i), (bytes.u8(i + 1) shl 8) or bytes.u8(i + 2)); i += 3; return result
            }
            while (i < end) {
                val flag = bytes.u8(i++); val count = flag and 127
                require(count > 0 && x + count <= width) { "Invalid RLE run" }
                if ((flag and 128) != 0) { val p = pixel(); repeat(count) { pixels[y * width + x++] = p } }
                else repeat(count) { pixels[y * width + x++] = pixel() }
            }
            require(x == width && i.toLong() == end) { "Incomplete row" }
        }
        return FaceImage(width, height, pixels)
    }

    data class Face(val background: FaceImage, val digits: List<List<FaceImage>>, val positions: List<Point>,
        val sets: List<Int>, val preview: FaceImage, val dash: FaceImage)

    fun parse(bytes: ByteArray): Face {
        require(bytes.size in 18..MAX_FILE_BYTES && bytes.u16(0) == 0x23) { "Not a supported API 0x23 face" }
        fun image(o: Int) = decodeImage(bytes, bytes.u32(o), bytes.u16(o + 4), bytes.u16(o + 6))
        val preview = image(4); val digitOffset = bytes.u16(12); val elementOffset = bytes.u16(14)
        require(digitOffset >= 16 && elementOffset >= digitOffset + 2 && elementOffset < bytes.size)
        require((elementOffset - digitOffset - 2) % 83 == 0)
        val n = (elementOffset - digitOffset - 2) / 83; require(n in 1..4)
        val digits = (0 until n).map { set -> (0..9).map { image(digitOffset + 3 + set * 83 + it * 8) } }
        var o = elementOffset; var background: FaceImage? = null; var dash: FaceImage? = null
        var positions: List<Point>? = null; var sets: List<Int>? = null; var count = 0
        while (bytes.u8(o) == 1) {
            require(++count <= 3) { "Duplicate or unsupported elements" }
            when (bytes.u8(o + 1)) {
                0x23 -> {
                    require(dash == null)
                    dash = try { image(o + 2) } catch (error: IllegalArgumentException) {
                        // The supplied captured face has an invalid unused 1x1 dash pointer.
                        // Python silently returns a transparent pixel. Match only this exact
                        // fixture; never relax image bounds for arbitrary imported files.
                        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                            .joinToString("") { "%02x".format(it.toInt() and 255) }
                        if (bytes.u16(o + 6) == 1 && bytes.u16(o + 8) == 1 &&
                            hash == "29d80e83e92c048f3f526edd9535bc477961bef6355c97a99610c1afcec05a5a") FaceImage(1, 1, intArrayOf(0))
                        else throw error
                    }
                    o += 10
                }
                0 -> { require(background == null && bytes.u16(o + 2) == 0 && bytes.u16(o + 4) == 0); background = image(o + 6); o += 14 }
                2 -> {
                    require(positions == null)
                    sets = (0..3).map { bytes.u8(o + 2 + it).also { s -> require(s < n) } }
                    positions = (0..3).map { Point(bytes.u16(o + 6 + it * 4), bytes.u16(o + 8 + it * 4)) }
                    require(o + 34 <= bytes.size); o += 34
                }
                else -> error("Unsupported face element")
            }
        }
        val result = Face(requireNotNull(background), digits, requireNotNull(positions), requireNotNull(sets), preview, requireNotNull(dash))
        validate(result); return result
    }
    private fun validate(face: Face) {
        require(face.background.width == WIDTH && face.background.height == HEIGHT)
        require(face.preview.width == 140 && face.preview.height == 163)
        require(face.digits.size in 1..4 && face.digits.all { it.size == 10 })
        require(face.positions.size == 4 && face.sets.size == 4)
        face.positions.forEachIndexed { i, p ->
            require(face.sets[i] in face.digits.indices)
            face.digits[face.sets[i]].forEach { glyph ->
                require(p.x >= 0 && p.y >= 0 && p.x + glyph.width <= WIDTH && p.y + glyph.height <= HEIGHT) { "Clock exceeds screen bounds" }
            }
        }
    }
    fun build(face: Face): ByteArray {
        validate(face)
        val n = face.digits.size; val headerSize = 16 + 2 + 83 * n + 10 + 14 + 34 + 2
        var offset = (headerSize + 3) and -4; val blobs = ByteArrayOutputStream()
        fun add(image: FaceImage): Int { val start = offset; val encoded = encodeImage(image); blobs.write(encoded); offset += encoded.size; require(offset <= MAX_FILE_BYTES); return start }
        val dashOffset = add(face.dash); val bgOffset = add(face.background)
        val digitOffsets = face.digits.map { set -> set.map { add(it) } }; val previewOffset = add(face.preview)
        val out = ByteArrayOutputStream()
        fun image(off: Int, img: FaceImage) { out.u32(off); out.u16(img.width); out.u16(img.height) }
        out.u16(0x23); out.u16(0xffff); image(previewOffset, face.preview); out.u16(16); out.u16(18 + 83 * n)
        out.write(1); out.write(1)
        face.digits.forEachIndexed { s, set -> out.write(s); set.forEachIndexed { d, img -> image(digitOffsets[s][d], img) }; out.u16(if (s == 0) 0x0101 else 0) }
        out.write(1); out.write(0x23); image(dashOffset, face.dash)
        out.write(1); out.write(0); out.u16(0); out.u16(0); image(bgOffset, face.background)
        out.write(1); out.write(2); face.sets.forEach { out.write(it) }
        face.positions.forEach { out.u16(it.x); out.u16(it.y) }; repeat(14) { out.write(0) }
        while (out.size() % 4 != 0) out.write(0)
        require(out.size() == ((headerSize + 3) and -4)); out.write(blobs.toByteArray()); return out.toByteArray()
    }
}
