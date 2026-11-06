package com.rr.numio.pdf

import com.rr.numio.pdf.data.Bookmark
import com.rr.numio.pdf.data.Bookmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksTest {
    @Test
    fun toggleAddsInPageOrderAndRemoves() {
        var b = Bookmarks.toggle(emptyList(), 9)
        b = Bookmarks.toggle(b, 2)
        assertEquals(listOf(2, 9), b.map { it.page })
        assertEquals("Page 3", b.first().name)
        b = Bookmarks.toggle(b, 9)
        assertEquals(listOf(2), b.map { it.page })
    }

    @Test
    fun renameKeepsADefaultWhenBlank() {
        val b = listOf(Bookmark(4, "Page 5"))
        assertEquals("Chapter 2", Bookmarks.rename(b, 4, "  Chapter 2 ").single().name)
        assertEquals("Page 5", Bookmarks.rename(b, 4, "   ").single().name)
    }

    @Test
    fun codecRoundTripsAndSkipsDamage() {
        val b = listOf(Bookmark(0, "Tab\there"), Bookmark(12, "അദ്ധ്യായം 1"))
        assertEquals(b, Bookmarks.decode(Bookmarks.encode(b)))
        assertEquals(b, Bookmarks.decode("x\n" + Bookmarks.encode(b) + "\nnope\tname"))
        assertTrue(Bookmarks.decode("").isEmpty())
    }
}
