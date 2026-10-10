package dev.citali.bolttstudio.codec

import org.junit.Assert.*
import org.junit.Test

class PhotoDepthTest {
    private fun design(): MoyFace.Face {
        val base = DepthProbe.create(false).face
        val bg = FaceImage(240, 296, IntArray(240 * 296) { if (it % 240 < 120) 0xffff0000.toInt() else 0xff0000ff.toInt() })
        val p = listOf(Point(5, 20), Point(40, 20), Point(90, 180), Point(140, 180))
        val tables = (0..3).map { i -> (0..9).map { digit ->
            val w = if (i < 2) 25 else 40; val h = if (i < 2) 90 else 60
            FaceImage(w, h, IntArray(w * h) { if (it % 10 == digit) -1 else 0 })
        } }
        return base.copy(background = bg, digits = tables, positions = p, sets = listOf(0, 1, 2, 3))
    }
    @Test fun photoDepthPreservesBackgroundGeometryAndEveryLiveGlyph() {
        val source = design()
        val built = DepthProbe.fromEditor(source, listOf(2, 3, 5, 8), true, 205)
        val decoded = MoyFace.parse(built.bytes)
        assertArrayEquals(source.background.pixels, decoded.background.pixels)
        assertEquals(source.positions, decoded.positions); assertEquals(source.sets, decoded.sets)
        for (slot in 0..3) for (digit in 0..9) {
            assertArrayEquals(source.digits[slot][digit].pixels, decoded.digits[slot][digit].pixels)
            assertEquals(source.digits[slot][digit].width, decoded.digits[slot][digit].width)
            assertEquals(source.digits[slot][digit].height, decoded.digits[slot][digit].height)
        }
        assertEquals(Point(12, 205), decoded.foreground!!.position)
    }
    @Test fun disablingStripeRemovesOnlyTheExtraImage() {
        val source = design()
        val yes = DepthProbe.fromEditor(source, listOf(1, 0, 0, 9), true)
        val no = DepthProbe.fromEditor(source, listOf(1, 0, 0, 9), false)
        assertNull(MoyFace.parse(no.bytes).foreground)
        assertArrayEquals(yes.face.background.pixels, no.face.background.pixels)
        assertEquals(yes.face.digits, no.face.digits)
        assertFalse(yes.simulatedPreview.pixels.contentEquals(no.simulatedPreview.pixels))
    }
    @Test fun changingPreviewTimeDoesNotFreezeOrChangeLiveTables() {
        val source = design()
        val a = DepthProbe.fromEditor(source, listOf(1, 0, 0, 9), true)
        val b = DepthProbe.fromEditor(source, listOf(2, 3, 5, 8), true)
        assertEquals(a.face.digits, b.face.digits)
        assertEquals(a.face.positions, b.face.positions)
        assertArrayEquals(a.face.background.pixels, b.face.background.pixels)
        assertFalse(a.simulatedPreview.pixels.contentEquals(b.simulatedPreview.pixels))
    }
    @Test fun validatesPreviewAndStripeAndFitsBottomEdge() {
        val source = design()
        assertTrue(runCatching { DepthProbe.fromEditor(source, listOf(1, 2), true) }.isFailure)
        assertTrue(runCatching { DepthProbe.fromEditor(source, listOf(1, 2, 3, 10), true) }.isFailure)
        assertTrue(runCatching { DepthProbe.fromEditor(source, listOf(1, 2, 3, 4), true, 279) }.isFailure)
        val layer = MoyFace.parse(DepthProbe.fromEditor(source, listOf(1, 2, 3, 4), true, 278).bytes).foreground!!
        assertEquals(296, layer.position.y + layer.image.height)
    }
}
