package com.rr.numio.pdf

import com.rr.numio.pdf.text.Glyph
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.text.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextTest {

    /**
     * Builds a page from lines of text, each character 0.02 wide and each line
     * 0.03 high with a gap, with word and line separators like the extractor makes.
     */
    private fun page(vararg lines: String): PageText {
        val glyphs = ArrayList<Glyph>()
        lines.forEachIndexed { row, line ->
            val top = 0.1f + row * 0.05f
            var x = 0.1f
            line.forEach { c ->
                val box = NBox(x, top, x + 0.02f, top + 0.03f)
                glyphs += if (c == ' ') Glyph(" ", box, separator = true) else Glyph(c.toString(), box)
                x += 0.02f
            }
            if (row < lines.lastIndex) glyphs += Glyph("\n", NBox(x, top, x, top + 0.03f), separator = true)
        }
        return PageText(glyphs)
    }

    @Test
    fun longPressPicksTheWordUnderTheFinger() {
        val p = page("hello world", "second line")
        val i = p.hit(0.1f + 7 * 0.02f + 0.01f, 0.115f)!! // on the 'o' of world
        assertEquals("world", p.text(p.wordAt(i)))
    }

    @Test
    fun pressingEmptyMarginSelectsNothing() {
        val p = page("hello")
        assertNull(p.hit(0.9f, 0.9f))
        assertTrue(page().isEmpty)
        assertNull(page().nearest(0.5f, 0.5f))
    }

    @Test
    fun selectionAcrossLinesKeepsLineBreaks() {
        val p = page("hello world", "second line")
        val start = p.hit(0.1f + 6 * 0.02f + 0.01f, 0.115f)!! // 'w'
        val end = p.hit(0.1f + 5 * 0.02f + 0.01f, 0.165f)!! // 'd' of second
        assertEquals("world\nsecond", p.text(start..end))
        assertEquals(2, p.lineBoxes(start..end).size)
    }

    @Test
    fun draggingBelowTheTextSnapsToTheLastLine() {
        val p = page("one", "two")
        val i = p.nearest(0.99f, 0.99f)!!
        assertEquals("o", p.glyphs[i].text)
        assertEquals(p.lastIndex, i)
    }

    @Test
    fun selectAllCopiesWholePage() {
        val p = page("a b", "c")
        assertEquals("a b\nc", p.text(p.firstIndex..p.lastIndex))
    }

    @Test
    fun malayalamClustersStayTogether() {
        // The extractor may hand over a whole cluster as one glyph
        val glyphs = listOf(
            Glyph("ക്ഷ", NBox(0.1f, 0.1f, 0.14f, 0.13f)),
            Glyph("മ", NBox(0.14f, 0.1f, 0.16f, 0.13f)),
        )
        val p = PageText(glyphs)
        assertEquals("ക്ഷമ", p.text(p.wordAt(0)))
    }
}
