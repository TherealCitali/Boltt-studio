package dev.citali.bolttstudio.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.citali.bolttstudio.BuildConfig
import dev.citali.bolttstudio.bluetooth.WatchViewModel
import dev.citali.bolttstudio.codec.MoyFace
import kotlinx.coroutines.*

private data class PendingUpload(val label: String, val bytes: ByteArray, val depthProbe: Boolean = false)

@Composable
fun WatchPanel(watch: WatchViewModel, face: MoyFace.Face) {
    val ui by watch.ui.collectAsState()
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var showAll by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingUpload?>(null) }
    var acknowledged by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (watch.bluetoothEnabled()) watch.scan(showAll) else watch.report("Bluetooth was not enabled")
    }
    fun scanOrEnable() {
        if (watch.bluetoothEnabled()) watch.scan(showAll)
        else runCatching { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { watch.report("Bluetooth unavailable or permission missing") }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (watch.hasPermissions()) scanOrEnable()
        else watch.report("Permission denied. Android 10 needs Location; Android 12+ needs Nearby devices. See app settings.")
    }
    val exportLogs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val text = "Boltt Studio ${BuildConfig.VERSION_NAME} / Android ${android.os.Build.VERSION.SDK_INT}\nBuild ${BuildConfig.BUILD_COMMIT}\n" +
                watch.ui.value.logs.joinToString("\n") + "\n"
            try {
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri, "wt") ?: error("No output")).bufferedWriter().use { it.write(text) } }
                watch.note("Diagnostic log exported")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { watch.report("Could not export log") }
        }
    }
    fun prepare(captured: Boolean) {
        preparing = true
        scope.launch {
            try {
                val snapshot = face
                val bytes = withContext(Dispatchers.Default) {
                    if (captured) withContext(Dispatchers.IO) { context.assets.open("dafit_captured_face.bin").use { it.readBytes() } }
                    else MoyFace.build(snapshot)
                }
                acknowledged = false
                pending = PendingUpload(if (captured) "Captured Da Fit face (original bytes)" else "Current editor face", bytes)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { watch.report("Cannot prepare face: ${error.message}") }
            finally { preparing = false }
        }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Watch · experimental BLE", style = MaterialTheme.typography.titleLarge)
            Text("Force-stop Da Fit first. Keep the watch above 30% charge and this app open. Intended for Brillia MOY-7QI2-2.0.1 only; service discovery cannot verify firmware.", style = MaterialTheme.typography.bodySmall)
            Text(ui.status, style = MaterialTheme.typography.bodyMedium)
            if (ui.selected.isNotBlank()) Text("Selected: ${ui.selected}", style = MaterialTheme.typography.labelMedium)
            if (ui.ready || ui.transferring) Text("Data packets: up to ${ui.packet} bytes", style = MaterialTheme.typography.labelSmall)
            if (!ui.ready && !ui.connecting && !ui.transferring) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Show all nearby devices", Modifier.weight(1f))
                    Switch(showAll, { showAll = it }, enabled = !ui.scanning)
                }
                Button(enabled = !preparing, onClick = {
                    if (ui.scanning) watch.stopScan()
                    else if (watch.hasPermissions()) scanOrEnable()
                    else permissions.launch(WatchViewModel.permissions())
                }) { Text(if (ui.scanning) "Stop scan" else "Scan for watch") }
                Row {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }) { Text("Permissions") }
                    if (android.os.Build.VERSION.SDK_INT <= 30) TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }) { Text("Location settings") }
                }
                ui.devices.forEach { device ->
                    OutlinedButton(onClick = { watch.connect(device) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(device.name)
                            Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (ui.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.transferring || ui.progress > 0f) LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
            if (ui.ready && !ui.transferring) {
                OutlinedButton(enabled = !preparing, onClick = { prepare(true) }, modifier = Modifier.fillMaxWidth()) { Text("Send captured Da Fit face · test first") }
                Button(enabled = !preparing, onClick = { prepare(false) }, modifier = Modifier.fillMaxWidth()) { Text("Send current face") }
            }
            if (ui.ready || ui.connecting || ui.transferring) OutlinedButton(onClick = {
                watch.disconnect(if (ui.transferring) "Upload cancelled and disconnected. A partial face may remain; inspect watch before retrying." else "Disconnected")
            }) { Text(if (ui.transferring) "Cancel & disconnect" else "Disconnect") }
            if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { showLogs = !showLogs }) { Text(if (showLogs) "Hide diagnostics" else "Show diagnostics") }
            if (showLogs) {
                Text(ui.logs.takeLast(16).joinToString("\n").ifBlank { "No connection events yet." }, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !ui.transferring, onClick = {
                    runCatching { exportLogs.launch("Boltt-Studio-BLE-${System.currentTimeMillis()}.txt") }.onFailure { watch.report("Document picker unavailable") }
                }) { Text("Export diagnostic log") }
            }
        }
    }
    DepthProbePanel(ready = ui.ready && !preparing, transferring = ui.transferring || ui.connecting, onUpload = { result ->
        acknowledged = false
        pending = PendingUpload(if (result.face.foreground != null) "EXPERIMENTAL depth layering probe" else "Depth probe — no-overlay control",
            result.bytes.clone(), depthProbe = true)
    })
    pending?.let { (label, bytes, depthProbe) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Send watchface?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$label · ${bytes.size} bytes\nTarget: ${ui.selected}")
                if (depthProbe) Text("Multiple-image support, alpha blending and redraw order are UNKNOWN. This is a diagnostic test, not a finished photo-depth feature. Inspect across minute changes and sleep/wake; an app preview or upload-complete message cannot establish support.")
                Text("This reverse-engineered protocol can leave a partial or unusable face if interrupted or incompatible. The completion-check algorithm and finalization fields are not fully understood. There is no guaranteed rollback.")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(acknowledged, { acknowledged = it })
                    Text("I selected my Brillia, stopped Da Fit, charged above 30%, and accept this test risk.", style = MaterialTheme.typography.bodySmall)
                }
            } },
            confirmButton = { TextButton(enabled = acknowledged && ui.ready && !ui.transferring, onClick = { pending = null; watch.upload(bytes, label) }) { Text("Send now") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}
