package com.rr.numio.pdf

import com.rr.numio.pdf.edit.ColorSample
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSampleTest {
    private fun image(w: Int, h: Int, bg: Int, ink: Int, inkRect: IntArray): IntArray {
        val px = IntArray(w * h) { 0xFF000000.toInt() or bg }
        for (y in inkRect[1]..inkRect[3]) for (x in inkRect[0]..inkRect[2]) px[y * w + x] = 0xFF000000.toInt() or ink
        return px
    }

    @Test
    fun whiteTextOnRed() {
        val red = 0xB83A4B
        val px = image(40, 20, red, 0xFFFFFF, intArrayOf(10, 6, 30, 14))
        val (bg, text) = ColorSample.colors(px, 40, 20, 5, 2, 35, 18)
        assertEquals(red, bg)
        assertEquals(0xFFFFFF, text)
    }

    @Test
    fun blackTextOnWhite() {
        val px = image(40, 20, 0xFFFFFF, 0x111111, intArrayOf(10, 6, 30, 14))
        val (bg, text) = ColorSample.colors(px, 40, 20, 5, 2, 35, 18)
        assertEquals(0xFFFFFF, bg)
        assertEquals(0x111111, text)
    }

    @Test
    fun emptyBoxFallsBackToWhite() {
        val px = IntArray(4) { -1 }
        assertEquals(0xFFFFFF to 0x000000, ColorSample.colors(px, 2, 2, 1, 1, 1, 1))
    }
}
