package dev.citali.bolttstudio.codec

/** Screen-pixel viewport; mask coordinates always remain native 240x296 pixels. */
data class CanvasViewport(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    fun scale(width: Float, height: Float) = minOf(width / 240f, height / 296f) * zoom
    fun origin(width: Float, height: Float): Pair<Float, Float> {
        val s = scale(width, height)
        return (width - 240 * s) / 2 + panX to (height - 296 * s) / 2 + panY
    }
    fun imagePoint(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> {
        val (left, top) = origin(width, height); val s = scale(width, height)
        require(s > 0)
        return (x - left) / s to (y - top) / s
    }
    fun resized(oldWidth: Float, oldHeight: Float, width: Float, height: Float): CanvasViewport {
        require(oldWidth > 0 && oldHeight > 0 && width > 0 && height > 0)
        val ratio = scale(width, height) / scale(oldWidth, oldHeight)
        return copy(panX = panX * ratio, panY = panY * ratio)
    }
    fun transform(oldX: Float, oldY: Float, newX: Float, newY: Float, factor: Float, width: Float, height: Float): CanvasViewport {
        require(width > 0 && height > 0 && factor.isFinite() && factor > 0)
        val (ix, iy) = imagePoint(oldX, oldY, width, height)
        val next = copy(zoom = (zoom * factor).coerceIn(1f, 12f))
        val s = next.scale(width, height)
        val px = newX - ix * s - (width - 240 * s) / 2
        val py = newY - iy * s - (height - 296 * s) / 2
        // Keep at least half the canvas visible/reachable; Fit is always available.
        return next.copy(panX = px.coerceIn(-240 * s / 2, 240 * s / 2), panY = py.coerceIn(-296 * s / 2, 296 * s / 2))
    }
}

/** Copies at every boundary: draft undo/redo can never mutate the editor's committed mask. */
class MaskHistory(initial: ByteArray, private val limit: Int = 20) {
    init { require(initial.size == 240 * 296 && limit > 0) }
    var current: ByteArray = initial.clone(); private set
    private val undo = ArrayDeque<ByteArray>()
    private val redo = ArrayDeque<ByteArray>()
    val undoCount get() = undo.size
    val redoCount get() = redo.size
    fun replacePreview(mask: ByteArray) { require(mask.size == 240 * 296); current = mask.clone() }
    fun finishStroke(before: ByteArray) {
        if (before.contentEquals(current)) return
        if (undo.size == limit) undo.removeFirst()
        undo.addLast(before.clone()); redo.clear()
    }
    fun undo() { if (undo.isNotEmpty()) { redo.addLast(current.clone()); current = undo.removeLast() } }
    fun redo() { if (redo.isNotEmpty()) { undo.addLast(current.clone()); current = redo.removeLast() } }
    fun clear() { val before = current; current = ClockMask.empty(); finishStroke(before) }
}
