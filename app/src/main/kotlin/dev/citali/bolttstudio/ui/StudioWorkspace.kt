package dev.citali.bolttstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.bolttstudio.EditorState
import dev.citali.bolttstudio.R
import kotlin.math.roundToInt

/** Non-modal sheet: its upper bound reserves a visible, interactive preview.
 * Only the grab area resizes it; settings scroll independently with elastic edges. */
@Composable
fun StudioWorkspace(state: EditorState, image: Bitmap, pageTitle: String, atHome: Boolean,
    status: String, onBack: () -> Unit, onWatch: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var fraction by rememberSaveable { mutableFloatStateOf(.49f) }
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize().testTag("studio-workspace").windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_boltt), null, Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Boltt Studio", style = MaterialTheme.typography.titleMedium)
                    Text("YOUR TIME, YOUR DESIGN", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onWatch, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(status, style = MaterialTheme.typography.labelMedium)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth > 600.dp && maxWidth > maxHeight
                val density = LocalDensity.current
                val availableHeight = maxHeight.coerceAtLeast(1.dp)
                val totalPx = with(density) { availableHeight.toPx() }.coerceAtLeast(1f)
                // Small windows still reserve at least 35% for preview. Normal phones reserve 160dp.
                val reserved = minOf(160.dp, availableHeight * .35f)
                val maxFraction = ((availableHeight - reserved) / availableHeight).coerceIn(.5f, .78f)
                val minFraction = .32f
                val actualFraction = fraction.coerceIn(minFraction, maxFraction)
                val sheetHeight = availableHeight * actualFraction
                val preview: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier.testTag("preview-region").padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.radialGradient(listOf(colors.primaryContainer.copy(alpha = .7f), colors.background))),
                            contentAlignment = Alignment.Center) {
                            val height = minOf(maxHeight - 8.dp, (maxWidth - 8.dp) * 296f / 240f).coerceAtLeast(1.dp)
                            Image(image.asImageBitmap(), "Watch preview. Drag to move ${if (!state.independent) "the clock" else if (state.editHours) "hours" else "minutes"}.",
                                Modifier.height(height).aspectRatio(240f / 296f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(2.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                                    .pointerInput(state.independent, state.editHours, state.snap) {
                                        var dx = 0f; var dy = 0f; var start = state.design
                                        detectDragGestures(onDragStart = { dx = 0f; dy = 0f; start = state.design }) { change, delta ->
                                            change.consume()
                                            dx += delta.x * 240f / size.width; dy += delta.y * 296f / size.height
                                            val step = if (state.snap) 4 else 1
                                            state.design = start.move(state.editHours || !state.independent, state.independent,
                                                (dx / step).roundToInt() * step, (dy / step).roundToInt() * step)
                                        }
                                    })
                        }
                        Text("Drag clock to move · 240 × 296", Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = colors.onSurfaceVariant)
                    }
                }
                val sheet: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier.testTag("settings-sheet"), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = colors.surfaceContainerLow, tonalElevation = 2.dp) {
                        Column {
                            Column(Modifier.fillMaxWidth().then(if (wide) Modifier else Modifier.pointerInput(totalPx, maxFraction) {
                                detectVerticalDragGestures { change, drag ->
                                    change.consume(); fraction = (fraction.coerceIn(minFraction, maxFraction) - drag / totalPx).coerceIn(minFraction, maxFraction)
                                }
                            }), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!wide) Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(colors.outlineVariant))
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!atHome) TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Back") }
                                    Text(pageTitle, Modifier.weight(1f).padding(start = if (atHome) 8.dp else 4.dp),
                                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!wide) TextButton(onClick = {
                                        fraction = if (actualFraction > .53f) minFraction else maxFraction
                                    }) { Text(if (actualFraction > .53f) "Less" else "Expand") }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(.43f).fillMaxHeight())
                    sheet(Modifier.weight(.57f).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    sheet(Modifier.fillMaxWidth().height(sheetHeight))
                }
            }
        }
    }
}

@Composable
fun SettingsDestination(number: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(number, Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
