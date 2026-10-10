package dev.citali.bolttstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.citali.bolttstudio.EditorState
import dev.citali.bolttstudio.codec.ClockMask
import kotlin.math.roundToInt

@Composable
fun ClockBrushPanel(state: EditorState, frame: FaceRenderer.Frame) {
    var painting by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var feather by remember { mutableStateOf(true) }
    var radius by remember { mutableFloatStateOf(12f) }
    var showMask by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Depth lab · clock transparency brush", style = MaterialTheme.typography.titleLarge)
            Text("Paint holes in live digits to reveal the photo. This is a fixed screen-space mask, not AI subject extraction or proof of foreground-image layering.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Apply painted transparency", Modifier.weight(1f)); Switch(state.maskEnabled, { state.maskEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(painting, { painting = !painting }, label = { Text(if (painting) "Painting on" else "Paint mode") })
                FilterChip(!restore, { restore = false }, label = { Text("Erase") })
                FilterChip(restore, { restore = true }, label = { Text("Restore") })
            }
            Text("Brush diameter · ${(radius * 2).roundToInt()} px")
            Slider(radius, { radius = it }, valueRange = 2f..40f)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Feather edges", Modifier.weight(1f)); Switch(feather, { feather = it })
                Text("Tint mask", Modifier.padding(start = 8.dp)); Switch(showMask, { showMask = it })
            }
            val overlay = remember(state.mask, showMask) {
                if (!showMask) null else Bitmap.createBitmap(IntArray(240 * 296) { i ->
                    (((255 - (state.mask[i].toInt() and 255)) / 2) shl 24) or 0x00ff3366
                }, 240, 296, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            Box(Modifier.width(240.dp).height(296.dp).align(androidx.compose.ui.Alignment.CenterHorizontally)
                .pointerInput(painting, restore, feather, radius) {
                    if (painting) awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume(); state.checkpointMask(); state.maskEnabled = true
                        var last = down.position
                        fun paint(x0: Float, y0: Float, x1: Float, y1: Float) {
                            state.mask = ClockMask.stroke(state.mask, x0 * 240 / size.width, y0 * 296 / size.height,
                                x1 * 240 / size.width, y1 * 296 / size.height, radius, restore, feather)
                        }
                        paint(last.x, last.y, last.x, last.y)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            paint(last.x, last.y, change.position.x, change.position.y)
                            last = change.position
                        } while (change.pressed)
                    }
                }) {
                Image(frame.image.asImageBitmap(), "Brush on clock to erase or restore transparency", Modifier.fillMaxSize())
                if (overlay != null) Image(overlay, "Tinted erased mask; tint is not exported", Modifier.fillMaxSize())
            }
            Row {
                TextButton(enabled = state.undoCount > 0, onClick = state::undoMask) { Text("Undo (${state.undoCount})") }
                TextButton(onClick = { state.checkpointMask(); state.mask = ClockMask.empty() }) { Text("Clear mask") }
            }
            Text("Inspect time · %02d:%02d".format(state.previewMinute / 60, state.previewMinute % 60))
            Slider(state.previewMinute.toFloat(), { state.previewMinute = it.roundToInt() }, valueRange = 0f..1439f)
            Row {
                listOf(609 to "10:09", 754 to "12:34", 1438 to "23:58", 488 to "08:08").forEach { (time, label) ->
                    TextButton(onClick = { state.previewMinute = time }) { Text(label) }
                }
            }
            if (state.maskEnabled && state.mask.any { (it.toInt() and 255) < 255 }) {
                Text("Experimental export: four position-specific digit tables. Every numeral gets the same screen-aligned mask; all four positions stay live. Check changing digits and screen wake on your watch.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text("Mask stays fixed to the 240×296 canvas when you move/resize the clock or recrop the photo. Repaint after changing the photo crop. It survives rotation, not process death.", style = MaterialTheme.typography.bodySmall)
                if (frame.maxOcclusion >= .45f) Text("WARNING: up to ${(frame.maxOcclusion * 100).roundToInt()}% of a numeral's opacity is hidden. Inspect other times or restore the mask.", color = MaterialTheme.colorScheme.error)
                else Text("Check legibility at several times: a brush stroke can hide some numerals more than others.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text("Use Export watchface or Send current face for this design. The separate magenta-stripe firmware probe does not use this brush.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
