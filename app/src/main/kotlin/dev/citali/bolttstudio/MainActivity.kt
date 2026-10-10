package dev.citali.bolttstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import dev.citali.bolttstudio.ui.ClockControls
import dev.citali.bolttstudio.ui.ClockBrushPanel
import dev.citali.bolttstudio.codec.ClockDesign
import dev.citali.bolttstudio.codec.ClockMask
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
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFFFAB77))
                else lightColorScheme(primary = Color(0xFF9B4100))
            MaterialTheme(colorScheme = colors) { Studio(watch = watch) }
        }
    }
}
class EditorState : ViewModel() {
    var background by mutableStateOf<Bitmap?>(null)
    var sampleLoaded = false
    var x by mutableFloatStateOf(74f); var y by mutableFloatStateOf(41f)
    var design by mutableStateOf(ClockDesign())
    var independent by mutableStateOf(false)
    var editHours by mutableStateOf(true)
    var maskEnabled by mutableStateOf(false)
    var mask by mutableStateOf(ClockMask.empty())
    var previewMinute by mutableIntStateOf(609)
    val previewDigits get() = listOf(previewMinute / 60 / 10, previewMinute / 60 % 10, previewMinute % 60 / 10, previewMinute % 10)
    val undoMasks = ArrayDeque<ByteArray>()
    var undoCount by mutableIntStateOf(0)
    fun checkpointMask() {
        if (undoMasks.size == 20) undoMasks.removeFirst()
        undoMasks.addLast(mask.clone()); undoCount = undoMasks.size
    }
    fun undoMask() { if (undoMasks.isNotEmpty()) mask = undoMasks.removeLast(); undoCount = undoMasks.size }
    var stacked by mutableStateOf(true); var outline by mutableStateOf(true)
    var family by mutableStateOf("sans-serif")
    var customTypeface by mutableStateOf<Typeface?>(null)
    var useCustomFont by mutableStateOf(false)
    var snap by mutableStateOf(false)
    var pendingExport: ByteArray? = null
    var hours by mutableIntStateOf(android.graphics.Color.WHITE)
    var minutes by mutableIntStateOf(android.graphics.Color.WHITE)
    var zoom by mutableFloatStateOf(1f); var panX by mutableFloatStateOf(0f); var panY by mutableFloatStateOf(0f)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Studio(state: EditorState = viewModel(), watch: WatchViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val photoAnchor = remember { BringIntoViewRequester() }
    val clockAnchor = remember { BringIntoViewRequester() }
    val styleAnchor = remember { BringIntoViewRequester() }
    val brushAnchor = remember { BringIntoViewRequester() }
    val watchAnchor = remember { BringIntoViewRequester() }
    val depthAnchor = remember { BringIntoViewRequester() }
    fun jump(anchor: BringIntoViewRequester) { scope.launch { anchor.bringIntoView() } }
    val watchUi by watch.ui.collectAsState()
    val view = LocalView.current
    DisposableEffect(watchUi.transferring) {
        val previous = view.keepScreenOn
        view.keepScreenOn = watchUi.transferring
        onDispose { view.keepScreenOn = previous }
    }
    var message by remember { mutableStateOf("Editor ready. Preview time can be changed in Depth lab.") }
    var busy by remember { mutableStateOf(false) }
    val frame = remember(state.background, state.design, state.family, state.hours, state.minutes,
        state.outline, state.zoom, state.panX, state.panY, state.customTypeface, state.useCustomFont,
        state.mask, state.maskEnabled, state.previewMinute) {
        FaceRenderer.render(state.background, 0, 0, state.stacked, state.family,
            state.hours, state.minutes, state.outline, state.zoom, state.panX, state.panY,
            if (state.useCustomFont) state.customTypeface else null, state.design,
            if (state.maskEnabled) state.mask else null, state.previewDigits)
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
                state.background = bitmap; state.zoom = 1f; state.panX = 0f; state.panY = 0f; message = "Background loaded; adjust crop below."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not load this image." }
            finally { busy = false }
        }
    }
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val font = withContext(Dispatchers.IO) {
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
                    val file = File.createTempFile("boltt-font-", ".font", context.cacheDir)
                    try { file.writeBytes(bytes); Typeface.Builder(file).build() ?: error("Invalid font") }
                    finally { file.delete() }
                }
                state.customTypeface = font; state.useCustomFont = true
                message = "Font loaded. Check every digit before upload; font licensing remains your responsibility."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Could not load font" }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        if (!state.sampleLoaded) {
            val sample = withContext(Dispatchers.IO) { context.assets.open("sample_bg.png").use { BitmapFactory.decodeStream(it) } }
            if (state.background == null) state.background = sample
            state.sampleLoaded = true
        }
    }
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("Boltt Studio · ${BuildConfig.VERSION_NAME}") })
            Row(Modifier.fillMaxWidth()) {
                listOf("Photo" to photoAnchor, "Clock" to clockAnchor, "Brush" to brushAnchor,
                    "Watch" to watchAnchor, "Depth" to depthAnchor).forEach { (label, anchor) ->
                    TextButton(onClick = { jump(anchor) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("BRILLIA / NATIVE PREVIEW", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Make time your own.", style = MaterialTheme.typography.headlineLarge)
            Image(frame.image.asImageBitmap(), "Watchface preview. Drag to move the selected clock group.",
                Modifier.width(240.dp).height(296.dp).align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .pointerInput(state.independent, state.editHours, state.snap) {
                        var dx = 0f; var dy = 0f; var start = state.design
                        detectDragGestures(onDragStart = { dx = 0f; dy = 0f; start = state.design }) { change, delta ->
                            change.consume()
                            dx += delta.x * 240f / size.width; dy += delta.y * 296f / size.height
                            val step = if (state.snap) 4 else 1
                            state.design = start.move(state.editHours, state.independent,
                                (dx / step).roundToInt() * step, (dy / step).roundToInt() * step)
                        }
                    })
            Text(if (state.independent) "Drag to move selected ${if (state.editHours) "hours" else "minutes"}." else "Drag to move the whole clock.", style = MaterialTheme.typography.labelMedium)
            Text("240 × 296 · API 0x23 · live, variable-size digits", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(photoAnchor))
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background", style = MaterialTheme.typography.titleLarge)
                Button(enabled = !busy && !watchUi.transferring, onClick = { runCatching { importImage.launch(arrayOf("image/*")) }.onFailure { message = "No document picker available." } }) { Text("Choose image") }
                TextButton(onClick = { state.zoom = 1f; state.panX = 0f; state.panY = 0f }) { Text("Reset crop") }
                Control("Zoom", state.zoom, 1f..3f) { state.zoom = it }
                Control("Horizontal crop", state.panX, -1f..1f) { state.panX = it }
                Control("Vertical crop", state.panY, -1f..1f) { state.panY = it }
            } }
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(clockAnchor))
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clock controls · size, position & style", style = MaterialTheme.typography.titleLarge)
                ClockControls(state)
                Spacer(Modifier.height(1.dp).bringIntoViewRequester(styleAnchor))
                Row { listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono").forEach { (font, label) ->
                    FilterChip(selected = !state.useCustomFont && state.family == font, onClick = { state.family = font; state.useCustomFont = false }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp))
                } }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedButton(enabled = !busy && !watchUi.transferring, onClick = {
                        runCatching { importFont.launch(arrayOf("*/*")) }.onFailure { message = "No document picker available" }
                    }) { Text("Import TTF / OTF") }
                    if (state.customTypeface != null) FilterChip(selected = state.useCustomFont, onClick = { state.useCustomFont = true }, label = { Text("Imported") }, modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Dark outline", Modifier.weight(1f)); Switch(state.outline, { state.outline = it })
                }
                ColorPicker("Hours", state.hours) { state.hours = it }
                ColorPicker("Minutes", state.minutes) { state.minutes = it }
            } }
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
            Text(message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(brushAnchor))
            ClockBrushPanel(state, frame)
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(watchAnchor))
            WatchPanel(watch, frame.face, state, frame,
                onChooseImage = {
                    if (!busy && !watchUi.transferring) runCatching { importImage.launch(arrayOf("image/*")) }
                        .onFailure { message = "No document picker available" }
                }, onEditStyle = { jump(styleAnchor) }, depthAnchor = Modifier.bringIntoViewRequester(depthAnchor))
            Text("Session edits survive rotation, not process termination. Full .bin import and saved projects are still pending. Uploads stop when the app leaves the foreground; keep it open until finished.", style = MaterialTheme.typography.bodySmall)
            Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_COMMIT}\n${BuildConfig.BUILD_DATE}\nIndependent GPL-3.0 project; not an official Fire-Boltt or Da Fit app.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun Control(label: String, value: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
    Text(label); Slider(value = value.coerceIn(range), onValueChange = update, valueRange = range)
}
@Composable private fun ColorPicker(label: String, selected: Int, update: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(label)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("White" to 0xffffffff.toInt(), "Orange" to 0xffff7a2f.toInt(), "Mint" to 0xff73e2bb.toInt()).forEach { (name, color) ->
            FilterChip(selected = selected == color, onClick = { update(color) }, label = { Text(name) })
        }
    }
    OutlinedButton(onClick = { open = true }) { Text("Custom · #%06X".format(selected and 0xffffff)) }
    if (open) {
        var hex by remember { mutableStateOf("%06X".format(selected and 0xffffff)) }
        val rgb = hex.takeIf { it.length == 6 }?.toIntOrNull(16)
        val color = 0xff000000.toInt() or (rgb ?: (selected and 0xffffff))
        AlertDialog(onDismissRequest = { open = false }, title = { Text("$label color") }, text = {
            Column {
                Surface(color = Color(color), modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {}
                OutlinedTextField(value = hex, onValueChange = { value ->
                    hex = value.removePrefix("#").filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase()
                }, label = { Text("Hex RGB · 6 digits") }, singleLine = true, isError = rgb == null)
                listOf("Red" to 16, "Green" to 8, "Blue" to 0).forEach { (name, shift) ->
                    val component = (color ushr shift) and 255
                    Text("$name · $component")
                    Slider(value = component.toFloat(), valueRange = 0f..255f, onValueChange = {
                        val edited = (color and (255 shl shift).inv()) or (it.roundToInt() shl shift)
                        hex = "%06X".format(edited and 0xffffff)
                    })
                }
                Text("Watch colors are quantized to RGB565.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = rgb != null, onClick = { update(color); open = false }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}
