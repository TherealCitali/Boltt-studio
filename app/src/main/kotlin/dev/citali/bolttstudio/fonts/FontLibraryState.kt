package dev.citali.bolttstudio.fonts

import android.app.Application
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.citali.bolttstudio.EditorState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FontLibraryState(application: Application) : AndroidViewModel(application) {
    private val store = GoogleFontStore(application)
    private val storeLock = Mutex()
    private val options = application.getSharedPreferences("font-library", 0)
    var autoPreviews by mutableStateOf(options.getBoolean("auto-previews", true)); private set
    fun setAutoPreviews(value: Boolean) { autoPreviews = value; options.edit().putBoolean("auto-previews", value).apply() }
    private fun cachePreview(entry: CatalogFont, face: Typeface) {
        cardFonts[entry.blob] = face
        recent.remove(entry.blob); recent.add(entry.blob)
        while (recent.size > 24) cardFonts.remove(recent.first().also { recent.remove(it) })
    }
    val cardFonts = mutableStateMapOf<String, Typeface>()
    val cardErrors = mutableStateMapOf<String, String>()
    private val recent = LinkedHashSet<String>()
    suspend fun previewVisible(entries: List<CatalogFont>) {
        val index = catalog ?: return
        for (entry in entries.take(8)) {
            currentCoroutineContext().ensureActive()
            if (busy || entry.blob in cardFonts || entry.blob in cardErrors) continue
            try {
                storeLock.withLock {
                    if (busy) return@withLock
                    val face = withContext(Dispatchers.IO) {
                        val data = if (entry.blob in store.downloaded()) store.load(entry) else store.download(index, entry)
                        Typeface.Builder(data.file).build() ?: error("Android could not open this font")
                    }
                    cachePreview(entry, face)
                    saved = saved + entry.blob
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (cardErrors.size >= 128) cardErrors.remove(cardErrors.keys.first())
                cardErrors[entry.blob] = "Preview unavailable · tap Retry (check connection or clear downloads)"
            }
        }
    }
    fun retryPreview(entry: CatalogFont) { cardErrors.remove(entry.blob); select(entry) }

    var catalog by mutableStateOf<FontCatalog?>(null); private set
    var saved by mutableStateOf<Set<String>>(emptySet()); private set
    var selected by mutableStateOf<DownloadedFont?>(null); private set
    var preview by mutableStateOf<Typeface?>(null); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("Loading the offline catalog…"); private set
    var query by mutableStateOf("")
    var category by mutableStateOf("All")
    var downloadedOnly by mutableStateOf(false)
    private var task: Job? = null
    init {
        task = viewModelScope.launch {
            busy = true
            try {
                val loaded = withContext(Dispatchers.IO) {
                    store.removeIncompleteDownloads()
                    application.assets.open("google-fonts/catalog.tsv").bufferedReader().use { FontCatalog.parse(it.readText()) } to store.downloaded()
                }
                catalog = loaded.first; saved = loaded.second; status = "Visible font previews load automatically. Saved fonts work offline."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Could not open the font catalog. Reopen this app to retry." }
            finally { busy = false }
        }
    }
    fun select(entry: CatalogFont) {
        if (busy) return
        val index = catalog ?: return
        val isSaved = entry.blob in saved
        selected = null; preview = null; busy = true
        status = if (isSaved) "Opening ${entry.family}…" else "Downloading ${entry.family} and its license…"
        task = viewModelScope.launch {
            try {
                val (data, face) = storeLock.withLock {
                    withContext(Dispatchers.IO) {
                        val data = if (entry.blob in store.downloaded()) store.load(entry) else store.download(index, entry)
                        data to (Typeface.Builder(data.file).build() ?: error("Android could not open this font"))
                    }
                }
                selected = data; preview = face; saved = saved + entry.blob
                cachePreview(entry, face); cardErrors.remove(entry.blob)
                status = "${entry.family} is ready. Review the digits, then choose Use font."
            } catch (cancelled: CancellationException) {
                status = "Download cancelled. Your editor font was not changed."; throw cancelled
            } catch (error: Exception) {
                storeLock.withLock { withContext(Dispatchers.IO) { store.forget(entry) } }; saved = saved - entry.blob
                status = if (isSaved) "Saved copy could not be opened. Download it again; your editor font was not changed."
                    else "Could not download this font. Check your connection and try again. ${error.message.orEmpty().take(180)}"
            } finally { busy = false }
        }
    }
    fun cancel() { task?.cancel() }
    fun useSelected(editor: EditorState) {
        val data = selected ?: return
        if (busy) return
        busy = true
        task = viewModelScope.launch {
            try {
                val bytes = storeLock.withLock { withContext(Dispatchers.IO) {
                    store.load(data.entry).file.inputStream().use { FontBytes.readBounded(it, data.entry.bytes) }
                } }
                editor.installFont(bytes, data.entry.family, data.license)
                status = "${data.entry.family} applied and saved to your draft."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Could not apply this font. Your previous editor font is kept." }
            finally { busy = false }
        }
    }
    fun clearDownloads() {
        if (busy) return
        setAutoPreviews(false)
        busy = true
        task = viewModelScope.launch {
            try {
                storeLock.withLock {
                    withContext(Dispatchers.IO) { store.clear() }
                    selected = null; preview = null; saved = emptySet()
                    cardFonts.clear(); cardErrors.clear(); recent.clear()
                }
                status = "Downloads cleared; automatic previews paused. The active draft font is kept separately."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Some downloads could not be removed. Try again." }
            finally { busy = false }
        }
    }
}
