package com.rr.numio.pdf

import com.rr.numio.pdf.pages.PagePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePlanTest {

    @Test
    fun startsUnchanged() {
        val items = PagePlan.start(4)
        assertEquals(listOf(0, 1, 2, 3), items.map { it.index })
        assertFalse(PagePlan.changed(items, 4))
        assertEquals("", PagePlan.summary(items, 4))
    }

    @Test
    fun rotatesBothWaysAndBackToStart() {
        var items = PagePlan.start(3)
        items = PagePlan.rotate(items, setOf(1L), 1)
        assertEquals(listOf(0, 1, 0), items.map { it.turn })
        assertTrue(PagePlan.changed(items, 3))
        items = PagePlan.rotate(items, setOf(1L), -1)
        assertEquals(listOf(0, 0, 0), items.map { it.turn })
        assertFalse(PagePlan.changed(items, 3))
        items = PagePlan.rotate(items, setOf(0L), -1)
        assertEquals(3, items[0].turn) // a quarter anticlockwise is three clockwise
    }

    @Test
    fun deletesAndRemembersWherePagesWent() {
        var items = PagePlan.start(5)
        items = PagePlan.delete(items, setOf(1L, 3L))
        assertEquals(listOf(0, 2, 4), items.map { it.index })
        assertEquals(mapOf(0 to 0, 2 to 1, 4 to 2), PagePlan.moved(items))
        assertEquals("2 pages deleted", PagePlan.summary(items, 5))
    }

    @Test
    fun movesAPage() {
        var items = PagePlan.start(4)
        items = PagePlan.move(items, 3, 0)
        assertEquals(listOf(3, 0, 1, 2), items.map { it.index })
        assertEquals(0, PagePlan.moved(items)[3])
        assertEquals("Pages moved", PagePlan.summary(items, 4))
        // Moving it back is no change at all
        items = PagePlan.move(items, 0, 3)
        assertFalse(PagePlan.changed(items, 4))
        // Out of range does nothing
        assertEquals(items, PagePlan.move(items, 0, 9))
    }

    @Test
    fun addsAnotherPdfAtTheEnd() {
        var items = PagePlan.start(2)
        items = PagePlan.add(items, source = 1, count = 3, firstKey = 2)
        assertEquals(listOf(0, 0, 1, 1, 1), items.map { it.source })
        assertEquals(5, items.map { it.key }.toSet().size)
        assertEquals("3 pages added", PagePlan.summary(items, 2))
        // Pages of the added PDF keep their own numbers
        assertEquals(listOf(0, 1, 2), items.filter { it.source == 1 }.map { it.index })
    }

    @Test
    fun selectedPagesFollowTheArrangement() {
        var items = PagePlan.start(4)
        items = PagePlan.move(items, 2, 0)
        assertEquals(listOf(2, 0), PagePlan.selected(items, setOf(0L, 2L)).map { it.index })
    }
}
