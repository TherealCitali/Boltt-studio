package dev.citali.bolttstudio.codec

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ImportedFaceTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }
    @Test fun capturesExactOriginalBytesRatherThanRebuilding() {
        for (name in listOf("dafit_captured_face.bin", "demo_face.bin")) {
            val bytes = fixture(name)
            val data = ImportedFace.read(ByteArrayInputStream(bytes))
            assertArrayEquals(bytes, data.uploadBytes())
            assertEquals(bytes.size, data.size)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(sha, data.sha256)
        }
    }
    @Test fun inputAndReturnedCopiesCannotChangeSnapshot() {
        val bytes = fixture("demo_face.bin")
        val data = ImportedFace.read(ByteArrayInputStream(bytes))
        val original = data.uploadBytes(); bytes.fill(0)
        val exposed = data.uploadBytes(); exposed.fill(0)
        assertArrayEquals(original, data.uploadBytes())
    }
    @Test fun refusesEmptyTruncatedTextAndUnknownElements() {
        val valid = fixture("demo_face.bin")
        val modified = valid.clone()
        val elements = (modified[14].toInt() and 255) or ((modified[15].toInt() and 255) shl 8)
        modified[elements + 1] = 0x7f
        for (bytes in listOf(byteArrayOf(), valid.copyOf(100), "IwD//w==".toByteArray(), modified)) {
            assertTrue(runCatching { ImportedFace.read(ByteArrayInputStream(bytes)) }.isFailure)
        }
    }
    @Test fun rejectsOversizedImageDescriptorsBeforeDecodingTables() {
        val bytes = fixture("demo_face.bin").clone()
        // First glyph descriptor width (digit table starts at16, first image descriptor at19).
        bytes[23] = 0; bytes[24] = 2 // 512, beyond the240-pixel watch canvas
        assertTrue(runCatching { ImportedFace.read(ByteArrayInputStream(bytes)) }.isFailure)
    }
    @Test fun boundsUnknownLengthStreamsWithoutLoadingWholeFile() {
        var read = 0
        val stream = object : InputStream() { override fun read(): Int { read++; return 0 } }
        assertTrue(runCatching { ImportedFace.read(stream) }.isFailure)
        assertTrue(read <= MoyFace.MAX_FILE_BYTES + 8192)
    }
    @Test fun supportsShortReadsAndZeroLengthReadFallback() {
        val bytes = fixture("demo_face.bin")
        val stream = object : ByteArrayInputStream(bytes) {
            var first = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; return 0 }
                return super.read(b, off, minOf(len, 7))
            }
        }
        assertArrayEquals(bytes, ImportedFace.read(stream).uploadBytes())
    }
}
