package dev.citali.bolttstudio.fonts

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

data class DownloadedFont(val entry: CatalogFont, val file: File, val license: String)

class GoogleFontStore(context: Context) {
    private val root = File(context.filesDir, "google-fonts").apply { mkdirs() }
    fun downloaded(): Set<String> = root.listFiles()?.filter {
        it.name.matches(Regex("[a-f0-9]{40}")) && File(it, "font.ttf").isFile && File(it, "license.txt").isFile
    }?.map { it.name }?.toSet() ?: emptySet()
    fun clear() { root.listFiles()?.forEach { check(it.deleteRecursively()) { "Could not remove a downloaded font" } } }
    fun load(entry: CatalogFont): DownloadedFont {
        val dir = File(root, entry.blob)
        val font = File(dir, "font.ttf"); val license = File(dir, "license.txt")
        require(font.length() == entry.bytes.toLong() && license.length() == entry.licenseBytes.toLong()) { "Saved download is incomplete. Download it again." }
        val bytes = font.inputStream().use { FontBytes.readBounded(it, entry.bytes) }
        val licenseBytes = license.inputStream().use { FontBytes.readBounded(it, entry.licenseBytes) }
        FontBytes.verify(bytes, entry.bytes, entry.blob); FontBytes.requireSfnt(bytes)
        FontBytes.verify(licenseBytes, entry.licenseBytes, entry.licenseBlob)
        return DownloadedFont(entry, font, licenseBytes.toString(Charsets.UTF_8))
    }
    fun forget(entry: CatalogFont) { File(root, entry.blob).deleteRecursively() }
    suspend fun download(catalog: FontCatalog, entry: CatalogFont): DownloadedFont = withContext(Dispatchers.IO) {
        val jobContext = coroutineContext
        val started = System.nanoTime()
        fun checkActive() {
            jobContext.ensureActive()
            if ((System.nanoTime() - started) / 1_000_000 > 45_000) throw IOException("Download timed out")
        }
        fun fetch(path: String, bytes: Int, blob: String): ByteArray {
            checkActive()
            val connection = URL(catalog.url(path)).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "BolttStudio-FontDownload")
            try {
                if (connection.responseCode != 200) throw IOException("Font server returned ${connection.responseCode}")
                val length = connection.contentLengthLong
                require(length == -1L || length == bytes.toLong()) { "Unexpected download length" }
                return connection.inputStream.use { FontBytes.readBounded(it, bytes, ::checkActive) }
                    .also { FontBytes.verify(it, bytes, blob) }
            } finally { connection.disconnect() }
        }
        val used = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        require(used + entry.bytes + entry.licenseBytes <= 64L * 1024 * 1024) { "Font storage is full. Clear downloads to make space; your active draft font is kept." }
        val font = fetch(entry.path, entry.bytes, entry.blob); FontBytes.requireSfnt(font)
        val license = fetch(entry.licensePath, entry.licenseBytes, entry.licenseBlob)
        checkActive()
        val temp = File(root, "pending-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            fun write(name: String, data: ByteArray) { FileOutputStream(File(temp, name)).use { it.write(data); it.fd.sync() } }
            write("font.ttf", font); write("license.txt", license)
            checkActive()
            val target = File(root, entry.blob)
            if (target.exists()) check(target.deleteRecursively())
            check(temp.renameTo(target)) { "Could not save downloaded font" }
            load(entry)
        } finally { temp.deleteRecursively() }
    }
    fun removeIncompleteDownloads() { root.listFiles()?.filter { it.name.startsWith("pending-") }?.forEach { it.deleteRecursively() } }
}
