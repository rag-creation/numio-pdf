package com.rr.numio.pdf

import com.rr.numio.pdf.data.Library
import com.rr.numio.pdf.data.RecentCodec
import com.rr.numio.pdf.data.RecentFile
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {

    private fun file(uri: String, opened: Long, starred: Boolean = false) =
        RecentFile(uri = uri, name = "$uri.pdf", pageCount = 10, lastOpened = opened, starred = starred)

    @Test
    fun codecRoundTripsAwkwardNames() {
        val files = listOf(
            RecentFile("content://a/1%20b", "Tab\there.pdf", 12, 3, 1000L, true, 2048L),
            RecentFile("content://a/2", "Line\nbreak & ക്ഷ.pdf", 1, 0, 5L, false, -1L),
        )
        assertEquals(files, RecentCodec.decode(RecentCodec.encode(files)))
    }

    @Test
    fun codecSkipsDamagedLines() {
        val good = RecentFile("content://x", "x.pdf", 2, 1, 9L, false, 10L)
        val text = "garbage\n" + RecentCodec.encode(listOf(good)) + "\na\tb\tnot-a-number\t0\t0\t0\t0"
        assertEquals(listOf(good), RecentCodec.decode(text))
        assertEquals(emptyList<RecentFile>(), RecentCodec.decode(""))
    }

    @Test
    fun openingMovesToTopAndKeepsStarAndPlace() {
        var files = listOf(file("a", 1), file("b", 2))
        files = Library.toggleStar(files, "a")
        files = Library.withPage(files, "a", 7)
        files = Library.opened(files, "a", "a.pdf", 100L, 10, now = 50)
        val a = files.first()
        assertEquals("a", a.uri)
        assertTrue(a.starred)
        assertEquals(7, a.lastPage)
        assertEquals(2, files.size)
    }

    @Test
    fun lastPageIsClampedWhenTheFileGotShorter() {
        val files = Library.withPage(listOf(file("a", 1)), "a", 9)
        assertEquals(2, Library.opened(files, "a", "a.pdf", 1L, 3, now = 5).first().lastPage)
    }

    @Test
    fun oldUnstarredFilesAreTrimmedButStarredStay() {
        var files = listOf(file("starred-old", 0, starred = true))
        for (i in 1..Library.MAX_RECENT + 5) files = Library.opened(files, "f$i", "f$i", 1L, 1, now = i.toLong())
        assertEquals(Library.MAX_RECENT + 1, files.size)
        assertTrue(files.any { it.uri == "starred-old" })
        assertFalse(files.any { it.uri == "f1" })
    }

    @Test
    fun searchNeedsEveryWordInAnyOrder() {
        assertTrue(Library.matches("Car insurance policy.pdf", "policy car"))
        assertTrue(Library.matches("Car insurance policy.pdf", "  "))
        assertFalse(Library.matches("Car insurance policy.pdf", "car visa"))
        assertTrue(Library.matches("VISA renewal.pdf", "visa"))
    }

    @Test
    fun whenLabels() {
        val day = 86_400_000L
        val now = 1_762_400_000_000L // Thu 6 Nov 2025, 03:33 UTC
        val utc = ZoneOffset.UTC
        val en = Locale.ENGLISH
        assertEquals("Today", Library.whenLabel(now, now - 1000, utc, en))
        assertEquals("Yesterday", Library.whenLabel(now, now - day, utc, en))
        assertEquals("Mon", Library.whenLabel(now, now - 3 * day, utc, en))
        assertEquals("27 Oct", Library.whenLabel(now, now - 10 * day, utc, en))
        assertEquals("6 Nov 2024", Library.whenLabel(now, now - 365 * day, utc, en))
    }

    @Test
    fun progressAndLabels() {
        assertEquals(0.25f, Library.progress(RecentFile("u", "n", pageCount = 12, lastPage = 2)), 0.0001f)
        assertEquals(0f, Library.progress(RecentFile("u", "n", pageCount = 0)), 0f)
        assertEquals("1 page", Library.pagesLabel(1))
        assertEquals("18 pages", Library.pagesLabel(18))
    }
}
