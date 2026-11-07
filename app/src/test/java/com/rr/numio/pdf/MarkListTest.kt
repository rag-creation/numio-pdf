package com.rr.numio.pdf

import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkColors
import com.rr.numio.pdf.edit.MarkEntry
import com.rr.numio.pdf.edit.MarkFilter
import com.rr.numio.pdf.edit.MarkKind
import com.rr.numio.pdf.edit.MarkList
import com.rr.numio.pdf.text.Glyph
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.text.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkListTest {

    private fun mark(page: Int, kind: MarkKind, top: Float, text: String = "", color: Int = MarkColors.YELLOW) =
        Mark(page, kind, listOf(NBox(0.1f, top, 0.5f, top + 0.02f)), color, text)

    @Test
    fun sortsPageByPageTopToBottom() {
        val list = MarkList.sorted(
            listOf(
                MarkEntry(mark(2, MarkKind.NOTE, 0.1f, "c")),
                MarkEntry(mark(0, MarkKind.HIGHLIGHT, 0.5f, "b")),
                MarkEntry(mark(0, MarkKind.HIGHLIGHT, 0.2f, "a")),
            ),
        )
        assertEquals(listOf("a", "b", "c"), list.map { it.mark.text })
    }

    @Test
    fun filtersAndCounts() {
        val entries = listOf(
            MarkEntry(mark(0, MarkKind.HIGHLIGHT, 0.1f)),
            MarkEntry(mark(0, MarkKind.UNDERLINE, 0.2f)),
            MarkEntry(mark(0, MarkKind.STRIKEOUT, 0.3f)),
            MarkEntry(mark(1, MarkKind.SIGN, 0.3f)),
        )
        val counts = MarkList.counts(entries)
        assertEquals(4, counts[MarkFilter.ALL])
        assertEquals(2, counts[MarkFilter.LINE])
        assertEquals(1, counts[MarkFilter.SIGN])
        assertEquals(0, counts[MarkFilter.NOTE])
    }

    @Test
    fun namesColoursFromOtherAppsByTheNearestOne() {
        assertEquals("Yellow", MarkList.colourName(0xFFFF00))
        assertEquals("Blue", MarkList.colourName(0x5599FF))
        assertEquals("Highlight · Green", MarkList.title(mark(0, MarkKind.HIGHLIGHT, 0f, color = MarkColors.GREEN)))
        assertEquals("Signature", MarkList.title(mark(0, MarkKind.SIGN, 0f)))
    }

    @Test
    fun describesTextChanges() {
        assertEquals("Removed “Phone”", MarkList.detail(MarkEntry(mark(0, MarkKind.REPLACE, 0f, ""), "Phone")))
        assertEquals("“2024” → “2026”", MarkList.detail(MarkEntry(mark(0, MarkKind.REPLACE, 0f, "2026"), "2024")))
    }

    @Test
    fun exportsPageByPage() {
        val text = MarkList.export(
            "Notes.pdf",
            listOf(
                MarkEntry(mark(4, MarkKind.NOTE, 0.1f, "Ask about this")),
                MarkEntry(mark(0, MarkKind.HIGHLIGHT, 0.1f), words = "the main idea"),
            ),
        )
        assertTrue(text.startsWith("Marks in Notes.pdf\n2 marks\n"))
        assertTrue(text.contains("Page 1\n• Highlight · Yellow: “the main idea”\n"))
        assertTrue(text.contains("Page 5\n• Note · Yellow: Ask about this\n"))
        assertTrue(text.indexOf("Page 1") < text.indexOf("Page 5"))
    }

    @Test
    fun wordsUnderAHighlightAreRead() {
        val glyphs = ArrayList<Glyph>()
        var x = 0.1f
        "keep this part".forEach { c ->
            val box = NBox(x, 0.1f, x + 0.02f, 0.13f)
            glyphs += if (c == ' ') Glyph(" ", box, separator = true) else Glyph(c.toString(), box)
            x += 0.02f
        }
        val page = PageText(glyphs)
        // Covers "this part" (characters 5 to 13)
        val box = NBox(0.1f + 5 * 0.02f, 0.1f, 0.1f + 14 * 0.02f, 0.13f)
        assertEquals("this part", page.textIn(listOf(box)))
    }
}
