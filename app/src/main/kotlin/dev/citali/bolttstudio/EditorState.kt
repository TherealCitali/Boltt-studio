package dev.citali.bolttstudio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.citali.bolttstudio.data.DraftSettings
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.*

/** Preferences are queued on every edit (and flushed by Android at lifecycle transitions).
 * Media is fully written and synced before publishing a new immutable filename in metadata. */
class EditorState(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("editor-draft", 0)
    private val restored = runCatching { prefs.getString("draft", null)?.let(DraftSettings::decode) }
    private val initial = restored.getOrNull() ?: DraftSettings()
    private val directory = File(application.filesDir, "editor-draft").apply { mkdirs() }
    private var savingEnabled = false
    var loading by mutableStateOf(true); private set
    var storageNotice by mutableStateOf(""); private set
    var background by mutableStateOf<Bitmap?>(null); private set
    var customTypeface by mutableStateOf<Typeface?>(null); private set
    private var photoName by saved(initial.photo)
    private var fontName by saved(initial.font)
    var fontLabel by saved(initial.fontLabel); private set
    var fontLicense by mutableStateOf<String?>(null); private set
    var design by saved(initial.design)
    var stacked by saved(initial.stacked); private set
    fun arrangeClock(stacked: Boolean) {
        savingEnabled = false
        this.stacked = stacked; design = design.arrange(stacked)
        savingEnabled = true; persist()
    }
    var independent by saved(initial.independent)
    var editHours by saved(initial.editHours)
    var previewMinute by saved(initial.previewMinute)
    val previewDigits get() = listOf(previewMinute / 60 / 10, previewMinute / 60 % 10, previewMinute % 60 / 10, previewMinute % 10)
    var outline by saved(initial.outline)
    var family by saved(initial.family)
    var useCustomFont by saved(initial.useCustomFont)
    var snap by saved(initial.snap)
    var hours by saved(initial.hours)
    var minutes by saved(initial.minutes)
    var zoom by saved(initial.zoom)
    var panX by saved(initial.panX)
    var panY by saved(initial.panY)
    var amoledBlack by saved(initial.amoledBlack)
    var blackCutoff by saved(initial.blackCutoff)
    var pendingExport: ByteArray? = null

    private fun <T> saved(initial: T) = object : ReadWriteProperty<Any?, T> {
        private var value by mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (this.value != value) { this.value = value; if (savingEnabled) persist() }
        }
    }
    private fun persist() {
        val data = DraftSettings(design, independent, editHours, previewMinute, outline, family,
            useCustomFont, snap, hours, minutes, zoom, panX, panY, photoName, fontName, fontLabel, stacked, amoledBlack, blackCutoff)
        prefs.edit().putString("draft", data.encode()).apply()
    }
    init {
        viewModelScope.launch {
            val restoredAssets = withContext(Dispatchers.IO) {
                // Only cleanup on launch, using the committed metadata. No active import can race this.
                if (restored.isSuccess) directory.listFiles()?.filter { it.name != initial.photo && it.name != initial.font && it.name != initial.font + ".license" }?.forEach { it.delete() }
                val photo = if (initial.photo.isNotEmpty()) runCatching {
                    val file = File(directory, initial.photo)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    require(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
                    BitmapFactory.decodeFile(file.path) ?: error("Cannot decode saved photo")
                }.getOrNull() else null
                val font = if (initial.font.isNotEmpty()) runCatching {
                    val file = File(directory, initial.font); require(file.length() in 12..4L * 1024 * 1024)
                    Typeface.Builder(file).build()
                }.getOrNull() else null
                Triple(photo ?: application.assets.open("sample_bg.png").use { BitmapFactory.decodeStream(it) }, font, photo != null)
            }
            background = restoredAssets.first; customTypeface = restoredAssets.second
            fontLicense = withContext(Dispatchers.IO) {
                runCatching { File(directory, initial.font + ".license").takeIf { initial.font.isNotEmpty() && it.length() in 1..65536 }?.readText() }.getOrNull()
            }
            if (restored.isFailure) storageNotice = "Saved settings could not be restored. Defaults are shown; stored media has been kept."
            if (initial.photo.isNotEmpty() && !restoredAssets.third) {
                photoName = ""; zoom = 1f; panX = 0f; panY = 0f
                storageNotice = "Saved photo is unavailable. The sample is shown; choose your photo again."
            }
            if (initial.font.isNotEmpty() && customTypeface == null) {
                fontName = ""; useCustomFont = false
                storageNotice = "Saved font is unavailable. A system font is shown; import your font again."
            }
            savingEnabled = true; loading = false
        }
    }
    suspend fun installPhoto(bitmap: Bitmap) {
        val name = withContext(Dispatchers.IO) {
            val file = File(directory, "${UUID.randomUUID()}.png")
            try {
                FileOutputStream(file).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.fd.sync()
                }
                file.name
            } catch (e: Exception) { file.delete(); throw e }
        }
        background = bitmap; photoName = name
        zoom = 1f; panX = 0f; panY = 0f
        storageNotice = ""
    }
    suspend fun installFont(bytes: ByteArray, label: String = "Imported font", licenseText: String? = null) {
        require(bytes.size in 12..4 * 1024 * 1024)
        require(licenseText == null || licenseText.toByteArray(Charsets.UTF_8).size <= 65536)
        val (name, font) = withContext(Dispatchers.IO) {
            val file = File(directory, "${UUID.randomUUID()}.font")
            try {
                FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
                if (licenseText != null) FileOutputStream(File(directory, file.name + ".license")).use {
                    it.write(licenseText.toByteArray(Charsets.UTF_8)); it.fd.sync()
                }
                file.name to (Typeface.Builder(file).build() ?: error("Invalid font"))
            } catch (e: Exception) { file.delete(); throw e }
        }
        // Publish file reference, display name and selected state together after all disk writes succeed.
        savingEnabled = false
        customTypeface = font; fontName = name; fontLabel = label.filterNot { it.isISOControl() }.take(120)
        fontLicense = licenseText; useCustomFont = true; storageNotice = ""
        savingEnabled = true; persist()
    }
}
