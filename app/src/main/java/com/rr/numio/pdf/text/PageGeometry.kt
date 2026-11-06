package com.rr.numio.pdf.text

/**
 * Converts between PDF page coordinates (points, origin bottom-left, before
 * the page's /Rotate) and the fractions of the page as it is shown (origin top
 * left, after rotating), which is how PDF Reader stores positions.
 */
data class PageGeometry(
    val llx: Float,
    val lly: Float,
    val urx: Float,
    val ury: Float,
    val rotation: Int,
) {
    private val w = (urx - llx).takeIf { it > 0f } ?: 1f
    private val h = (ury - lly).takeIf { it > 0f } ?: 1f
    private val turn = ((rotation % 360) + 360) % 360

    /** Shown fractions (u across, v down) to PDF points. */
    fun toPdf(u: Float, v: Float): Pair<Float, Float> {
        val (px, py) = when (turn) {
            90 -> v * w to (1f - u) * h
            180 -> (1f - u) * w to (1f - v) * h
            270 -> (1f - v) * w to u * h
            else -> u * w to v * h
        }
        return (llx + px) to (ury - py)
    }

    /** PDF points to shown fractions (u across, v down). */
    fun toShown(x: Float, y: Float): Pair<Float, Float> {
        val px = (x - llx) / w
        val py = (ury - y) / h
        return when (turn) {
            90 -> (1f - py) to px
            180 -> (1f - px) to (1f - py)
            270 -> py to (1f - px)
            else -> px to py
        }
    }

    /** A box of shown fractions around any four PDF points (a QuadPoints group). */
    fun boxAround(points: FloatArray, from: Int = 0): NBox {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (k in 0 until 4) {
            val (u, v) = toShown(points[from + k * 2], points[from + k * 2 + 1])
            l = minOf(l, u); r = maxOf(r, u); t = minOf(t, v); b = maxOf(b, v)
        }
        return NBox(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    /**
     * QuadPoints for one shown box, in the order PDF readers expect:
     * upper-left, upper-right, lower-left, lower-right (as the text reads).
     */
    fun quad(box: NBox): FloatArray {
        val ul = toPdf(box.left, box.top)
        val ur = toPdf(box.right, box.top)
        val ll = toPdf(box.left, box.bottom)
        val lr = toPdf(box.right, box.bottom)
        return floatArrayOf(ul.first, ul.second, ur.first, ur.second, ll.first, ll.second, lr.first, lr.second)
    }
}
