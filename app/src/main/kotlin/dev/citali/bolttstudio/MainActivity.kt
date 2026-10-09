package dev.citali.bolttstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.codec.MoyFace
import dev.citali.bolttstudio.ui.FaceRenderer
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFFFAB77))
                else lightColorScheme(primary = Color(0xFF9B4100))
            MaterialTheme(colorScheme = colors) { Studio() }
        }
    }
}
class EditorState : ViewModel() {
    var background by mutableStateOf<Bitmap?>(null)
    var sampleLoaded = false
    var x by mutableFloatStateOf(74f); var y by mutableFloatStateOf(41f)
    var stacked by mutableStateOf(true); var outline by mutableStateOf(true)
    var family by mutableStateOf("sans-serif")
    var hours by mutableIntStateOf(android.graphics.Color.WHITE)
    var minutes by mutableIntStateOf(android.graphics.Color.WHITE)
    var zoom by mutableFloatStateOf(1f); var panX by mutableFloatStateOf(0f); var panY by mutableFloatStateOf(0f)
}

@Composable
private fun Studio(state: EditorState = viewModel()) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Editor ready. Preview time: 10:09") }
    var busy by remember { mutableStateOf(false) }
    val frame = remember(state.background, state.x, state.y, state.stacked, state.family, state.hours, state.minutes,
        state.outline, state.zoom, state.panX, state.panY) {
        FaceRenderer.render(state.background, state.x.toInt(), state.y.toInt(), state.stacked, state.family,
            state.hours, state.minutes, state.outline, state.zoom, state.panX, state.panY)
    }
    // Capture export bytes before launching SAF so edits cannot silently change the pending export.
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingExport; pendingExport = null
        if (uri != null && bytes != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open output")).use { it.write(bytes) } }
                message = "Exported ${bytes.size} bytes. Watch compatibility still requires hardware testing."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Export failed. Check the chosen storage location." }
            finally { busy = false }
        }
    }
    val importImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768) { "Image too large" }
                    var sample = 1
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Unsupported image")
                }
                state.background = bitmap; message = "Background loaded; adjust crop below."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not load this image." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        if (!state.sampleLoaded) {
            state.sampleLoaded = true
            state.background = withContext(Dispatchers.IO) { context.assets.open("sample_bg.png").use { BitmapFactory.decodeStream(it) } }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Boltt Studio") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("BRILLIA / NATIVE PREVIEW", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Make time your own.", style = MaterialTheme.typography.headlineLarge)
            Image(frame.image.asImageBitmap(), "Watchface preview showing 10:09", Modifier.fillMaxWidth().height(296.dp))
            Text("240 × 296 · API 0x23 · 42 × 66 digit cells", style = MaterialTheme.typography.bodySmall)
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background", style = MaterialTheme.typography.titleLarge)
                Button(enabled = !busy, onClick = { runCatching { importImage.launch(arrayOf("image/*")) }.onFailure { message = "No document picker available." } }) { Text("Choose image") }
                Control("Zoom", state.zoom, 1f..3f) { state.zoom = it }
                Control("Horizontal crop", state.panX, -1f..1f) { state.panX = it }
                Control("Vertical crop", state.panY, -1f..1f) { state.panY = it }
            } }
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clock", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Stack hours above minutes", Modifier.weight(1f)); Switch(state.stacked, { state.stacked = it; state.x = state.x.coerceAtMost(if (it) 148f else 37f); state.y = state.y.coerceAtMost(if (it) 150f else 230f) })
                }
                Control("Position X", state.x, 0f..(if (state.stacked) 148f else 37f)) { state.x = it }
                Control("Position Y", state.y, 0f..(if (state.stacked) 150f else 230f)) { state.y = it }
                TextButton(onClick = { state.x = if (state.stacked) 74f else 18f; state.y = if (state.stacked) 75f else 115f }) { Text("Center clock") }
                Row { listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono").forEach { (font, label) ->
                    FilterChip(selected = state.family == font, onClick = { state.family = font }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp))
                } }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Dark outline", Modifier.weight(1f)); Switch(state.outline, { state.outline = it })
                }
                ColorPicker("Hours", state.hours) { state.hours = it }
                ColorPicker("Minutes", state.minutes) { state.minutes = it }
            } }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        val snapshot = frame.face
                        pendingExport = withContext(Dispatchers.Default) { MoyFace.build(snapshot) }
                        export.launch("Boltt-Brillia-face.bin")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { pendingExport = null; message = "Could not prepare export." }
                    finally { busy = false }
                }
            }) { Text("Export watchface .bin") }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("Bluetooth upload is not included in this native milestone. Use the retained web prototype only if you accept its experimental hardware risks. This app does not connect to or modify your watch.", style = MaterialTheme.typography.bodySmall)
            Text("Session edits survive rotation, but are not yet saved across process termination. Custom font import, drag/snap, and full .bin import are still pending.", style = MaterialTheme.typography.bodySmall)
            Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT}\n${BuildConfig.BUILD_DATE}\nIndependent GPL-3.0 project; not an official Fire-Boltt or Da Fit app.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun Control(label: String, value: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
    Text(label); Slider(value = value.coerceIn(range), onValueChange = update, valueRange = range)
}
@Composable private fun ColorPicker(label: String, selected: Int, update: (Int) -> Unit) {
    Text(label)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("White" to 0xffffffff.toInt(), "Orange" to 0xffff7a2f.toInt(), "Mint" to 0xff73e2bb.toInt()).forEach { (name, color) ->
            FilterChip(selected = selected == color, onClick = { update(color) }, label = { Text(name) })
        }
    }
}
