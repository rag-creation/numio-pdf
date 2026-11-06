package com.rr.numio.pdf

import com.rr.numio.pdf.edit.Ink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class InkTest {
    @Test
    fun straightLineKeepsOnlyItsEnds() {
        val line = FloatArray(40) { i -> if (i % 2 == 0) i / 40f else 0.5f }
        val s = Ink.simplify(line)
        assertEquals(4, s.size)
        assertEquals(line[0], s[0], 0f)
        assertEquals(line[38], s[2], 0f)
    }

    @Test
    fun cornersAreKept() {
        // An L: right then down
        val l = floatArrayOf(0.1f, 0.1f, 0.2f, 0.1f, 0.3f, 0.1f, 0.3f, 0.2f, 0.3f, 0.3f)
        assertArrayEquals(floatArrayOf(0.1f, 0.1f, 0.3f, 0.1f, 0.3f, 0.3f), Ink.simplify(l), 0f)
    }

    @Test
    fun aDotStaysADot() {
        val dot = floatArrayOf(0.5f, 0.5f)
        assertArrayEquals(dot, Ink.simplify(dot), 0f)
    }

    @Test
    fun boundsIncludeThePenWidth() {
        val b = Ink.bounds(listOf(floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f)), 0.02f)
        assertEquals(0.19f, b.left, 0.0001f)
        assertEquals(0.29f, b.top, 0.0001f)
        assertEquals(0.41f, b.right, 0.0001f)
        assertEquals(0.51f, b.bottom, 0.0001f)
    }
}
