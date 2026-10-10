@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.citali.bolttstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ColorWheelPicker(label: String, selected: Int, update: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var hsv by remember { mutableStateOf(WheelColor.fromArgb(selected)) }
    var emitted by remember { mutableIntStateOf(selected) }
    var hex by remember(selected) { mutableStateOf("%06X".format(selected and 0xffffff)) }
    val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(selected) {
        if (selected != emitted) {
            val incoming = WheelColor.fromArgb(selected)
            hsv = incoming.copy(hue = if (incoming.saturation == 0f) hsv.hue else incoming.hue)
            emitted = selected
        }
    }
    fun apply(color: WheelColor) { hsv = color; emitted = color.argb(); update(emitted) }
    fun applyHex() {
        hex.takeIf { it.length == 6 }?.toIntOrNull(16)?.let { apply(WheelColor.fromArgb(0xff000000.toInt() or it)) }
        focus.clearFocus(); keyboard?.hide()
    }
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState<(WheelColor) -> Unit>({ apply(it) })
    Text(label, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("White" to 0xffffffff.toInt(), "Orange" to 0xffff7a2f.toInt(), "Mint" to 0xff73e2bb.toInt()).forEach { (name, color) ->
            FilterChip(selected == color, { apply(WheelColor.fromArgb(color)) }, label = { Text(name) })
        }
    }
    OutlinedButton(onClick = { open = !open; focus.clearFocus(); keyboard?.hide() }) {
        Text(if (open) "Close $label colour wheel" else "$label colour wheel · #%06X".format(selected and 0xffffff))
    }
    if (open) {
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
            .semantics {
                contentDescription = "$label colour wheel"
                stateDescription = "Colour #%06X".format(selected and 0xffffff)
                customActions = listOf(
                    CustomAccessibilityAction("Next hue") { apply(hsv.copy(hue = (hsv.hue + 15) % 360)); true },
                    CustomAccessibilityAction("Previous hue") { apply(hsv.copy(hue = (hsv.hue + 345) % 360)); true },
                    CustomAccessibilityAction("More saturation") { apply(hsv.copy(saturation = (hsv.saturation + .1f).coerceAtMost(1f))); true },
                    CustomAccessibilityAction("Less saturation") { apply(hsv.copy(saturation = (hsv.saturation - .1f).coerceAtLeast(0f))); true },
                    CustomAccessibilityAction("Brighter") { apply(hsv.copy(value = (hsv.value + .1f).coerceAtMost(1f))); true },
                    CustomAccessibilityAction("Darker") { apply(hsv.copy(value = (hsv.value - .1f).coerceAtLeast(0f))); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val g = WheelGeometry(min(size.width, size.height).toFloat())
                    val origin = Offset((size.width - g.size) / 2f, (size.height - g.size) / 2f)
                    val start = down.position - origin
                    val ring = g.isRing(start.x, start.y)
                    if (!ring && !g.isDiamond(start.x, start.y)) return@awaitEachGesture
                    fun choose(position: Offset) {
                        val p = position - origin
                        change(if (ring) current.copy(hue = g.hue(p.x, p.y)) else {
                            val (s, v) = g.sv(p.x, p.y); current.copy(saturation = s, value = v)
                        })
                    }
                    down.consume(); choose(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (pointer.isConsumed) break
                        pointer.consume()
                        if (pointer.pressed) choose(pointer.position)
                    } while (pointer.pressed)
                }
            }) {
            val g = WheelGeometry(min(size.width, size.height))
            val center = Offset(size.width / 2f, size.height / 2f)
            val hue = Color(WheelColor(hsv.hue, 1f, 1f).argb())
            drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center),
                g.ringRadius, center, style = Stroke(g.ringWidth))
            val topLeft = center - Offset(g.halfSide, g.halfSide)
            val bottomRight = center + Offset(g.halfSide, g.halfSide)
            rotate(45f, center) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hue), topLeft.x, bottomRight.x), topLeft, Size(2 * g.halfSide, 2 * g.halfSide))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), topLeft.y, bottomRight.y), topLeft, Size(2 * g.halfSide, 2 * g.halfSide))
            }
            fun handle(at: Offset, color: Color) {
                drawCircle(Color.Black.copy(alpha = .7f), 10.dp.toPx(), at)
                drawCircle(Color.White, 8.dp.toPx(), at)
                drawCircle(color, 5.dp.toPx(), at)
            }
            val radians = hsv.hue * PI.toFloat() / 180f
            handle(center + Offset(cos(radians), sin(radians)) * g.ringRadius, hue)
            val (x, y) = g.point(hsv.saturation, hsv.value)
            handle(Offset(x, y) + center - Offset(g.center, g.center), Color(hsv.argb()))
        }
        }
        Text("Ring: hue · Diamond: saturation & brightness", style = MaterialTheme.typography.bodySmall)
        Surface(color = Color(selected), modifier = Modifier.fillMaxWidth().height(32.dp), shape = MaterialTheme.shapes.medium) {}
        OutlinedTextField(value = hex, onValueChange = {
            hex = it.removePrefix("#").filter { c -> c in "0123456789abcdefABCDEF" }.take(6).uppercase()
        }, label = { Text("$label hex · 6 digits") }, singleLine = true,
            isError = hex.length != 6, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { applyHex() }))
        TextButton(enabled = hex.length == 6, onClick = { applyHex() }) { Text("Apply $label hex") }
        Text("Opaque colours · quantized to RGB565 on the watch.", style = MaterialTheme.typography.bodySmall)
    }
}
