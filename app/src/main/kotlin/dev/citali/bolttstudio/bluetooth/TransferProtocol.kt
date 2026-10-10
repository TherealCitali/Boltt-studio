package dev.citali.bolttstudio.bluetooth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Pure protocol layer; reverse-engineered commands, not an official vendor API. */
object TransferProtocol {
    const val BLOCK_BYTES = 10_240
    fun frame(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val size = 5 + payload.size
        require(command in 0..255 && size <= 1024)
        return byteArrayOf(0xfe.toByte(), 0xea.toByte(), (0x20 or (size ushr 8)).toByte(), size.toByte(), command.toByte()) + payload
    }
    fun sizePayload(size: Int): ByteArray {
        require(size in 1..2 * 1024 * 1024)
        return ByteArray(4) { (size ushr (24 - it * 8)).toByte() }
    }
    data class Frame(val command: Int, val payload: ByteArray)

    /** Incremental notification framing: split/coalesced frames, bounded noise buffering. */
    class Decoder {
        private var pending = byteArrayOf()
        fun clear() { pending = byteArrayOf() }
        fun feed(bytes: ByteArray): List<Frame> {
            require(bytes.size <= 4096 && pending.size + bytes.size <= 8192) { "Notification buffer overflow" }
            pending += bytes
            val result = mutableListOf<Frame>()
            while (pending.size >= 2) {
                if ((pending[0].toInt() and 255) != 0xfe || (pending[1].toInt() and 255) != 0xea) {
                    pending = pending.copyOfRange(1, pending.size); continue
                }
                if (pending.size < 4) break
                val flag = pending[2].toInt() and 255
                val length = ((flag and 31) shl 8) or (pending[3].toInt() and 255)
                if ((flag and 0xe0) != 0x20 || length !in 5..1024) {
                    pending = pending.copyOfRange(1, pending.size); continue
                }
                if (pending.size < length) break
                result += Frame(pending[4].toInt() and 255, pending.copyOfRange(5, length))
                pending = pending.copyOfRange(length, pending.size)
            }
            return result
        }
    }

    /** Only an explicit length rejection permits retrying the same offset with smaller packets. */
    class PacketTooLarge : Exception("Packet length rejected")
    interface Port {
        val payloadBytes: Int
        suspend fun control(command: Int, payload: ByteArray)
        suspend fun data(bytes: ByteArray)
        suspend fun await(command: Int, timeoutMs: Long): Frame
    }
    data class Progress(val uniqueBytesQueued: Int, val totalBytes: Int, val block: Int, val packetBytes: Int)

    suspend fun upload(bytes: ByteArray, port: Port, progress: (Progress) -> Unit, log: (String) -> Unit): String = withTimeout(600_000L) {
        require(bytes.size in 18..2 * 1024 * 1024)
        val totalBlocks = (bytes.size + BLOCK_BYTES - 1) / BLOCK_BYTES
        val attempts = IntArray(totalBlocks); val sent = BooleanArray(totalBlocks)
        var queued = 0; var packet = port.payloadBytes.coerceIn(20, 244)
        port.control(0xba, byteArrayOf(1))
        try { port.await(0xba, 4000L); log("Watch replied to upload-mode command (BA)") }
        catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            log("No BA reply; continuing as in the captured sequence")
        }
        port.control(0x74, sizePayload(bytes.size))
        var checksum: String
        while (true) {
            currentCoroutineContext().ensureActive()
            val reply = port.await(0x74, 20_000L).payload
            require(reply.size >= 2) { "Truncated block reply" }
            val index = ((reply[0].toInt() and 255) shl 8) or (reply[1].toInt() and 255)
            if (index == 0xffff) {
                require(reply.size >= 4 && sent.all { it }) { "Watch finished before all blocks were served" }
                checksum = reply.copyOfRange(2, 4).joinToString("") { "%02x".format(it.toInt() and 255) }
                log("Watch reports transfer complete; check=$checksum (algorithm unknown)")
                break
            }
            require(index in sent.indices) { "Watch requested invalid block $index" }
            require(++attempts[index] <= 3) { "Too many requests for block $index" }
            log("Watch requested block ${index + 1}/$totalBlocks (attempt ${attempts[index]})")
            val start = index * BLOCK_BYTES; val end = minOf(start + BLOCK_BYTES, bytes.size)
            var offset = start
            while (offset < end) {
                currentCoroutineContext().ensureActive()
                val count = minOf(packet, end - offset)
                try { port.data(bytes.copyOfRange(offset, offset + count)); offset += count }
                catch (large: PacketTooLarge) {
                    if (packet <= 20) throw large
                    packet = 20
                    log("Explicit packet-size rejection; retrying SAME offset with 20-byte packets")
                }
            }
            if (!sent[index]) { queued += end - start; sent[index] = true }
            progress(Progress(queued, bytes.size, index + 1, packet))
        }
        // These commands are reproduced from the capture; their undocumented fields are unchanged.
        port.control(0x74, byteArrayOf(0, 0, 0, 0))
        port.control(0xb4, byteArrayOf(0x11, 0xb5.toByte(), 0x11, 0, 0))
        port.control(0x19, byteArrayOf(0x0b))
        checksum
    }
}
