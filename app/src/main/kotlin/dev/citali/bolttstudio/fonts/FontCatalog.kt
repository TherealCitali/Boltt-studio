package dev.citali.bolttstudio.fonts

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest

const val MAX_FONT_BYTES = 4 * 1024 * 1024
const val MAX_LICENSE_BYTES = 65536

data class CatalogFont(val family: String, val category: String, val path: String, val bytes: Int,
    val blob: String, val licensePath: String, val licenseBytes: Int, val licenseBlob: String)

data class FontCatalog(val revision: String, val fonts: List<CatalogFont>) {
    fun url(path: String): String {
        require(safePath(path))
        return URI("https", "raw.githubusercontent.com", "/google/fonts/$revision/$path", null).toASCIIString()
    }
    fun search(query: String, category: String = "All"): List<CatalogFont> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return fonts.filter { (category == "All" || it.category == category) && words.all { word -> it.family.contains(word, ignoreCase = true) } }
    }
    companion object {
        private val sha = Regex("[a-f0-9]{40}")
        fun safePath(path: String): Boolean = path.length <= 300 && path.split('/').let { parts ->
            parts.size >= 3 && parts[0] in listOf("ofl", "apache", "ufl") &&
                parts.all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c.isISOControl() || c in "\\?#%:" } }
        }
        fun parse(text: String): FontCatalog {
            require(text.length <= 1024 * 1024)
            val lines = text.lineSequence().toList()
            val revision = lines.first().removePrefix("# google/fonts\t")
            require(sha.matches(revision))
            val fonts = lines.drop(1).filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val p = line.split('\t'); require(p.size == 8)
                require(p[0].isNotBlank() && p[0].length <= 120 && p[0].none { it.isISOControl() })
                require(p[1] in listOf("Sans Serif", "Serif", "Display", "Handwriting", "Monospace"))
                require(safePath(p[2]) && p[2].endsWith(".ttf"))
                require(safePath(p[5]) && p[5].split('/').take(2) == p[2].split('/').take(2))
                require(sha.matches(p[4]) && sha.matches(p[7]))
                CatalogFont(p[0], p[1], p[2], p[3].toInt().also { require(it in 12..MAX_FONT_BYTES) }, p[4],
                    p[5], p[6].toInt().also { require(it in 1..MAX_LICENSE_BYTES) }, p[7])
            }
            require(fonts.isNotEmpty() && fonts.size <= 5000 && fonts.map { it.family }.distinct().size == fonts.size)
            return FontCatalog(revision, fonts)
        }
    }
}

/** Exact catalog size + Git blob identity, rather than accepting an HTML error as a font. */
object FontBytes {
    fun readBounded(input: InputStream, limit: Int, checkCancelled: () -> Unit = {}): ByteArray {
        require(limit in 1..MAX_FONT_BYTES)
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            checkCancelled()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) {
                val one = input.read(); if (one < 0) break
                require(output.size() < limit) { "Download is larger than expected" }; output.write(one)
            } else {
                require(n <= limit - output.size()) { "Download is larger than expected" }; output.write(buffer, 0, n)
            }
        }
        return output.toByteArray()
    }
    fun blob(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").run {
        update("blob ${bytes.size}\u0000".toByteArray(Charsets.US_ASCII)); digest(bytes).joinToString("") { "%02x".format(it) }
    }
    fun verify(bytes: ByteArray, expectedSize: Int, expectedBlob: String) {
        require(bytes.size == expectedSize && blob(bytes) == expectedBlob) { "Download did not match the bundled catalog" }
    }
    fun requireSfnt(bytes: ByteArray) {
        require(bytes.size in 12..MAX_FONT_BYTES)
        require(bytes.take(4).toByteArray().contentEquals(byteArrayOf(0, 1, 0, 0)) ||
            String(bytes, 0, 4, Charsets.US_ASCII) == "OTTO") { "Not a TTF / OTF font" }
        val tables = ((bytes[4].toInt() and 255) shl 8) or (bytes[5].toInt() and 255)
        require(tables in 1..256 && 12 + tables * 16 <= bytes.size) { "Invalid font table directory" }
        fun u32(o: Int): Long = (0..3).fold(0L) { v, i -> (v shl 8) or (bytes[o + i].toLong() and 255) }
        for (i in 0 until tables) {
            val at = 12 + i * 16; val offset = u32(at + 8); val length = u32(at + 12)
            require(offset <= bytes.size && length <= bytes.size - offset) { "Truncated font table" }
        }
    }
}
