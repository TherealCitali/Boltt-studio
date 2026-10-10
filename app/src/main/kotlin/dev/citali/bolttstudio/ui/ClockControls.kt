package dev.citali.bolttstudio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citali.bolttstudio.EditorState
import dev.citali.bolttstudio.codec.ClockDesign
import kotlin.math.roundToInt

@Composable
fun ClockControls(state: EditorState) {
    Text("Clock layout", style = MaterialTheme.typography.titleMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(true to "Stacked", false to "Side by side").forEachIndexed { index, (stacked, label) ->
            SegmentedButton(selected = state.stacked == stacked, onClick = { state.arrangeClock(stacked) },
                shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
        }
    }
    Text(if (state.stacked) "Hours above minutes" else "Hours left · minutes right", style = MaterialTheme.typography.bodySmall)

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Independent hours / minutes", Modifier.weight(1f))
        Switch(state.independent, {
            state.independent = it
            if (!it) {
                val h = state.design.hours
                state.design = state.design.resized(true, false, h.width, h.height, h.gap)
            }
        })
    }
    if (state.independent) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.editHours, { state.editHours = true }, label = { Text("Hours") })
        FilterChip(!state.editHours, { state.editHours = false }, label = { Text("Minutes") })
    }
    val group = if (state.editHours || !state.independent) state.design.hours else state.design.minutes
    fun resize(w: Int = group.width, h: Int = group.height, gap: Int = group.gap) {
        state.design = state.design.resized(state.editHours, state.independent, w, h, gap)
    }
    Text(if (state.independent) "Editing ${if (state.editHours) "hours" else "minutes"}" else "Linked sizing and movement", style = MaterialTheme.typography.labelLarge)
    var sizeDrag by remember { mutableFloatStateOf(1f) }
    var sizeBase by remember { mutableStateOf(group) }
    var sizing by remember { mutableStateOf(false) }
    Text("Overall size · proportional")
    Slider(sizeDrag, { value ->
        if (!sizing) { sizeBase = group; sizing = true }
        sizeDrag = value
        val low = maxOf(8f / sizeBase.width, 16f / sizeBase.height)
        val high = minOf(((240 - sizeBase.gap) / 2).toFloat() / sizeBase.width, 240f / sizeBase.height)
        val scale = value.coerceIn(low, high)
        resize((sizeBase.width * scale).roundToInt(), (sizeBase.height * scale).roundToInt())
    }, valueRange = .5f..2f, onValueChangeFinished = { sizeDrag = 1f; sizing = false })
    fun move(x: Int = group.x, y: Int = group.y) {
        state.design = state.design.move(state.editHours || !state.independent, state.independent, x - group.x, y - group.y)
    }
    PixelSlider("Digit width / horizontal stretch", group.width, 8..((240 - group.gap) / 2)) { resize(w = it) }
    PixelSlider("Digit height / vertical stretch", group.height, 16..240) { resize(h = it) }
    PixelSlider("Spacing between digits", group.gap, 0..24) { resize(gap = it) }
    val h = state.design.hours; val m = state.design.minutes
    val xRange = if (state.independent) 0..(240 - group.span)
        else (group.x - minOf(h.x, m.x))..(group.x + 240 - maxOf(h.x + h.span, m.x + m.span))
    val yRange = if (state.independent) 0..(296 - group.height)
        else (group.y - minOf(h.y, m.y))..(group.y + 296 - maxOf(h.y + h.height, m.y + m.height))
    PixelSlider("Position X", group.x, xRange) { move(x = it) }
    PixelSlider("Position Y", group.y, yRange) { move(y = it) }
    Text("Shape presets · preserves your selected/imported font", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { resize(34, 110) }) { Text("Tall") }
        TextButton(onClick = { resize(54, 62) }) { Text("Wide") }
        TextButton(onClick = { resize(28, 82) }) { Text("Condensed") }
    }
    TextButton(onClick = { state.design = ClockDesign(); state.arrangeClock(true); state.design = ClockDesign(); state.independent = false; state.editHours = true }) { Text("Reset size & position") }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Snap dragging to 4 px", Modifier.weight(1f)); Switch(state.snap, { state.snap = it })
    }
    if (state.design.overlaps) Text("Hours and minutes overlap. Use independent placement or an arrange button before uploading.", color = MaterialTheme.colorScheme.error)
    Text("Width/height stretch the glyphs; these are inspired shape controls, not Apple/ColorOS fonts. New dimensions need device testing. Oversized layouts are bounded to the screen.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PixelSlider(label: String, value: Int, range: IntRange, update: (Int) -> Unit) {
    Text("$label · $value px")
    if (range.first < range.last) Slider(value.toFloat(), { update(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
}
