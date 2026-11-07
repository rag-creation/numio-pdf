package com.rr.numio.pdf.text

import kotlin.math.max
import kotlin.math.min

/**
 * A box on a page, as fractions of the page size (0..1), origin at the top left.
 * Fractions keep it independent of zoom and screen size.
 */
data class NBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun union(o: NBox) = NBox(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun width() = right - left
    fun height() = bottom - top
}

/** One character (or a word / line break marker when [separator] is true). */
data class Glyph(
    val text: String,
    val box: NBox,
    val separator: Boolean = false,
    /** Text size as a fraction of the page width. */
    val size: Float = 0f,
    /** Where the letter sits (its baseline), as a fraction of the page height. */
    val baseline: Float = 0f,
    /** The PDF font's name, e.g. "ABCDEF+Arial-BoldMT". */
    val font: String = "",
)

/**
 * The text of one page in reading order, with where each character sits.
 * Pure Kotlin so selection rules are unit-tested without a phone.
 */
class PageText(val glyphs: List<Glyph>) {

    /** Lines of real characters (separators left out), top to bottom as extracted. */
    private val lines: List<Line> = buildList {
        var current = ArrayList<Int>()
        glyphs.forEachIndexed { i, g ->
            if (g.separator && g.text == "\n") {
                if (current.isNotEmpty()) add(Line(current))
                current = ArrayList()
            } else if (!g.separator) {
                current.add(i)
            }
        }
        if (current.isNotEmpty()) add(Line(current))
    }

    private inner class Line(val indices: List<Int>) {
        val box: NBox = indices.map { glyphs[it].box }.reduce { a, b -> a.union(b) }
    }

    val isEmpty: Boolean get() = lines.isEmpty()

    val lastIndex: Int get() = glyphs.indexOfLast { !it.separator }

    val firstIndex: Int get() = glyphs.indexOfFirst { !it.separator }

    /**
     * The character a finger is on, for a long-press. Only counts if the finger
     * is on the text or very close to it, so pressing empty margin selects nothing.
     */
    fun hit(x: Float, y: Float, slack: Float = 0.02f): Int? {
        val i = nearest(x, y) ?: return null
        val b = glyphs[i].box
        val dx = when {
            x < b.left -> b.left - x
            x > b.right -> x - b.right
            else -> 0f
        }
        val dy = when {
            y < b.top -> b.top - y
            y > b.bottom -> y - b.bottom
            else -> 0f
        }
        return if (dx <= slack && dy <= slack) i else null
    }

    /** The closest character, for dragging a selection handle anywhere on the page. */
    fun nearest(x: Float, y: Float): Int? {
        if (lines.isEmpty()) return null
        val line = lines.minByOrNull { l ->
            when {
                y < l.box.top -> l.box.top - y
                y > l.box.bottom -> y - l.box.bottom
                else -> 0f
            }
        } ?: return null
        return line.indices.minByOrNull { i ->
            val b = glyphs[i].box
            when {
                x < b.left -> b.left - x
                x > b.right -> x - b.right
                else -> 0f
            }
        }
    }

    /** The whole word around character [i]. */
    fun wordAt(i: Int): IntRange {
        var start = i
        var end = i
        while (start - 1 >= 0 && !glyphs[start - 1].separator && !isSpace(start - 1)) start--
        while (end + 1 < glyphs.size && !glyphs[end + 1].separator && !isSpace(end + 1)) end++
        return start..end
    }

    private fun isSpace(i: Int) = glyphs[i].text.isBlank()

    /** The selected text as it should be pasted: spaces between words, line breaks between lines. */
    fun text(range: IntRange): String {
        val sb = StringBuilder()
        for (i in range.first.coerceAtLeast(0)..range.last.coerceAtMost(glyphs.lastIndex)) {
            sb.append(glyphs[i].text)
        }
        return sb.toString()
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    /**
     * Searchable text: lower-case, every run of spaces and line breaks made one
     * space, with which glyph each character came from.
     */
    private val searchable: Pair<String, IntArray> by lazy {
        val sb = StringBuilder()
        val owner = ArrayList<Int>()
        var lastSpace = true
        glyphs.forEachIndexed { i, g ->
            val t = if (g.separator) " " else g.text
            for (ch in t) {
                if (ch.isWhitespace()) {
                    if (lastSpace) continue
                    lastSpace = true
                    sb.append(' ')
                } else {
                    lastSpace = false
                    sb.append(ch.lowercaseChar())
                }
                owner.add(i)
            }
        }
        sb.toString() to owner.toIntArray()
    }

    /** The page's words as search compares them. */
    val searchText: String get() = searchable.first

    /**
     * Every place [query] appears, as glyph ranges in reading order. Ignores case,
     * and a space in the query matches any spacing or a line break.
     */
    fun find(query: String): List<IntRange> {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return emptyList()
        val (hay, owner) = searchable
        val found = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val at = hay.indexOf(q, from)
            if (at < 0) break
            found.add(owner[at]..owner[at + q.length - 1])
            from = at + q.length
        }
        return found
    }

    /** One box per line covered by the selection, for drawing the highlight. */
    fun lineBoxes(range: IntRange): List<NBox> = lines.mapNotNull { line ->
        val inRange = line.indices.filter { it in range && glyphs[it].text.isNotBlank() }
        if (inRange.isEmpty()) null
        else inRange.map { glyphs[it].box }.reduce { a, b -> a.union(b) }
            // Lines share one top and bottom so the highlight is an even band
            .let { it.copy(top = line.box.top, bottom = line.box.bottom) }
    }
}

/** What is selected: characters [start]..[end] on [page] (counted from 0). */
data class Selection(val page: Int, val text: PageText, val start: Int, val end: Int) {
    val range: IntRange get() = minOf(start, end)..maxOf(start, end)
    val boxes: List<NBox> get() = text.lineBoxes(range)
    val string: String get() = text.text(range)

    /** The first real letter selected, to copy its size, line and font. */
    val firstGlyph: Glyph? get() = range.asSequence().map { text.glyphs[it] }.firstOrNull { !it.separator }
}

/** A query as [PageText.find] compares it: trimmed, lower-case, single spaces. */
fun normalizeQuery(query: String): String =
    query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { w ->
        buildString { w.forEach { append(it.lowercaseChar()) } }
    }

/** One search result: where it is, and the boxes to light up. */
data class SearchHit(val page: Int, val range: IntRange, val boxes: List<NBox>)

/** A page searched: what was found, and whether it has any text (scans don't). */
data class PageSearch(val hits: List<SearchHit>, val hasText: Boolean)
