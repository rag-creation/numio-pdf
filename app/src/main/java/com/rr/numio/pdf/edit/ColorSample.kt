package com.rr.numio.pdf.edit

/**
 * Finds the background and text colours of a spot on a page from its pixels,
 * so a text change can be previewed in the right colours (white on red, etc).
 * Pure Kotlin on an ARGB pixel array, unit-tested.
 */
object ColorSample {

    /**
     * [pixels] is a [width]-wide ARGB image. The box runs from ([left], [top]) to
     * ([right], [bottom]) in pixels. Returns (background, text) as 0xRRGGBB.
     */
    fun colors(pixels: IntArray, width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): Pair<Int, Int> {
        val l = left.coerceIn(0, width - 1)
        val r = right.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val b = bottom.coerceIn(0, height - 1)
        if (r <= l || b <= t) return 0xFFFFFF to 0x000000

        // Background: the most common colour around the edge of the box
        // (grouped loosely), then the exact average of that group, so a
        // cover painted in it doesn't show as a slightly different patch
        val counts = HashMap<Int, Int>()
        val sums = HashMap<Int, LongArray>()
        fun count(x: Int, y: Int) {
            val p = pixels[y * width + x]
            val c = quantise(p)
            counts[c] = (counts[c] ?: 0) + 1
            val s = sums.getOrPut(c) { LongArray(3) }
            s[0] += ((p shr 16) and 0xFF).toLong()
            s[1] += ((p shr 8) and 0xFF).toLong()
            s[2] += (p and 0xFF).toLong()
        }
        for (x in l..r) {
            count(x, t)
            count(x, b)
        }
        for (y in t..b) {
            count(l, y)
            count(r, y)
        }
        val group = counts.maxByOrNull { it.value } ?: return 0xFFFFFF to 0x000000
        val s = sums.getValue(group.key)
        val n = group.value.toLong()
        val background = ((s[0] / n).toInt() shl 16) or ((s[1] / n).toInt() shl 8) or (s[2] / n).toInt()

        // Text: the colour inside the box furthest from the background
        var text = if (luminance(background) > 0.5f) 0x000000 else 0xFFFFFF
        var far = 0
        for (y in t..b) for (x in l..r) {
            val c = pixels[y * width + x] and 0xFFFFFF
            val d = distance(c, background)
            if (d > far) {
                far = d
                text = c
            }
        }
        return background to text
    }

    /** Rounds colours a little so anti-aliased edges count as the same colour. */
    private fun quantise(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF) and 0xF8
        val g = ((argb shr 8) and 0xFF) and 0xF8
        val b = (argb and 0xFF) and 0xF8
        return (r shl 16) or (g shl 8) or b
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun luminance(c: Int): Float =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
}
