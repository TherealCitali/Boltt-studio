package dev.citali.bolttstudio.codec

import org.junit.Assert.*
import org.junit.Test

class CanvasViewportTest {
    @Test fun fittedCanvasMapsBackToNativePixels() {
        val v = CanvasViewport()
        val (left, top) = v.origin(720f, 1000f)
        val p = v.imagePoint(left + 120 * 3, top + 148 * 3, 720f, 1000f)
        assertEquals(120f, p.first, .001f); assertEquals(148f, p.second, .001f)
    }
    @Test fun pinchKeepsPointUnderFingersAndPanningUsesDisplayPixels() {
        val v = CanvasViewport()
        val original = v.imagePoint(300f, 400f, 720f, 1000f)
        val next = v.transform(300f, 400f, 330f, 440f, 2f, 720f, 1000f)
        val mapped = next.imagePoint(330f, 440f, 720f, 1000f)
        assertEquals(original.first, mapped.first, .001f); assertEquals(original.second, mapped.second, .001f)
    }
    @Test fun resizedViewportKeepsSameNativeCenter() {
        val v = CanvasViewport(4f, 100f, -90f)
        val old = v.imagePoint(360f, 500f, 720f, 1000f)
        val next = v.resized(720f, 1000f, 1000f, 400f)
        val point = next.imagePoint(500f, 200f, 1000f, 400f)
        assertEquals(old.first, point.first, .001f); assertEquals(old.second, point.second, .001f)
    }
    @Test fun zoomAndPanAreBounded() {
        val v = CanvasViewport().transform(360f, 500f, 100000f, -100000f, 100f, 720f, 1000f)
        assertEquals(12f, v.zoom, 0f)
        assertTrue(kotlin.math.abs(v.panX) <= 240 * v.scale(720f, 1000f) / 2)
        assertEquals(1f, CanvasViewport().transform(0f, 0f, 0f, 0f, .1f, 720f, 1000f).zoom, 0f)
    }
    @Test fun draftHistoryCopiesInputsAndSupportsUndoRedo() {
        val original = ClockMask.empty(); val h = MaskHistory(original)
        val erased = ClockMask.stroke(original, 50f, 50f, 50f, 50f, 8f, false, false)
        h.replacePreview(erased); h.finishStroke(original); erased[0] = 0
        assertEquals(255, h.current[0].toInt() and 255)
        h.undo(); assertArrayEquals(original, h.current)
        h.redo(); assertEquals(0, h.current[50 * 240 + 50].toInt())
        assertEquals(255, original[50 * 240 + 50].toInt() and 255)
        h.clear(); assertArrayEquals(original, h.current)
        h.undo(); assertEquals(0, h.current[50 * 240 + 50].toInt())
    }
    @Test fun newStrokeClearsRedoAndNavigationRollbackDoesNotCommit() {
        val original = ClockMask.empty(); val h = MaskHistory(original)
        val stroke = ClockMask.stroke(original, 50f, 50f, 50f, 50f, 8f, false, false)
        h.replacePreview(stroke); h.replacePreview(original)
        assertEquals(0, h.undoCount)
        h.replacePreview(stroke); h.finishStroke(original); h.undo()
        h.replacePreview(stroke); h.finishStroke(original)
        assertEquals(0, h.redoCount)
    }
    @Test fun undoIsBoundedAndNoOpDoesNotConsumeHistory() {
        val original = ClockMask.empty(); val h = MaskHistory(original, 3)
        h.finishStroke(original); assertEquals(0, h.undoCount)
        repeat(7) { i -> val before = h.current.clone(); val after = before.clone(); after[i] = 0; h.replacePreview(after); h.finishStroke(before) }
        assertEquals(3, h.undoCount)
    }
}
