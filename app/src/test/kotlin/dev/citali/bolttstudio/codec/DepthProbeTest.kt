package dev.citali.bolttstudio.codec

import org.junit.Assert.*
import org.junit.Test

class DepthProbeTest {
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
    @Test fun outputMatchesIndependentPythonFixture() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("depth_probe.bin")!!.use { it.readBytes() }
        assertArrayEquals(fixture, DepthProbe.create().bytes)
        assertArrayEquals(fixture, MoyFace.build(MoyFace.parse(fixture)))
    }
    @Test fun foregroundFollowsTheLiveTimeElementBeforeTerminator() {
        val b = DepthProbe.create().bytes
        var o = u16(b, 14)
        assertEquals(0x23, b[o + 1].toInt()); o += 10
        assertEquals(0, b[o + 1].toInt()); o += 14
        assertEquals(2, b[o + 1].toInt())
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), b.copyOfRange(o + 2, o + 6))
        o += 34
        assertEquals(1, b[o].toInt()); assertEquals(0, b[o + 1].toInt())
        assertEquals(12, u16(b, o + 2)); assertEquals(139, u16(b, o + 4))
        assertEquals(216, u16(b, o + 10)); assertEquals(18, u16(b, o + 12))
        assertEquals(0, u16(b, o + 14))
    }
    @Test fun backgroundAndDigitsNeverContainFlattenedOcclusion() {
        val overlay = DepthProbe.create(); val control = DepthProbe.create(false)
        assertTrue(overlay.face.background.pixels.all { it == DepthProbe.BACKGROUND })
        assertArrayEquals(control.face.background.pixels, overlay.face.background.pixels)
        overlay.face.digits[0].forEachIndexed { i, glyph ->
            assertTrue(glyph.pixels.all { it == 0 || it == -1 })
            assertArrayEquals(control.face.digits[0][i].pixels, glyph.pixels)
        }
        assertEquals(4, overlay.face.positions.size)
        assertEquals(control.face.positions, overlay.face.positions)
        assertNull(MoyFace.parse(control.bytes).foreground)
        assertFalse(overlay.face.preview.pixels.contentEquals(control.face.preview.pixels))
    }
    @Test fun foregroundAlphaAndBoundsSurviveCodecRoundtrip() {
        val result = DepthProbe.create()
        val layer = MoyFace.parse(result.bytes).foreground!!
        assertEquals(Point(12, 139), layer.position)
        assertEquals(setOf(0, 64, 128, 255), layer.image.pixels.map { it ushr 24 }.toSet())
        assertArrayEquals(result.face.foreground!!.image.pixels, layer.image.pixels)
        assertTrue(result.bytes.size < 120956)
    }
    @Test fun croppingRetainsScreenRegistrationAndHandlesEmptyMask() {
        val pixels = IntArray(240 * 296)
        assertNull(DepthProbe.cropForeground(FaceImage(240, 296, pixels)))
        pixels[295 * 240 + 239] = 0x01ffffff
        val layer = DepthProbe.cropForeground(FaceImage(240, 296, pixels))!!
        assertEquals(Point(239, 295), layer.position)
        assertEquals(1, layer.image.width); assertEquals(1, layer.image.height)
        assertEquals(0x01ffffff, layer.image.pixels.single())
    }
    @Test fun rejectsOutOfBoundsAndMisorderedForegrounds() {
        val result = DepthProbe.create()
        val layer = result.face.foreground!!
        for (p in listOf(Point(-1, 0), Point(240, 0), Point(0, 296), Point(Int.MAX_VALUE, 0))) {
            assertTrue(runCatching { MoyFace.build(result.face.copy(foreground = layer.copy(position = p))) }.isFailure)
        }
        val bytes = result.bytes.clone()
        val time = u16(bytes, 14) + 10 + 14
        val timeBytes = bytes.copyOfRange(time, time + 34)
        val fgBytes = bytes.copyOfRange(time + 34, time + 48)
        fgBytes.copyInto(bytes, time); timeBytes.copyInto(bytes, time + 14)
        assertTrue(runCatching { MoyFace.parse(bytes) }.isFailure)
    }
}
