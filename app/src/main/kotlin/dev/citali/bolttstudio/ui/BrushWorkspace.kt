package dev.citali.bolttstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.citali.bolttstudio.codec.*
import kotlin.math.roundToInt

/** Owned by EditorState, so the open draft and history survive activity rotation. */
class BrushSession(initial: ByteArray, val source: MoyFace.Face) {
    val original = initial.clone()
    val history = MaskHistory(initial)
    var mask by mutableStateOf(history.current)
    var revision by mutableIntStateOf(0)
    var viewport by mutableStateOf(CanvasViewport())
    var tool by mutableIntStateOf(0) // erase, restore, navigate
    var radius by mutableFloatStateOf(12f)
    var feather by mutableStateOf(true)
    var tint by mutableStateOf(false)
    var compare by mutableStateOf(false)
    var minute by mutableIntStateOf(609)
    var cursor by mutableStateOf<Offset?>(null)
    var drawing by mutableStateOf(false)
    var canvasWidth = 0
    var canvasHeight = 0
    var askDiscard by mutableStateOf(false)
    var askClear by mutableStateOf(false)
    val dirty get() = !mask.contentEquals(original)
    fun preview(bytes: ByteArray) { history.replacePreview(bytes); mask = history.current }
    fun finish(before: ByteArray) { history.finishStroke(before); revision++ }
    fun undo() { history.undo(); mask = history.current; revision++ }
    fun redo() { history.redo(); mask = history.current; revision++ }
    fun clear() { history.clear(); mask = history.current; revision++ }
}

