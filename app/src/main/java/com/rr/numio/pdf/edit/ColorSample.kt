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
        val counts = HashMap<Int, Int>()
        fun count(x: Int, y: Int) {
            val c = quantise(pixels[y * width + x])
            counts[c] = (counts[c] ?: 0) + 1
        }
        for (x in l..r) {
            count(x, t)
            count(x, b)
        }
        for (y in t..b) {
            count(l, y)
            count(r, y)
        }
        val background = counts.maxByOrNull { it.value }?.key ?: 0xFFFFFF

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
