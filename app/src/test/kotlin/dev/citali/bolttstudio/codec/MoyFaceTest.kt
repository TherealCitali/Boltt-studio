package dev.citali.bolttstudio.codec

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class MoyFaceTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)).use { it.readBytes() }
    @Test fun capturedAndDemoMatchIndependentPythonEncoder() {
        listOf("dafit_captured_face.bin", "demo_face.bin").forEach { name ->
            val f = MoyFace.parse(fixture(name)); val bytes = MoyFace.build(f)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(String(fixture("$name.rebuilt.sha256")).trim(), hash)
            val g = MoyFace.parse(bytes)
            assertArrayEquals(f.background.pixels, g.background.pixels)
            assertArrayEquals(f.preview.pixels, g.preview.pixels)
            assertEquals(f.positions, g.positions); assertEquals(f.sets, g.sets)
        }
    }
    @Test fun repeatedAndLiteralRowsPreserveQuantizedPixels() {
        val pixels = IntArray(240 * 3) { i -> when (i / 240) {
            0 -> 0xffffffff.toInt()
            1 -> 0
            else -> (0xff000000L or ((i % 32 * 8).toLong() shl 16) or ((i % 64 * 4).toLong() shl 8) or (i % 32 * 8).toLong()).toInt()
        } }
        val img = FaceImage(240, 3, pixels)
        val a = MoyFace.encodeImage(img); val decoded = MoyFace.decodeImage(a, 0, 240, 3)
        assertArrayEquals(a, MoyFace.encodeImage(decoded))
        assertEquals(0, a.size % 4)
    }
    @Test fun malformedInputsAreRejected() {
        assertTrue(runCatching { MoyFace.parse(ByteArray(12)) }.isFailure)
        val original = fixture("dafit_captured_face.bin")
        assertTrue(runCatching { MoyFace.parse(original.copyOf(100)) }.isFailure)
        assertTrue(runCatching { MoyFace.parse(original.clone().also { it[0] = 1 }) }.isFailure)
        assertTrue(runCatching { MoyFace.decodeImage(byteArrayOf(4,0,32,0,0), 0, 1, 1) }.isFailure)
        assertTrue(runCatching { MoyFace.decodeImage(byteArrayOf(4,0,32,0,127), 0, 1, 1) }.isFailure)
        val face = MoyFace.parse(original)
        assertTrue(runCatching { MoyFace.build(face.copy(positions = listOf(Point(240,296)) + face.positions.drop(1))) }.isFailure)
        assertTrue(runCatching { MoyFace.build(face.copy(sets = listOf(9,9,9,9))) }.isFailure)
    }
}
