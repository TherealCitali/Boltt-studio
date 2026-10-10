package dev.citali.bolttstudio.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.codec.ImportedFace
import kotlinx.coroutines.*

class ImportFaceState(application: Application) : AndroidViewModel(application) {
    var imported by mutableStateOf<ImportedFace?>(null); private set
    var name by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    fun report(message: String) { error = message }
    fun clear() { if (!busy) { imported = null; name = ""; error = null } }
    fun load(uri: Uri) {
        if (busy) return
        imported = null; name = ""; error = null; busy = true
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val title = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull()?.filter { !it.isISOControl() }?.take(120)?.takeIf { it.isNotBlank() } ?: "Imported watchface.bin"
                    val data = (resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open selected file")).use { ImportedFace.read(it) }
                    title to data
                }
                name = loaded.first; imported = loaded.second
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Cannot read selected face" }
            finally { busy = false }
        }
    }
}

@Composable
fun ImportFacePanel(canPick: Boolean, canSend: Boolean, onUpload: (String, ImportedFace) -> Unit,
    state: ImportFaceState = viewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.load(uri)
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Import .bin face", style = MaterialTheme.typography.titleLarge)
            Text("Select a custom-made digital face to send unchanged. This does not replace your editor design or import its layers for editing.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = canPick && !state.busy, onClick = {
                runCatching { picker.launch(arrayOf("*/*")) }.onFailure { state.report("Document picker unavailable") }
            }) { Text("Choose .bin file") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.imported?.let { data ->
                val thumbnail = remember(data) {
                    val image = data.face.preview
                    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                Text(state.name, style = MaterialTheme.typography.titleMedium)
                Image(thumbnail, "Thumbnail embedded in imported file; may not reflect actual watch rendering", Modifier.width(140.dp).height(163.dp))
                Text("${data.size} bytes · API 0x23 · 240×296 · ${data.face.digits.size} digit tables")
                Text("SHA-256: ${data.sha256}", style = MaterialTheme.typography.labelSmall)
                Text("Embedded thumbnail only. Structural validation does not establish watch compatibility or the firmware's file-size limit. Original file bytes will be sent, not re-encoded.", style = MaterialTheme.typography.bodySmall)
                if (data.face.digits.size > 2) Text("Warning: more than two digit tables. Some custom configurations may omit digits on your watch.", color = MaterialTheme.colorScheme.error)
                Button(enabled = canSend && !state.busy, modifier = Modifier.fillMaxWidth(), onClick = { onUpload(state.name, data) }) { Text("Send imported face…") }
                TextButton(enabled = canPick && !state.busy, onClick = state::clear) { Text("Remove imported file") }
                if (!canSend) Text("Connect your watch above and finish any active operation to enable sending.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Binary .bin only; maximum 2 MiB for this app. Unknown analogue/other element formats are rejected. Selection survives rotation, not process termination.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
