package dev.citali.bolttstudio.fonts

import android.app.Application
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.citali.bolttstudio.EditorState
import kotlinx.coroutines.*

class FontLibraryState(application: Application) : AndroidViewModel(application) {
    private val store = GoogleFontStore(application)
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
                catalog = loaded.first; saved = loaded.second; status = "Search offline. Downloads require internet."
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
                val (data, face) = withContext(Dispatchers.IO) {
                    val data = if (isSaved) store.load(entry) else store.download(index, entry)
                    data to (Typeface.Builder(data.file).build() ?: error("Android could not open this font"))
                }
                selected = data; preview = face; saved = saved + entry.blob
                status = "${entry.family} is ready. Review the digits, then choose Use font."
            } catch (cancelled: CancellationException) {
                status = "Download cancelled. Your editor font was not changed."; throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.IO) { store.forget(entry) }; saved = saved - entry.blob
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
                val bytes = withContext(Dispatchers.IO) {
                    store.load(data.entry).file.inputStream().use { FontBytes.readBounded(it, data.entry.bytes) }
                }
                editor.installFont(bytes, data.entry.family, data.license)
                status = "${data.entry.family} applied and saved to your draft."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Could not apply this font. Your previous editor font is kept." }
            finally { busy = false }
        }
    }
    fun clearDownloads() {
        if (busy) return
        busy = true
        task = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.clear() }
                selected = null; preview = null; saved = emptySet()
                status = "Downloads cleared. The active draft font is kept separately."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Some downloads could not be removed. Try again." }
            finally { busy = false }
        }
    }
}
