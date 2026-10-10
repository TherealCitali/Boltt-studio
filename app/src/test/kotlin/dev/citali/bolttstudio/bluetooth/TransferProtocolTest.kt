package dev.citali.bolttstudio.bluetooth

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TransferProtocolTest {
    private fun request(index: Int) = TransferProtocol.Frame(0x74,
        if (index == 0xffff) byteArrayOf(-1, -1, 0x12, 0x34) else byteArrayOf((index ushr 8).toByte(), index.toByte()))
    private class FakePort(override val payloadBytes: Int = 244, ack: Boolean = true) : TransferProtocol.Port {
        val frames = Channel<TransferProtocol.Frame>(Channel.UNLIMITED)
        val controls = mutableListOf<Pair<Int, ByteArray>>()
        val accepted = mutableListOf<ByteArray>()
        var rejectLargeOnce = false
        var failWrite = false
        var attemptedWrites = 0
        init { if (ack) frames.trySend(TransferProtocol.Frame(0xba, byteArrayOf(1))) }
        override suspend fun control(command: Int, payload: ByteArray) { controls += command to payload.clone() }
        override suspend fun data(bytes: ByteArray) {
            attemptedWrites++
            if (failWrite) error("ambiguous transport failure")
            if (rejectLargeOnce && bytes.size > 20) { rejectLargeOnce = false; throw TransferProtocol.PacketTooLarge() }
            accepted += bytes.clone()
        }
        override suspend fun await(command: Int, timeoutMs: Long): TransferProtocol.Frame = withTimeout(timeoutMs) {
            var frame = frames.receive()
            while (frame.command != command) frame = frames.receive()
            frame
        }
        fun add(frame: TransferProtocol.Frame) { check(frames.trySend(frame).isSuccess) }
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        var thrown = false
        try { block() } catch (_: IllegalArgumentException) { thrown = true }
        assertTrue("Expected invalid watch reply to abort", thrown)
    }
    @Test fun framingHandlesSplitCoalescedAndNoise() {
        val a = TransferProtocol.frame(0xba, byteArrayOf(1))
        assertArrayEquals(byteArrayOf(-2, -22, 0x20, 6, -70, 1), a)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), TransferProtocol.sizePayload(0x010203))
        val b = TransferProtocol.frame(0x74, byteArrayOf(0, 2))
        val decoder = TransferProtocol.Decoder()
        assertTrue(decoder.feed(byteArrayOf(7, 9) + a.copyOfRange(0, 3)).isEmpty())
        val frames = decoder.feed(a.copyOfRange(3, a.size) + b)
        assertEquals(listOf(0xba, 0x74), frames.map { it.command })
        assertArrayEquals(byteArrayOf(0, 2), frames.last().payload)
        val noise = byteArrayOf(-2, -22, -1, 0, 7)
        assertEquals(1, decoder.feed(noise + a).size)
        try { decoder.feed(ByteArray(4097)); fail("Unbounded notification accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun sendsExactBytesAndFinalSequence() = runTest {
        val bytes = ByteArray(TransferProtocol.BLOCK_BYTES + 77) { (it * 31).toByte() }
        val port = FakePort(517)
        port.add(request(0)); port.add(request(1)); port.add(request(0xffff))
        val progress = mutableListOf<TransferProtocol.Progress>()
        assertEquals("1234", TransferProtocol.upload(bytes, port, progress::add, {}))
        assertArrayEquals(bytes, port.accepted.fold(byteArrayOf()) { a, b -> a + b })
        assertTrue(port.accepted.all { it.size <= 244 })
        assertEquals(listOf(0xba, 0x74, 0x74, 0xb4, 0x19), port.controls.map { it.first })
        assertArrayEquals(byteArrayOf(0x11, -75, 0x11, 0, 0), port.controls[3].second)
        assertEquals(bytes.size, progress.last().uniqueBytesQueued)
    }
    @Test fun explicitLengthRejectionRetriesSameOffset() = runTest {
        val bytes = ByteArray(391) { it.toByte() }; val port = FakePort()
        port.rejectLargeOnce = true; port.add(request(0)); port.add(request(0xffff))
        TransferProtocol.upload(bytes, port, {}, {})
        assertArrayEquals(bytes, port.accepted.fold(byteArrayOf()) { a, b -> a + b })
        assertTrue(port.accepted.all { it.size <= 20 })
    }
    @Test fun defaultMtuUsesTwentyBytePackets() = runTest {
        val port = FakePort(20); port.add(request(0)); port.add(request(0xffff))
        TransferProtocol.upload(ByteArray(47), port, {}, {})
        assertEquals(listOf(20, 20, 7), port.accepted.map { it.size })
    }
    @Test fun repeatsDoNotInflateProgress() = runTest {
        val port = FakePort(); listOf(0, 0, 0xffff).forEach { port.add(request(it)) }
        val progress = mutableListOf<Int>()
        TransferProtocol.upload(ByteArray(47), port, { progress += it.uniqueBytesQueued }, {})
        assertEquals(listOf(47, 47), progress)
    }
    @Test fun rejectsEarlyDoneInvalidBlocksAndExcessiveRetries() = runTest {
        for (requests in listOf(listOf(0xffff), listOf(8), listOf(0, 0, 0, 0))) {
            val port = FakePort(); requests.forEach { port.add(request(it)) }
            rejected { TransferProtocol.upload(ByteArray(47), port, {}, {}) }
            assertFalse(port.controls.any { it.first == 0xb4 })
        }
        val truncated = FakePort(); truncated.add(TransferProtocol.Frame(0x74, byteArrayOf(0)))
        rejected { TransferProtocol.upload(ByteArray(47), truncated, {}, {}) }
    }
    @Test fun ambiguousWriteFailureNeverRetries() = runTest {
        val port = FakePort(); port.failWrite = true; port.add(request(0))
        try { TransferProtocol.upload(ByteArray(47), port, {}, {}); fail("Failure ignored") }
        catch (_: IllegalStateException) {}
        assertEquals(1, port.attemptedWrites)
        assertFalse(port.controls.any { it.first == 0xb4 })
    }
    @Test fun missingBlockRequestTimesOutWithoutFinalization() = runTest {
        val port = FakePort()
        try { TransferProtocol.upload(ByteArray(47), port, {}, {}); fail("Timeout ignored") }
        catch (_: TimeoutCancellationException) {}
        assertFalse(port.controls.any { it.first == 0xb4 })
    }
    @Test fun missingOptionalBaAckStillAllowsCapturedSequence() = runTest {
        val port = FakePort(ack = false)
        launch { delay(4001); port.add(request(0)); port.add(request(0xffff)) }
        assertEquals("1234", TransferProtocol.upload(ByteArray(47), port, {}, {}))
    }
    @Test fun cancellationStopsBeforeFinalization() = runTest {
        val port = FakePort()
        val task = launch { TransferProtocol.upload(ByteArray(47), port, {}, {}) }
        yield(); task.cancelAndJoin()
        assertFalse(port.controls.any { it.first == 0xb4 })
    }
}
