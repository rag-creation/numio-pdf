package com.rr.numio.pdf.edit

import com.rr.numio.pdf.text.NBox
import kotlin.math.hypot

/** Pen strokes: smoothing out shaky points and sizing. Pure Kotlin, unit-tested. */
object Ink {
    /** Pen sizes as fractions of the page width: fine, medium, bold. */
    val widths = listOf(0.003f, 0.006f, 0.012f)

    /**
     * Drops points that add nothing (Ramer–Douglas–Peucker), so a stroke saves
     * small while keeping its shape. [points] is u0, v0, u1, v1, …
     */
    fun simplify(points: FloatArray, tolerance: Float = 0.0007f): FloatArray {
        val n = points.size / 2
        if (n <= 2) return points.copyOf()
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var far = -1
            var farDist = 0f
            for (i in a + 1 until b) {
                val d = distanceToSegment(points, i, a, b)
                if (d > farDist) {
                    farDist = d
                    far = i
                }
            }
            if (far >= 0 && farDist > tolerance) {
                keep[far] = true
                stack.addLast(a to far)
                stack.addLast(far to b)
            }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) {
            out += points[i * 2]
            out += points[i * 2 + 1]
        }
        return out.toFloatArray()
    }

    private fun distanceToSegment(p: FloatArray, i: Int, a: Int, b: Int): Float {
        val x = p[i * 2]; val y = p[i * 2 + 1]
        val x1 = p[a * 2]; val y1 = p[a * 2 + 1]
        val x2 = p[b * 2]; val y2 = p[b * 2 + 1]
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(x - x1, y - y1)
        val t = (((x - x1) * dx + (y - y1) * dy) / len2).coerceIn(0f, 1f)
        return hypot(x - (x1 + t * dx), y - (y1 + t * dy))
    }

    /** The box around all strokes, grown by half the pen width so thick lines fit. */
    fun bounds(strokes: List<FloatArray>, width: Float): NBox {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        strokes.forEach { s ->
            var i = 0
            while (i + 1 < s.size) {
                l = minOf(l, s[i]); r = maxOf(r, s[i])
                t = minOf(t, s[i + 1]); b = maxOf(b, s[i + 1])
                i += 2
            }
        }
        if (l > r) return NBox(0f, 0f, 0f, 0f)
        val pad = width / 2f
        return NBox(l - pad, t - pad, r + pad, b + pad)
    }
}
