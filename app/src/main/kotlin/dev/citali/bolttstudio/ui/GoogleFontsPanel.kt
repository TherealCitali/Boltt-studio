package dev.citali.bolttstudio.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.citali.bolttstudio.EditorState
import dev.citali.bolttstudio.fonts.FontLibraryState

@Composable
fun GoogleFontsPanel(editor: EditorState, canUse: Boolean, library: FontLibraryState = viewModel()) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(library.selected?.entry?.blob) { if (library.selected != null) listState.animateScrollToItem(1) }
    var licenseOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val fonts = remember(library.catalog, library.query, library.category, library.downloadedOnly, library.saved) {
        library.catalog?.search(library.query, library.category)?.filter { !library.downloadedOnly || it.blob in library.saved } ?: emptyList()
    }
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Viewport keys, not composed/prefetched items: no downloading the entire catalog.
    LaunchedEffect(listState, library.catalog, library.query, library.category, library.downloadedOnly, foreground, library.autoPreviews, library.busy) {
        if (!foreground || library.busy) return@LaunchedEffect
        val byName = library.catalog?.fonts?.associateBy { it.family } ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { byName[it.key as? String]?.family } }
            .distinctUntilChanged().collectLatest { names ->
                delay(250)
                library.previewVisible(names.mapNotNull { byName[it] }.filter { library.autoPreviews || it.blob in library.saved })
            }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(library.query, { library.query = it.take(100) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            label = { Text("Search Google Fonts") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxSize().testTag("font-library-list"), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("${library.catalog?.fonts?.size ?: 0} families · offline catalog", style = MaterialTheme.typography.titleMedium)
                Text("Visible cards automatically download their real fonts and licenses, including on mobile data. Saved previews work offline. Use font is still required to change your draft.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Sans Serif", "Serif", "Display", "Handwriting", "Monospace").forEach { category ->
                        FilterChip(library.category == category, { library.category = category }, label = { Text(category) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Automatic previews", Modifier.weight(1f))
                    Switch(library.autoPreviews, { library.updateAutoPreviews(it) })
                }
                FilterChip(library.downloadedOnly, { library.downloadedOnly = !library.downloadedOnly }, label = { Text("Downloaded · ${library.saved.size}") })
                if (library.saved.isNotEmpty()) TextButton(enabled = !library.busy, onClick = { confirmClear = true }) { Text("Clear downloads · keeps active draft font") }
                Text(library.status, style = MaterialTheme.typography.bodySmall)
                if (library.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (library.busy && (library.status.startsWith("Downloading") || library.status.startsWith("Opening"))) {
                    TextButton(onClick = { library.cancel() }) { Text("Cancel download") }
                }
            }
            library.selected?.let { selected ->
                item(key = "selected") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selected.entry.family, style = MaterialTheme.typography.titleLarge)
                            val color = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
                            AndroidView(factory = { TextView(it).apply {
                                gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                            } }, update = {
                                it.text = "0123456789"; it.typeface = library.preview; it.setTextColor(color)
                                it.contentDescription = "Digits zero through nine in ${selected.entry.family}"
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
                            Text("Check every digit. Use font updates the watch preview and saves a private copy with your draft.", style = MaterialTheme.typography.bodySmall)
                            Button(enabled = canUse && !library.busy && !editor.loading, onClick = { library.useSelected(editor) }) { Text("Use font") }
                            TextButton(onClick = { licenseOpen = !licenseOpen }) { Text(if (licenseOpen) "Hide font license" else "View font license") }
                            if (licenseOpen) Text(selected.license, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("${fonts.size} results · actual digit samples load as you scroll", style = MaterialTheme.typography.labelSmall) }
            if (fonts.isEmpty() && library.catalog != null) item {
                Text("No matching fonts. Try another name or category, or turn off Downloaded.", style = MaterialTheme.typography.bodyMedium)
            }
            items(fonts, key = { it.family }) { font ->
                val downloaded = font.blob in library.saved
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(font.family, style = MaterialTheme.typography.titleMedium)
                            Text("${font.category} · ${(font.bytes + 1023) / 1024} KB${if (downloaded) " · saved" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(enabled = !library.busy, onClick = { licenseOpen = false; library.retryPreview(font) }) {
                            Text(if (font.blob in library.cardErrors) "Retry" else if (downloaded) "Choose" else "Get")
                        }
                    }
                    val face = library.cardFonts[font.blob]
                    // Reserve the same space before and after loading; no viewport/card-height jumps.
                    Box(Modifier.fillMaxWidth().height((72f * LocalDensity.current.fontScale).dp),
                        contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                    if (face != null) {
                        val color = MaterialTheme.colorScheme.onSurface.toArgb()
                        AndroidView(factory = { TextView(it).apply {
                            setSingleLine(false)
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, TypedValue.COMPLEX_UNIT_SP)
                        } }, update = {
                            it.typeface = face; it.text = "10:54 · 0123456789"; it.setTextColor(color)
                            it.contentDescription = "Actual font sample: ${font.family}"
                        }, modifier = Modifier.fillMaxSize().testTag("font-sample-${font.family}"))
                    } else Text(library.cardErrors[font.blob] ?: if (library.autoPreviews || downloaded) "Loading actual font preview…" else "Automatic previews paused · tap Get", style = MaterialTheme.typography.bodySmall)
                    }
                    }
                }
            }
            if (library.saved.isNotEmpty()) item {
                Text("Your active draft font is kept separately. Downloads use up to 64 MiB of private storage.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear downloaded fonts?") },
        text = { Text("Library copies will be removed. Your active draft font and its license are kept, and you can download library fonts again.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; library.clearDownloads() }) { Text("Clear downloads") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
