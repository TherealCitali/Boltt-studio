@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.citali.bolttstudio

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import dev.citali.bolttstudio.ui.StudioTheme
import dev.citali.bolttstudio.ui.StudioWorkspace
import dev.citali.bolttstudio.ui.SettingsDestination
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModelProvider
import dev.citali.bolttstudio.bluetooth.WatchViewModel
import dev.citali.bolttstudio.ui.WatchPanel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.codec.MoyFace
import dev.citali.bolttstudio.ui.FaceRenderer
import dev.citali.bolttstudio.ui.ClockControls
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var watch: WatchViewModel
    override fun onStop() {
        super.onStop()
        if (::watch.isInitialized && !isChangingConfigurations) watch.backgrounded()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        watch = ViewModelProvider(this)[WatchViewModel::class.java]
        setContent {
            StudioTheme { Studio(watch = watch) }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Studio(state: EditorState = viewModel(), watch: WatchViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val watchUi by watch.ui.collectAsState()
    val view = LocalView.current
    DisposableEffect(watchUi.transferring) {
        val previous = view.keepScreenOn
        view.keepScreenOn = watchUi.transferring
        onDispose { view.keepScreenOn = previous }
    }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    if (state.loading) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Restoring your studio…", Modifier.padding(16.dp))
                }
            }
        }
        return
    }
    val frame = remember(state.background, state.design, state.family, state.hours, state.minutes,
        state.outline, state.zoom, state.panX, state.panY, state.customTypeface, state.useCustomFont,
        state.previewMinute) {
        FaceRenderer.render(state.background, 0, 0, true, state.family,
            state.hours, state.minutes, state.outline, state.zoom, state.panX, state.panY,
            if (state.useCustomFont) state.customTypeface else null, state.design, state.previewDigits)
    }
    // Capture export bytes before launching SAF so edits cannot silently change the pending export.
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = state.pendingExport; state.pendingExport = null
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
                    val decoded = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Unsupported image")
                    val orientation = runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        }
                    }.getOrNull()
                    val matrix = Matrix().apply {
                        when (orientation) {
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                        }
                    }
                    if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                }
                state.installPhoto(bitmap); message = "Photo saved to your draft. Adjust its crop here."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not load this image." }
            finally { busy = false }
        }
    }
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val fontBytes = withContext(Dispatchers.IO) {
                    val bytes = (context.contentResolver.openInputStream(uri) ?: error("Cannot open font")).use { stream ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buffer); if (n < 0) break
                            require(output.size() + n <= 4 * 1024 * 1024) { "Font exceeds 4 MiB" }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size >= 12 && (bytes.take(4).toByteArray().contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        String(bytes, 0, 4, Charsets.US_ASCII) == "OTTO")) { "Choose a valid TTF or OTF font" }
                    bytes
                }
                state.installFont(fontBytes)
                message = "Font loaded. Check every digit before upload; font licensing remains your responsibility."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Could not load font" }
            finally { busy = false }
        }
    }
    var page by rememberSaveable { mutableStateOf("Design") }
    BackHandler(enabled = page != "Design") { page = "Design" }
    StudioWorkspace(state, frame.image, page, page == "Design",
        if (watchUi.transferring) "${(watchUi.progress * 100).roundToInt()}%" else "Watch",
        onBack = { page = "Design" }, onWatch = { page = "Watch" }) {
        key(page) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    "Design" -> {
                        Text("Make time\nyour own.", style = MaterialTheme.typography.headlineMedium)
                        Text("Your draft stays on this device, including your photo and imported font.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsDestination("01", "Photo & crop", "Background, zoom and framing") { page = "Photo" }
                        SettingsDestination("02", "Clock layout", "Size, spacing and independent placement") { page = "Clock" }
                        SettingsDestination("03", "Type & color", "Fonts, custom colors and outline") { page = "Style" }
                        SettingsDestination("04", "Preview & export", "Preview time and a ready-to-send .bin") { page = "Export" }
                        TextButton(onClick = { page = "About" }) { Text("About & local draft") }
                    }
                    "Photo" -> {
                        Text("Set the scene.", style = MaterialTheme.typography.headlineSmall)
                        Button(enabled = !busy && !watchUi.transferring, onClick = {
                            runCatching { importImage.launch(arrayOf("image/*")) }.onFailure { message = "No document picker available." }
                        }) { Text("Choose image") }
                        Text("An optimized copy is saved privately, so your draft does not depend on the original file remaining available.", style = MaterialTheme.typography.bodySmall)
                        Control("Zoom", state.zoom, 1f..3f) { state.zoom = it }
                        Control("Horizontal crop", state.panX, -1f..1f) { state.panX = it }
                        Control("Vertical crop", state.panY, -1f..1f) { state.panY = it }
                        TextButton(onClick = { state.zoom = 1f; state.panX = 0f; state.panY = 0f }) { Text("Reset crop") }
                    }
                    "Clock" -> {
                        Text("Find your balance.", style = MaterialTheme.typography.headlineSmall)
                        ClockControls(state)
                    }
                    "Style" -> {
                        Text("Give time character.", style = MaterialTheme.typography.headlineSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono").forEach { (font, label) ->
                                FilterChip(selected = !state.useCustomFont && state.family == font,
                                    onClick = { state.family = font; state.useCustomFont = false }, label = { Text(label) })
                            }
                        }
                        OutlinedButton(enabled = !busy && !watchUi.transferring, onClick = {
                            runCatching { importFont.launch(arrayOf("*/*")) }.onFailure { message = "No document picker available" }
                        }) { Text("Import TTF / OTF") }
                        if (state.customTypeface != null) FilterChip(selected = state.useCustomFont,
                            onClick = { state.useCustomFont = true }, label = { Text("Use saved custom font") })
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Dark outline", Modifier.weight(1f)); Switch(state.outline, { state.outline = it })
                        }
                        HorizontalDivider()
                        ColorPicker("Hours", state.hours) { state.hours = it }
                        HorizontalDivider()
                        ColorPicker("Minutes", state.minutes) { state.minutes = it }
                    }
                    "Export" -> {
                        Text("Ready when you are.", style = MaterialTheme.typography.headlineSmall)
                        Text("Preview · %02d:%02d".format(state.previewMinute / 60, state.previewMinute % 60), style = MaterialTheme.typography.titleMedium)
                        Slider(state.previewMinute.toFloat(), { state.previewMinute = it.roundToInt() }, valueRange = 0f..1439f)
                        Text("This is just a preview. The watch still displays its live time.", style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !busy && !watchUi.transferring, modifier = Modifier.fillMaxWidth(), onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val snapshot = frame.face
                                    state.pendingExport = withContext(Dispatchers.Default) { MoyFace.build(snapshot) }
                                    export.launch("Boltt-Brillia-face.bin")
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { state.pendingExport = null; message = "Could not prepare export." }
                                finally { busy = false }
                            }
                        }) { Text("Export watchface .bin") }
                        FilledTonalButton(onClick = { page = "Watch" }, modifier = Modifier.fillMaxWidth()) { Text("Connect & send to watch") }
                    }
                    "Watch" -> WatchPanel(watch, frame.face)
                    "About" -> {
                        Text("A studio, not a session.", style = MaterialTheme.typography.headlineSmall)
                        Text("Photo, crop, clock geometry, fonts, colors, outline, snapping and preview time are restored when you reopen the app. One local draft is kept. Clearing app data or uninstalling deletes it. Edits lost before this version cannot be recovered.")
                        Text("Private storage only. No cloud sync. Bluetooth connections, confirmations and pending uploads are never restored automatically.")
                        Text("Keep the app open until watch uploads finish. The BLE protocol is experimental and firmware-specific.")
                        Text("UI inspired by LunarTune by cognitiveshadows03. Elastic scrolling uses Miuix, as in ShadowRPC. Montserrat UI font: SIL Open Font License. Miuix: Apache-2.0. Licenses are bundled in the APK.", style = MaterialTheme.typography.bodySmall)
                        Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT}\n${BuildConfig.BUILD_DATE}\nIndependent GPL-3.0 project; not an official Fire-Boltt or Da Fit app.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.storageNotice.isNotBlank()) Text(state.storageNotice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
@Composable private fun Control(label: String, value: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
    Text(label); Slider(value = value.coerceIn(range), onValueChange = update, valueRange = range)
}
@Composable private fun ColorPicker(label: String, selected: Int, update: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var hex by remember(selected) { mutableStateOf("%06X".format(selected and 0xffffff)) }
    Text(label, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("White" to 0xffffffff.toInt(), "Orange" to 0xffff7a2f.toInt(), "Mint" to 0xff73e2bb.toInt()).forEach { (name, color) ->
            FilterChip(selected = selected == color, onClick = { update(color) }, label = { Text(name) })
        }
    }
    OutlinedButton(onClick = { open = !open }) { Text(if (open) "Close custom color" else "Custom · #%06X".format(selected and 0xffffff)) }
    if (open) {
        val rgb = hex.takeIf { it.length == 6 }?.toIntOrNull(16)
        Surface(color = Color(selected), modifier = Modifier.fillMaxWidth().height(32.dp), shape = MaterialTheme.shapes.medium) {}
        OutlinedTextField(value = hex, onValueChange = { value ->
            hex = value.removePrefix("#").filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase()
        }, label = { Text("Hex RGB · 6 digits") }, singleLine = true, isError = rgb == null, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = rgb != null, onClick = { update(0xff000000.toInt() or (rgb ?: 0)) }) { Text("Apply hex") }
        listOf("Red" to 16, "Green" to 8, "Blue" to 0).forEach { (name, shift) ->
            val component = (selected ushr shift) and 255
            Text("$name · $component")
            Slider(value = component.toFloat(), valueRange = 0f..255f, onValueChange = {
                update((selected and (255 shl shift).inv()) or (it.roundToInt() shl shift))
            })
        }
        Text("Watch colors are quantized to RGB565.", style = MaterialTheme.typography.bodySmall)
    }
}
