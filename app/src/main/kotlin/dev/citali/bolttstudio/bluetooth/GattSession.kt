package dev.citali.bolttstudio.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** One connection, one GATT operation at a time. Callbacks never start another GATT operation. */
@SuppressLint("MissingPermission") // UI requests permissions; revocation is caught and closes the session.
class GattSession(private val context: Context, private val scope: CoroutineScope,
    private val log: (String) -> Unit, private val disconnected: (String) -> Unit) : TransferProtocol.Port {
    companion object {
        fun uuid(short: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))
        val SERVICE = uuid(0xfeea); val CONTROL = uuid(0xfee2); val NOTIFY = uuid(0xfee3); val DATA = uuid(0xfee6)
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private data class Pending(val kind: String, val uuid: UUID?, val result: CompletableDeferred<Int>)
    private var pending: Pending? = null
    private val mutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var controlChar: BluetoothGattCharacteristic? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23
    override val payloadBytes get() = (negotiatedMtu - 3).coerceIn(20, 244)
    private val decoder = TransferProtocol.Decoder()
    private val notifications = Channel<TransferProtocol.Frame>(64)
    private var acceptingTransfer = false
    private class GattFailure(val code: Int) : Exception("GATT status $code")

    private fun post(g: BluetoothGatt, action: () -> Unit) {
        scope.launch(Dispatchers.Main.immediate) { if (g === gatt) action() }
    }
    private fun complete(kind: String, id: UUID?, status: Int) {
        pending?.takeIf { it.kind == kind && it.uuid == id }?.result?.complete(status)
    }
    private fun lost(reason: String) {
        close()
        disconnected(reason)
    }
    private fun notification(value: ByteArray) {
        if (!acceptingTransfer) return
        try {
            decoder.feed(value).forEach { frame ->
                // Ignore heartbeats and unrelated commands rather than consuming a transfer waiter.
                if (frame.command == 0xba || frame.command == 0x74) {
                    if (notifications.trySend(frame).isFailure) error("Transfer notification queue overflow")
                }
            }
        } catch (error: Exception) { lost(error.message ?: "Malformed watch notification") }
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = post(g) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                lost("Watch disconnected (GATT $status)")
            } else if (newState == BluetoothProfile.STATE_CONNECTED) complete("connect", null, status)
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = post(g) {
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu.coerceIn(23, 517)
            complete("mtu", null, status)
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = post(g) { complete("discover", null, status) }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = post(g) { complete("descriptor", d.uuid, status) }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = post(g) { complete("write", c.uuid, status) }
        @Deprecated("Compatibility callback for Android 10")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && c.uuid == NOTIFY) {
                @Suppress("DEPRECATION") val bytes = c.value?.clone() ?: return
                post(g) { notification(bytes) }
            }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == NOTIFY) { val copy = value.clone(); post(g) { notification(copy) } }
        }
    }
    private suspend fun operation(kind: String, id: UUID? = null, timeout: Long = 10_000L, start: () -> Boolean): Int =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                val waiter = Pending(kind, id, CompletableDeferred())
                check(pending == null) { "GATT operation already pending" }
                pending = waiter
                try {
                    check(start()) { "Android refused GATT $kind operation" }
                    withTimeout(timeout) { waiter.result.await() }
                } finally { if (pending === waiter) pending = null }
            }
        }
    private fun success(status: Int) { if (status != BluetoothGatt.GATT_SUCCESS) throw GattFailure(status) }

    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.Main.immediate) {
        try {
            success(operation("connect", timeout = 20_000L) {
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                gatt != null
            })
            val g = checkNotNull(gatt)
            log("GATT connected; requesting MTU 247")
            try {
                val status = operation("mtu", timeout = 4000L) { g.requestMtu(247) }
                if (status != BluetoothGatt.GATT_SUCCESS) log("MTU request refused; using negotiated/default size")
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive(); log("No MTU reply; using default until an MTU callback arrives")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalStateException) { log("MTU request unavailable; using default") }
            success(operation("discover", timeout = 15_000L) { g.discoverServices() })
            val service = g.getService(SERVICE) ?: error("Selected device does not expose FEEA; not a supported watch")
            controlChar = service.getCharacteristic(CONTROL) ?: error("Missing FEE2 control characteristic")
            dataChar = service.getCharacteristic(DATA) ?: error("Missing FEE6 data characteristic")
            val notify = service.getCharacteristic(NOTIFY) ?: error("Missing FEE3 notification characteristic")
            writeType(checkNotNull(controlChar)); writeType(checkNotNull(dataChar))
            val cccd = notify.getDescriptor(CCCD) ?: error("Missing notification CCCD")
            val value = when {
                notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> error("FEE3 does not support notifications/indications")
            }
            check(g.setCharacteristicNotification(notify, true)) { "Could not enable local notification routing" }
            success(operation("descriptor", CCCD) {
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    cccd.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            })
            log("FEEA/FEE2/FEE3/FEE6 ready; MTU=$negotiatedMtu, payload=$payloadBytes")
            log("Service match is not model/firmware verification; confirm Brillia before upload")
        } catch (error: Throwable) { close(); throw error }
    }
    private fun writeType(c: BluetoothGattCharacteristic): Int = when {
        c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else -> error("Characteristic ${c.uuid} is not writable")
    }
    private suspend fun write(c: BluetoothGattCharacteristic, bytes: ByteArray) {
        val type = writeType(c)
        val status = operation("write", c.uuid) {
            val g = checkNotNull(gatt) { "Connection closed" }
            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
            else {
                @Suppress("DEPRECATION")
                c.value = bytes
                c.writeType = type
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
        if (status == BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH) throw TransferProtocol.PacketTooLarge()
        success(status)
        // Android's write callback signals local queue completion, not a watch block acknowledgement.
        if (type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) delay(8)
    }
    override suspend fun control(command: Int, payload: ByteArray) {
        write(checkNotNull(controlChar) { "Not connected" }, TransferProtocol.frame(command, payload))
    }
    override suspend fun data(bytes: ByteArray) { write(checkNotNull(dataChar) { "Not connected" }, bytes) }
    override suspend fun await(command: Int, timeoutMs: Long): TransferProtocol.Frame = withTimeout(timeoutMs) {
        var f = notifications.receive()
        while (f.command != command) f = notifications.receive()
        f
    }
    fun beginTransfer() {
        while (notifications.tryReceive().isSuccess) { /* clear stale replies */ }
        decoder.clear(); acceptingTransfer = true
    }
    fun endTransfer() { acceptingTransfer = false; decoder.clear() }
    fun close() {
        acceptingTransfer = false
        pending?.result?.completeExceptionally(IllegalStateException("GATT connection closed"))
        notifications.close(IllegalStateException("Watch disconnected"))
        val g = gatt; gatt = null; controlChar = null; dataChar = null
        runCatching { g?.disconnect() }; runCatching { g?.close() }
    }
}
