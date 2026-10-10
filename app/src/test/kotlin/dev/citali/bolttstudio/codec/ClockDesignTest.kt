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
    @Test fun normalDigitalFaceUsesTwoTablesForAllFourLivePositions() {
        val original = javaClass.classLoader!!.getResourceAsStream("demo_face.bin")!!.use { MoyFace.parse(it.readBytes()) }
        val design = ClockDesign()
        val face = original.copy(digits = listOf(original.digits.first(), original.digits.first()),
            positions = design.positions, sets = listOf(0, 0, 1, 1))
        val decoded = MoyFace.parse(MoyFace.build(face))
        assertEquals(2, decoded.digits.size)
        assertEquals(listOf(0, 0, 1, 1), decoded.sets)
        assertEquals(design.positions, decoded.positions)
        for (table in decoded.digits) for (glyph in table) assertTrue(glyph.pixels.any { it ushr 24 > 0 })
    }
}
