package dev.citali.bolttstudio.codec

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** A validated upload snapshot, never reconstructed by the face encoder. */
class ImportedFace private constructor(private val original: ByteArray, val face: MoyFace.Face) {
    val size get() = original.size
    val sha256 = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun uploadBytes() = original.clone()
    companion object {
        fun read(stream: InputStream): ImportedFace {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val next = stream.read(); if (next < 0) break
                    require(out.size() < MoyFace.MAX_FILE_BYTES) { "File exceeds the 2 MiB application limit" }
                    out.write(next)
                } else {
                    require(out.size() + count <= MoyFace.MAX_FILE_BYTES) { "File exceeds the 2 MiB application limit" }
                    out.write(buffer, 0, count)
                }
            }
            val bytes = out.toByteArray()
            require(bytes.size >= 18 && bytes[0] == 0x23.toByte() && bytes[1] == 0.toByte()) {
                "Not a supported API 0x23 binary face. Select a decoded .bin, not Base64 text."
            }
            val face = try { MoyFace.parse(bytes) } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Unsupported or malformed digital face. Analogue/other element formats are not supported yet.", error)
            } catch (error: IllegalStateException) {
                throw IllegalArgumentException("Unsupported face elements. Analogue/other element formats are not supported yet.", error)
            }
            return ImportedFace(bytes, face)
        }
    }
}
