package dev.citali.bolttstudio.codec

import org.junit.Assert.*
import org.junit.Test

class ClockDesignTest {
    @Test fun independentEditingLeavesOtherGroupUntouched() {
        val d = ClockDesign()
        val changed = d.resized(true, true, 70, 150, 12).move(true, true, -20, 30)
        assertEquals(d.minutes, changed.minutes)
        assertEquals(70, changed.hours.width); assertEquals(150, changed.hours.height)
    }
    @Test fun linkedMovePreservesOffsetAndStaysOnScreen() {
        val d = ClockDesign()
        val moved = d.move(true, false, 10000, -10000)
        assertEquals(d.minutes.y - d.hours.y, moved.minutes.y - moved.hours.y)
        assertEquals(0, moved.hours.y)
        assertEquals(240, moved.hours.x + moved.hours.span)
    }
    @Test fun linkedSizingAndArrangementsFitAllSlots() {
        val d = ClockDesign().resized(true, false, 999, 999, 24)
        assertEquals(d.hours.width, d.minutes.width)
        for (stack in listOf(true, false)) {
            val arranged = d.arrange(stack)
            assertFalse(arranged.overlaps)
            arranged.positions.forEachIndexed { i, p ->
                val g = if (i < 2) arranged.hours else arranged.minutes
                assertTrue(p.x >= 0 && p.y >= 0 && p.x + g.width <= 240 && p.y + g.height <= 296)
            }
        }
    }
    @Test fun brushInterpolatesFastStrokesAndRestores() {
        val original = ClockMask.empty()
        val mask = ClockMask.stroke(original, 10f, 20f, 120f, 20f, 3f, false, false)
        for (x in 10..120) assertEquals(0, mask[20 * 240 + x].toInt())
        assertEquals(255, original[20 * 240 + 50].toInt() and 255)
        val restored = ClockMask.stroke(mask, 50f, 20f, 50f, 20f, 3f, true, false)
        assertEquals(255, restored[20 * 240 + 50].toInt() and 255)
    }
    @Test fun brushFeathersAndClipsAtEdges() {
        val mask = ClockMask.stroke(ClockMask.empty(), 0f, 0f, 0f, 0f, 10f, false, true)
        assertEquals(0, mask[0].toInt())
        assertTrue((mask[9].toInt() and 255) in 1..254)
        assertEquals(255, mask[100].toInt() and 255)
    }
    @Test fun sameScreenHoleDoesNotRepeatAtOtherDigitPositions() {
        val mask = ClockMask.stroke(ClockMask.empty(), 21f, 21f, 21f, 21f, 3f, false, false)
        val glyph = FaceImage(10, 10, IntArray(100) { -1 })
        val a = ClockMask.apply(glyph, Point(20, 20), mask)
        val b = ClockMask.apply(glyph, Point(80, 20), mask)
        assertEquals(0, a.pixels[11]); assertEquals(-1, b.pixels[11])
    }
    @Test fun fourLiveTablesWithVariableSizesRoundtrip() {
        val base = DepthProbe.create(false).face
        val positions = listOf(Point(0, 0), Point(35, 0), Point(0, 140), Point(70, 140))
        val tables = (0..3).map { slot -> (0..9).map { digit ->
            FaceImage(if (slot < 2) 30 else 60, if (slot < 2) 120 else 90,
                IntArray((if (slot < 2) 30 * 120 else 60 * 90)) { if (it % 10 == digit) -1 else 0 })
        } }
        val mask = ClockMask.stroke(ClockMask.empty(), 12f, 0f, 12f, 200f, 5f, false, true)
        val face = base.copy(digits = tables.mapIndexed { slot, set -> set.map { ClockMask.apply(it, positions[slot], mask) } },
            positions = positions, sets = listOf(0, 1, 2, 3))
        val restored = MoyFace.parse(MoyFace.build(face))
        assertEquals(listOf(0, 1, 2, 3), restored.sets)
        for (slot in 0..3) for (digit in 0..9)
            assertArrayEquals(face.digits[slot][digit].pixels, restored.digits[slot][digit].pixels)
        assertArrayEquals(base.background.pixels, restored.background.pixels)
    }
}
