package com.rr.numio.pdf

import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.text.PageGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {

    private val a4 = PageGeometry(0f, 0f, 595f, 842f, 0)

    @Test
    fun topLeftOfShownPageIsTopLeftOfPdfPage() {
        val (x, y) = a4.toPdf(0f, 0f)
        assertEquals(0f, x, 0.01f)
        assertEquals(842f, y, 0.01f)
    }

    @Test
    fun roundTripsForEveryRotation() {
        for (rot in listOf(0, 90, 180, 270, -90, 450)) {
            val g = PageGeometry(10f, 20f, 605f, 862f, rot)
            for ((u, v) in listOf(0.1f to 0.2f, 0.9f to 0.05f, 0.5f to 0.5f)) {
                val (x, y) = g.toPdf(u, v)
                val (u2, v2) = g.toShown(x, y)
                assertEquals("u at $rot", u, u2, 0.0001f)
                assertEquals("v at $rot", v, v2, 0.0001f)
            }
        }
    }

    @Test
    fun quadComesBackAsTheSameBox() {
        val box = NBox(0.1f, 0.2f, 0.6f, 0.23f)
        for (rot in listOf(0, 90, 180, 270)) {
            val g = PageGeometry(0f, 0f, 612f, 792f, rot)
            val back = g.boxAround(g.quad(box))
            assertEquals(box.left, back.left, 0.0001f)
            assertEquals(box.top, back.top, 0.0001f)
            assertEquals(box.right, back.right, 0.0001f)
            assertEquals(box.bottom, back.bottom, 0.0001f)
        }
    }

    @Test
    fun quadOrderIsUpperLeftUpperRightLowerLeftLowerRight() {
        val q = a4.quad(NBox(0f, 0f, 1f, 0.5f))
        // upper-left (0, 842), upper-right (595, 842), lower-left (0, 421), lower-right (595, 421)
        val expected = floatArrayOf(0f, 842f, 595f, 842f, 0f, 421f, 595f, 421f)
        expected.forEachIndexed { i, e -> assertEquals(e, q[i], 0.01f) }
    }
}
