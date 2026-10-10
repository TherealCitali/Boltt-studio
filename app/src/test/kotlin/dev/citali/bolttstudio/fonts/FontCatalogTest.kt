package dev.citali.bolttstudio.fonts

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class FontCatalogTest {
    private fun source() = javaClass.classLoader!!.getResourceAsStream("catalog.tsv")!!.bufferedReader().use { it.readText() }
    @Test fun bundledCatalogHasKnownFamiliesAndSafePinnedUrls() {
        val catalog = FontCatalog.parse(source())
        assertEquals(1723, catalog.fonts.size)
        for (name in listOf("Roboto", "Montserrat", "Bebas Neue", "Roboto Mono", "Inconsolata")) {
            assertTrue(catalog.fonts.any { it.family == name })
        }
        catalog.fonts.forEach {
            val url = java.net.URI(catalog.url(it.path))
            assertEquals("https", url.scheme); assertEquals("raw.githubusercontent.com", url.host)
            assertTrue(url.path.startsWith("/google/fonts/${catalog.revision}/"))
            assertNull(url.query); assertNull(url.fragment)
        }
        assertTrue(catalog.url(catalog.fonts.first().path).contains("%5B"))
    }
    @Test fun searchIsLocalCaseInsensitiveAndCategoryAware() {
        val catalog = FontCatalog.parse(source())
        assertTrue(catalog.search("  roboto MONO  ", "Monospace").any { it.family == "Roboto Mono" })
        assertTrue(catalog.search("Roboto Mono", "Serif").isEmpty())
        assertTrue(catalog.search("not-a-real-font-name").isEmpty())
    }
    @Test fun corruptCatalogAndEscapingPathsAreRejected() {
        assertTrue(runCatching { FontCatalog.parse(source().replace("# google/fonts\t", "bad")) }.isFailure)
        assertTrue(runCatching { FontCatalog.parse(source().replaceFirst("ofl/roboto/", "../../outside/")) }.isFailure)
        for (path in listOf("../x.ttf", "ofl/../bad.ttf", "ofl/x/a?token=x", "ofl/x/a#b", "ofl/x/%2e%2e/a.ttf", "https://evil/font.ttf")) {
            assertFalse(FontCatalog.safePath(path))
        }
    }
    @Test fun gitBlobIdentityAndLengthAreChecked() {
        val bytes = "hello".toByteArray()
        assertEquals("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0", FontBytes.blob(bytes))
        FontBytes.verify(bytes, 5, FontBytes.blob(bytes))
        assertTrue(runCatching { FontBytes.verify(bytes, 6, FontBytes.blob(bytes)) }.isFailure)
        assertTrue(runCatching { FontBytes.verify(bytes, 5, "0".repeat(40)) }.isFailure)
    }
    @Test fun streamLimitIncludesZeroReadFallbackAndUnknownLength() {
        val bytes = ByteArray(20) { it.toByte() }
        val stream = object : ByteArrayInputStream(bytes) {
            var first = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; return 0 }
                return super.read(b, off, minOf(len, 3))
            }
        }
        assertArrayEquals(bytes, FontBytes.readBounded(stream, 20))
        val endless = object : InputStream() { override fun read() = 1 }
        assertTrue(runCatching { FontBytes.readBounded(endless, 12) }.isFailure)
        assertTrue(runCatching { FontBytes.readBounded(ByteArrayInputStream(bytes), 19) }.isFailure)
    }
    @Test fun htmlWoffAndBrokenFontDirectoriesAreRejected() {
        for (bytes in listOf("<html>not a font</html>".toByteArray(), "wOF2".toByteArray() + ByteArray(40), byteArrayOf(0,1,0,0) + ByteArray(8))) {
            assertTrue(runCatching { FontBytes.requireSfnt(bytes) }.isFailure)
        }
        val minimal = ByteArray(28).also { it[1] = 1; it[5] = 1; it[23] = 28 }
        FontBytes.requireSfnt(minimal)
        assertTrue(runCatching { FontBytes.requireSfnt(minimal.clone().also { it[27] = 1 }) }.isFailure)
    }
    @Test fun downloadReaderCooperatesWithCancellation() {
        assertTrue(runCatching {
            FontBytes.readBounded(ByteArrayInputStream(ByteArray(100)), 100) { throw java.util.concurrent.CancellationException() }
        }.exceptionOrNull() is java.util.concurrent.CancellationException)
    }
    @Test fun everyFamilyHasAnOfflinePreviewOrDocumentedDigitException() {
        val catalog = FontCatalog.parse(source())
        val loader = javaClass.classLoader!!
        val exceptions = loader.getResourceAsStream("preview-exceptions.json")!!.bufferedReader().use { it.readText() }
        var totalBytes = 0
        for (font in catalog.fonts) {
            val stream = loader.getResourceAsStream("previews/${font.blob}.png")
            if (stream == null) {
                assertTrue("Missing preview: ${font.family}", exceptions.contains("\"${font.family}\""))
            } else {
                val bytes = stream.use { it.readBytes() }; totalBytes += bytes.size
                assertArrayEquals(byteArrayOf(-119,80,78,71,13,10,26,10), bytes.take(8).toByteArray())
                fun number(o: Int) = (0..3).fold(0) { v, i -> (v shl 8) or (bytes[o+i].toInt() and 255) }
                assertEquals(720, number(16)); assertEquals(96, number(20))
                assertNotNull("Preview license: ${font.family}", loader.getResource("preview-licenses/${font.licenseBlob}.txt"))
            }
        }
        assertTrue(totalBytes < 16 * 1024 * 1024)
    }

}
