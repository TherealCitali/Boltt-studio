package dev.citali.bolttstudio.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.codec.DepthProbe
import kotlinx.coroutines.*

class DepthProbeState : ViewModel() {
    var result by mutableStateOf<DepthProbe.Result?>(null)
    var includeForeground by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var pendingExport: ByteArray? = null
    fun prepare() {
        if (busy) return
        val overlay = includeForeground
        busy = true
        viewModelScope.launch {
            try {
                result = withContext(Dispatchers.Default) { DepthProbe.create(overlay) }
                message = "Prepared. Preview is a simulation, not evidence of firmware layering."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = "Cannot prepare test: ${error.message}" }
            finally { busy = false }
        }
    }
}

@Composable
fun DepthProbePanel(ready: Boolean, transferring: Boolean, onUpload: (DepthProbe.Result) -> Unit,
    state: DepthProbeState = viewModel()) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = state.pendingExport; state.pendingExport = null
        if (uri != null && bytes != null) scope.launch {
            state.busy = true
            try {
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open output")).use { it.write(bytes) }
                }
                state.message = "Exported ${bytes.size} bytes. Firmware layering is still unverified."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.message = "Could not export test file" }
            finally { state.busy = false }
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Depth lab · firmware probe", style = MaterialTheme.typography.titleLarge)
            Text("Depth support: UNVERIFIED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Before photo cutouts: test whether this firmware keeps a transparent image in front of four LIVE digits, including after redraws. This does not modify your editor design.")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Include foreground stripe", Modifier.weight(1f))
                Switch(state.includeForeground, {
                    state.includeForeground = it; state.result = null; state.message = ""
                }, enabled = !state.busy && !transferring)
            }
            Text(if (state.includeForeground) "Magenta stripe: opaque center, 25% / 50% alpha edges, and a fully clear window. Image element is AFTER live time."
                else "Control test: same background and four live digits, without the extra image element.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !state.busy && !transferring, onClick = state::prepare) { Text("Prepare test face") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.result?.let { result ->
                val bitmap = remember(result) {
                    Bitmap.createBitmap(result.simulatedPreview.pixels, 240, 296, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                Image(bitmap, "Simulated depth test at 10:09; watch rendering is unverified",
                    Modifier.width(240.dp).height(296.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                Text("Simulation · 10:09, not the watch's current time", style = MaterialTheme.typography.labelSmall)
                Text("Encoded file: ${result.bytes.size} bytes · all 4 time positions are live")
                result.face.foreground?.let { layer ->
                    Text("Cropped foreground: ${layer.image.width}×${layer.image.height} at (${layer.position.x}, ${layer.position.y})", style = MaterialTheme.typography.bodySmall)
                }
                Text("On the watch: inspect initially, across at least two minute changes, then after screen sleep/wake. Does the stripe stay in front? Are the clear window and translucent edges correct? Check all four digits (an hour rollover is needed to observe hour updates).", style = MaterialTheme.typography.bodySmall)
                Button(enabled = ready && !state.busy && !transferring, modifier = Modifier.fillMaxWidth(), onClick = { onUpload(result) }) {
                    Text(if (result.face.foreground != null) "Send depth probe…" else "Send no-overlay control…")
                }
                TextButton(enabled = !state.busy && !transferring, onClick = {
                    state.pendingExport = result.bytes.clone()
                    runCatching { export.launch(if (result.face.foreground != null) "Boltt-depth-probe-overlay.bin" else "Boltt-depth-probe-control.bin") }
                        .onFailure { state.pendingExport = null; state.message = "Document picker unavailable" }
                }) { Text("Export test .bin") }
                if (!ready) Text("Connect your watch above to enable sending.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            Text("Upload completion is not a depth-test pass. If redraws break occlusion or image blending fails, genuine depth remains unsupported; no frozen-clock screenshot fallback. AI segmentation, mask brushes and photo-depth export are not enabled in this build.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
