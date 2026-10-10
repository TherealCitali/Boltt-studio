package dev.citali.bolttstudio.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.citali.bolttstudio.codec.MoyFace
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
class WatchViewModel(application: Application) : AndroidViewModel(application) {
    data class Device(val address: String, val name: String, val rssi: Int)
    data class Ui(val scanning: Boolean = false, val connecting: Boolean = false, val ready: Boolean = false,
        val transferring: Boolean = false, val progress: Float = 0f, val status: String = "Not connected",
        val selected: String = "", val packet: Int = 20, val devices: List<Device> = emptyList(), val logs: List<String> = emptyList())
    private val mutable = MutableStateFlow(Ui()); val ui = mutable.asStateFlow()
    private val context = application.applicationContext
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val devices = linkedMapOf<String, BluetoothDevice>()
    private var scanCallback: ScanCallback? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var scanTimer: Job? = null
    private var operation: Job? = null
    private var session: GattSession? = null

    companion object {
        fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun hasPermissions() = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun bluetoothEnabled() = hasPermissions() && runCatching { adapter?.isEnabled == true }.getOrDefault(false)
    fun locationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return true
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
        else location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    fun note(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        mutable.value = mutable.value.copy(logs = (mutable.value.logs + "$time $message").takeLast(120))
    }
    fun report(message: String) { mutable.value = mutable.value.copy(status = message); note(message) }

    fun scan(showAll: Boolean) {
        if (mutable.value.connecting || mutable.value.transferring || mutable.value.ready) return
        if (!hasPermissions()) { report("Grant Nearby devices / location permission before scanning"); return }
        if (!bluetoothEnabled()) { report("Turn Bluetooth on before scanning"); return }
        if (!locationEnabled()) { report("Android 10 requires system Location enabled for BLE scanning"); return }
        stopScan(); devices.clear()
        mutable.value = mutable.value.copy(devices = emptyList(), status = "Scanning for 20 seconds…")
        startScanWith(showAll)
    }
    private fun startScanWith(showAll: Boolean) {
        lateinit var callback: ScanCallback
        fun found(result: ScanResult) {
            viewModelScope.launch {
                if (scanCallback !== callback) return@launch
                try {
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: "Unnamed device"
                    val known = result.scanRecord?.serviceUuids?.any { it.uuid == GattSession.SERVICE } == true ||
                        listOf("fireboltt", "fire-boltt", "brillia", "moyoung").any { name.lowercase().contains(it) }
                    if (!showAll && !known) return@launch
                    val address = result.device.address
                    if (devices.size >= 50 && address !in devices) return@launch
                    devices[address] = result.device
                    val updated = mutable.value.devices.filterNot { it.address == address } + Device(address, name, result.rssi)
                    mutable.value = mutable.value.copy(devices = updated.sortedByDescending { it.rssi })
                } catch (_: SecurityException) { stopScan(); report("Bluetooth permission was revoked") }
            }
        }
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = found(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { found(it) } }
            override fun onScanFailed(errorCode: Int) { viewModelScope.launch {
                if (scanCallback === callback) { stopScan(); report("BLE scan failed ($errorCode)") }
            } }
        }
        try {
            val scanner = adapter?.bluetoothLeScanner ?: error("BLE scanner unavailable")
            scanCallback = callback; activeScanner = scanner
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            mutable.value = mutable.value.copy(scanning = true)
            note("BLE scan started; ${if (showAll) "all nearby devices" else "candidate watches only"}")
            scanTimer = viewModelScope.launch { delay(20_000L); stopScan(); report("Scan finished; select your watch or scan again") }
        } catch (error: Exception) { stopScan(); report(error.message ?: "BLE scan could not start") }
    }
    fun stopScan() {
        scanTimer?.cancel(); scanTimer = null
        val callback = scanCallback; scanCallback = null
        runCatching { if (callback != null) activeScanner?.stopScan(callback) }; activeScanner = null
        mutable.value = mutable.value.copy(scanning = false,
            status = if (mutable.value.status.startsWith("Scanning for")) "Scan stopped; select a device or scan again" else mutable.value.status)
    }
    fun connect(device: Device) {
        if (mutable.value.connecting || mutable.value.transferring || mutable.value.ready) return
        val target = devices[device.address] ?: return
        if (!hasPermissions()) { report("Bluetooth permission required"); return }
        stopScan()
        lateinit var current: GattSession
        current = GattSession(context, viewModelScope, ::note) { reason ->
            if (session === current) {
                session = null; operation?.cancel()
                mutable.value = mutable.value.copy(ready = false, connecting = false, transferring = false, status = reason)
                note("$reason. If uploading, a partial face may remain.")
            }
        }
        session = current
        mutable.value = mutable.value.copy(connecting = true, selected = device.name, progress = 0f, status = "Connecting and discovering services…")
        operation = viewModelScope.launch {
            try {
                current.connect(target)
                if (session === current) mutable.value = mutable.value.copy(ready = true, connecting = false,
                    status = "Ready — verify Brillia model/firmware before sending", packet = current.payloadBytes)
            } catch (timeout: TimeoutCancellationException) {
                if (session === current) { current.close(); session = null; report("Connection setup timed out. Try again with Da Fit stopped.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (session === current) { current.close(); session = null; report(error.message ?: "Connection failed") }
            } finally {
                if (session === current || session == null) mutable.value = mutable.value.copy(connecting = false)
            }
        }
    }
    fun upload(bytes: ByteArray, label: String) {
        val current = session ?: return
        if (!mutable.value.ready || mutable.value.transferring) return
        val snapshot = bytes.clone()
        mutable.value = mutable.value.copy(transferring = true, progress = 0f, status = "Validating $label…")
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { MoyFace.parse(snapshot) }
                note("Upload: $label, ${snapshot.size} bytes. Captured protocol; hardware result is experimental.")
                current.beginTransfer()
                val check = TransferProtocol.upload(snapshot, current, { progress ->
                    mutable.value = mutable.value.copy(progress = (progress.uniqueBytesQueued.toFloat() / progress.totalBytes).coerceAtMost(.99f),
                        status = "Block ${progress.block}: ${progress.uniqueBytesQueued}/${progress.totalBytes} bytes queued; waiting for watch",
                        packet = progress.packetBytes)
                }, ::note)
                mutable.value = mutable.value.copy(progress = 1f, status = "Watch reported complete (check $check). Final commands sent — inspect the watch.")
            } catch (timeout: TimeoutCancellationException) {
                if (session === current) { current.close(); session = null; report("Transfer timed out. Connection closed; inspect the watch before retrying.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (session === current) { current.close(); session = null; report("Upload stopped: ${error.message ?: "unknown error"}. A partial face may remain.") }
            } finally {
                current.endTransfer()
                if (session === current || session == null)
                    mutable.value = mutable.value.copy(transferring = false, ready = session != null)
            }
        }
    }
    fun disconnect(reason: String = "Disconnected") {
        stopScan(); val old = session; session = null
        operation?.cancel(); operation = null; old?.close()
        mutable.value = mutable.value.copy(ready = false, connecting = false, transferring = false, status = reason)
        note(reason)
    }
    fun backgrounded() {
        stopScan()
        if (mutable.value.transferring || mutable.value.connecting) disconnect("App left foreground; operation cancelled. Inspect watch before retrying.")
    }
    override fun onCleared() { stopScan(); session?.close(); super.onCleared() }
}