@Composable
fun BrushWorkspace(session: BrushSession, source: MoyFace.Face, onDone: (ByteArray) -> Unit, onCancel: () -> Unit) {
    fun close() { if (session.dirty) session.askDiscard = true else onCancel() }
    val digits = listOf(session.minute / 60 / 10, session.minute / 60 % 10, session.minute % 60 / 10, session.minute % 10)
    val displayed = if (session.compare) session.original else session.mask
    val picture = remember(source, displayed, session.minute) {
        val bg = source.background
        val bitmap = Bitmap.createBitmap(bg.width, bg.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(bg.pixels, 0, bg.width, 0, 0, bg.width, bg.height)
        }
        val canvas = AndroidCanvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        digits.forEachIndexed { i, digit ->
            val img = ClockMask.apply(source.digits[source.sets[i]][digit], source.positions[i], displayed)
            val glyph = Bitmap.createBitmap(img.pixels, img.width, img.height, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(glyph, source.positions[i].x.toFloat(), source.positions[i].y.toFloat(), paint)
            glyph.recycle()
        }
        bitmap.asImageBitmap()
    }
    val overlay = remember(displayed, session.tint) {
        if (!session.tint) null else Bitmap.createBitmap(IntArray(240 * 296) { i ->
            (((255 - (displayed[i].toInt() and 255)) / 2) shl 24) or 0xff3366
        }, 240, 296, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { close() }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xffffab77))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xff15171b)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::close, enabled = !session.drawing) { Text("Cancel") }
                    Column(Modifier.weight(1f)) {
                        Text("Depth brush", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("Draft · 240 × 296 · ${(session.viewport.zoom * 100).roundToInt()}% fit", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = { onDone(session.mask.clone()) }, enabled = !session.drawing && !session.compare) { Text("Done") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Erase", "Restore", "Pan / zoom").forEachIndexed { i, label ->
                        FilterChip(session.tool == i, { session.tool = i; session.compare = false }, enabled = !session.drawing, label = { Text(label) })
                    }
                    val undoCount = remember(session.revision) { session.history.undoCount }
                    val redoCount = remember(session.revision) { session.history.redoCount }
                    OutlinedButton(enabled = undoCount > 0 && !session.drawing, onClick = session::undo) { Text("Undo") }
                    OutlinedButton(enabled = redoCount > 0 && !session.drawing, onClick = session::redo) { Text("Redo") }
                    TextButton(enabled = !session.drawing, onClick = { session.viewport = CanvasViewport() }) { Text("Fit") }
                    FilterChip(session.compare, { session.compare = !session.compare }, enabled = !session.drawing, label = { Text("Before") })
                    FilterChip(session.tint, { session.tint = !session.tint }, enabled = !session.drawing, label = { Text("Mask tint") })
                    TextButton(enabled = !session.drawing, onClick = { session.askClear = true }) { Text("Clear") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp).background(Color(0xff25282f), RoundedCornerShape(12.dp))) {
                    Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0 && session.canvasWidth > 0 && session.canvasHeight > 0) {
                            session.viewport = session.viewport.resized(session.canvasWidth.toFloat(), session.canvasHeight.toFloat(), size.width.toFloat(), size.height.toFloat())
                        }
                        session.canvasWidth = size.width; session.canvasHeight = size.height
                    }.pointerInput(session.tool, session.radius, session.feather, session.compare) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false); down.consume()
                            val before = session.mask.clone()
                            var previous = down.position
                            var navigating = session.tool == 2 || session.compare
                            var painted = false
                            var completed = false
                            session.drawing = true
                            fun paint(a: Offset, b: Offset) {
                                val (x0, y0) = session.viewport.imagePoint(a.x, a.y, size.width.toFloat(), size.height.toFloat())
                                val (x1, y1) = session.viewport.imagePoint(b.x, b.y, size.width.toFloat(), size.height.toFloat())
                                session.preview(ClockMask.stroke(session.mask, x0, y0, x1, y1, session.radius, session.tool == 1, session.feather))
                                session.cursor = Offset(x1, y1); painted = true
                            }
                            try {
                                if (!navigating) paint(previous, previous)
                                do {
                                    val event = awaitPointerEvent()
                                    val held = event.changes.filter { it.pressed }
                                    if (held.size >= 2) {
                                        // A second finger converts the gesture to navigation. Remove the
                                        // provisional one-finger mark and don't add a paint history item.
                                        if (!navigating && painted) { session.preview(before); painted = false }
                                        navigating = true; session.cursor = null
                                        val a = held[0]; val b = held[1]
                                        if (a.previousPressed && b.previousPressed) {
                                            val old = (a.previousPosition + b.previousPosition) / 2f
                                            val now = (a.position + b.position) / 2f
                                            val oldDistance = (a.previousPosition - b.previousPosition).getDistance()
                                            val distance = (a.position - b.position).getDistance()
                                            val factor = if (oldDistance > 1f && distance > 1f) distance / oldDistance else 1f
                                            session.viewport = session.viewport.transform(old.x, old.y, now.x, now.y, factor, size.width.toFloat(), size.height.toFloat())
                                        }
                                    } else if (navigating) {
                                        held.firstOrNull()?.let { finger ->
                                            if (finger.previousPressed) session.viewport = session.viewport.transform(
                                                finger.previousPosition.x, finger.previousPosition.y, finger.position.x, finger.position.y,
                                                1f, size.width.toFloat(), size.height.toFloat())
                                        }
                                    } else {
                                        event.changes.firstOrNull { it.id == down.id }?.let { finger -> paint(previous, finger.position); previous = finger.position }
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (held.isNotEmpty())
                                if (painted) session.finish(before)
                                completed = true
                            } finally {
                                // Cancellation (rotation, input/tool interruption) must not leave an
                                // unrecorded partial stroke. A completed stroke already has history.
                                if (painted && !completed) session.preview(before)
                                session.cursor = null; session.drawing = false
                            }
                        }
                    }) {
                        val viewport = session.viewport
                        val pixelsPerUnit = viewport.scale(size.width, size.height)
                        val (left, top) = viewport.origin(size.width, size.height)
                        withTransform({ translate(left, top); scale(pixelsPerUnit, pixelsPerUnit, Offset.Zero) }) {
                            drawImage(picture)
                            overlay?.let { drawImage(it) }
                            drawRect(Color(0xff9298a4), size = Size(240f, 296f), style = Stroke(1f / pixelsPerUnit))
                            session.cursor?.let { point ->
                                drawCircle(Color.Black, session.radius, point, style = Stroke(3f / pixelsPerUnit))
                                drawCircle(Color.White, session.radius, point, style = Stroke(1f / pixelsPerUnit))
                            }
                        }
                    }
                    Text(if (session.compare) "BEFORE · navigation only" else "One finger draws · two fingers pan / zoom",
                        Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(0xcc15171b), RoundedCornerShape(6.dp)).padding(6.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${(session.radius * 2).roundToInt()} px", color = Color.White, modifier = Modifier.width(56.dp))
                    Slider(session.radius, { session.radius = it }, valueRange = 2f..40f, enabled = !session.drawing, modifier = Modifier.weight(1f))
                    Text("Soft", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    Switch(session.feather, { session.feather = it }, enabled = !session.drawing)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Check time", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    listOf(609 to "10:09", 754 to "12:34", 1438 to "23:58", 488 to "08:08").forEach { (minute, label) ->
                        TextButton(enabled = !session.drawing, onClick = { session.minute = minute }) { Text(label) }
                    }
                }
            }
        }
        if (session.askDiscard) AlertDialog(onDismissRequest = { session.askDiscard = false }, title = { Text("Discard brush draft?") },
            text = { Text("Your editor mask will stay unchanged. Use Done to apply this draft.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { session.askDiscard = false }) { Text("Keep editing") } })
        if (session.askClear) AlertDialog(onDismissRequest = { session.askClear = false }, title = { Text("Clear draft mask?") },
            text = { Text("Reveals the whole clock in this draft. You can undo this action.") },
            confirmButton = { TextButton(onClick = { session.clear(); session.askClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { session.askClear = false }) { Text("Cancel") } })
        }
    }
}
