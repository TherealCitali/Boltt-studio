package dev.citali.bolttstudio.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.EditorState
import dev.citali.bolttstudio.codec.DepthProbe
import dev.citali.bolttstudio.codec.MoyFace
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class DepthProbeState : ViewModel() {
    var useEditor by mutableStateOf(true)
    var includeForeground by mutableStateOf(true)
    var stripeY by mutableIntStateOf(139)
    var message by mutableStateOf("")
    var pendingExport: ByteArray? = null
}
private data class DepthRequest(val face: MoyFace.Face?, val digits: List<Int>, val overlay: Boolean, val stripeY: Int)
private data class PreparedDepth(val request: DepthRequest, val result: DepthProbe.Result?, val error: String? = null)

@Composable
fun DepthProbePanel(ready: Boolean, transferring: Boolean, editor: EditorState, frame: FaceRenderer.Frame,
    onChooseImage: () -> Unit, onEditStyle: () -> Unit, onUpload: (DepthProbe.Result, Boolean) -> Unit,
    state: DepthProbeState = viewModel()) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var writing by remember { mutableStateOf(false) }
    val request = DepthRequest(if (state.useEditor) frame.face else null,
        if (state.useEditor) editor.previewDigits else listOf(1, 0, 0, 9), state.includeForeground,
        if (state.useEditor) state.stripeY else 139)
    // Debounce drag/crop events. Never expose stale bytes when the selected design changes.
    val prepared by produceState<PreparedDepth?>(null, request) {
        delay(120)
        value = try {
            val result = withContext(Dispatchers.Default) {
                if (request.face != null) DepthProbe.fromEditor(request.face, request.digits, request.overlay, request.stripeY)
                else DepthProbe.create(request.overlay)
            }
            PreparedDepth(request, result)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { PreparedDepth(request, null, error.message ?: "Cannot encode design") }
    }
    val current = prepared?.takeIf { it.request == request }
    val result = current?.result
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = state.pendingExport; state.pendingExport = null
        if (uri != null && bytes != null) scope.launch {
            writing = true
            try {
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open output")).use { it.write(bytes) } }
                state.message = "Exported ${bytes.size} bytes. Inspect redraws and wake on the watch."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.message = "Could not export test file" }
            finally { writing = false }
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Depth lab · your photo & clock", style = MaterialTheme.typography.titleLarge)
            Text("Experimental — redraw/wake behavior not yet confirmed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Use selected photo & clock", Modifier.weight(1f))
                Switch(state.useEditor, { state.useEditor = it; state.message = "" }, enabled = !transferring && !writing)
            }
            Text(if (state.useEditor) "Shared with the main editor: photo crop, font, colors, sizes, placement and clock-alpha brush. Drag this preview to move the clock."
                else "Original solid-background diagnostic: fixed seven-segment digits at 10:09 in the simulation. Turn the switch on to edit your photo and clock.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Show test stripe", Modifier.weight(1f))
                Switch(state.includeForeground, { state.includeForeground = it }, enabled = !transferring && !writing)
            }
            if (state.useEditor) {
                OutlinedButton(enabled = !transferring && !writing, onClick = onChooseImage) { Text("Choose / replace photo") }
                Text("Shared photo crop")
                Text("Zoom · %.2f×".format(editor.zoom)); Slider(editor.zoom, { editor.zoom = it }, valueRange = 1f..3f)
                Text("Horizontal crop"); Slider(editor.panX, { editor.panX = it }, valueRange = -1f..1f)
                Text("Vertical crop"); Slider(editor.panY, { editor.panY = it }, valueRange = -1f..1f)
            }
            val simulated = remember(result) { result?.simulatedPreview?.let { Bitmap.createBitmap(it.pixels, 240, 296, Bitmap.Config.ARGB_8888).asImageBitmap() } }
            val shown = simulated ?: if (state.useEditor) frame.image.asImageBitmap() else null
            if (shown != null) Image(shown, "Depth preview: drag to move selected clock group",
                Modifier.width(240.dp).height(296.dp).align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .pointerInput(state.useEditor, editor.independent, editor.editHours, editor.snap) {
                        if (state.useEditor) {
                            var start = editor.design; var dx = 0f; var dy = 0f
                            detectDragGestures(onDragStart = { start = editor.design; dx = 0f; dy = 0f }) { change, delta ->
                                change.consume(); dx += delta.x * 240 / size.width; dy += delta.y * 296 / size.height
                                val step = if (editor.snap) 4 else 1
                                editor.design = start.move(editor.editHours, editor.independent,
                                    (dx / step).roundToInt() * step, (dy / step).roundToInt() * step)
                            }
                        }
                    })
            if (current == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Updating current design… Send/export waits for this preview to match.", style = MaterialTheme.typography.labelSmall)
            }
            current?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.useEditor) {
                Text("Preview · %02d:%02d (watch time stays live)".format(editor.previewMinute / 60, editor.previewMinute % 60), style = MaterialTheme.typography.labelSmall)
                if (state.includeForeground) {
                    Text("Stripe vertical position · ${state.stripeY} px")
                    Slider(state.stripeY.toFloat(), { state.stripeY = it.roundToInt() }, valueRange = 0f..278f)
                }
                HorizontalDivider()
                Text("Clock controls · size & movement", style = MaterialTheme.typography.titleMedium)
                ClockControls(editor)
                TextButton(onClick = onEditStyle) { Text("Font, colors & outline → Clock section") }
                Text("Use the pinned Brush button for clock transparency painting. A new crop does not move the brush mask; check alignment after recropping.", style = MaterialTheme.typography.bodySmall)
            }
            result?.let { encoded ->
                Text("Encoded file: ${encoded.bytes.size} bytes · four live digit positions")
                encoded.face.foreground?.let { layer -> Text("Stripe: ${layer.image.width}×${layer.image.height} at (${layer.position.x}, ${layer.position.y})", style = MaterialTheme.typography.bodySmall) }
                Button(enabled = ready && !transferring && !writing, modifier = Modifier.fillMaxWidth(), onClick = { onUpload(encoded, state.useEditor) }) {
                    Text(if (state.useEditor) "Send current photo depth test…" else "Send original diagnostic…")
                }
                TextButton(enabled = !transferring && !writing, onClick = {
                    state.pendingExport = encoded.bytes.clone()
                    runCatching { export.launch(if (state.useEditor) "Boltt-photo-depth-test.bin" else "Boltt-depth-diagnostic.bin") }
                        .onFailure { state.pendingExport = null; state.message = "Document picker unavailable" }
                }) { Text("Export this depth test .bin") }
            }
            if (!ready) Text("Tap the pinned Watch button to connect, then return to Depth.", style = MaterialTheme.typography.bodySmall)
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
            Text("The stripe is a test image, not a subject cutout. Turn it off for a photo-and-clock control. Inspect changing minutes and screen sleep/wake; one photo does not establish persistent layering. No AI segmentation or frozen-clock fallback is included.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
