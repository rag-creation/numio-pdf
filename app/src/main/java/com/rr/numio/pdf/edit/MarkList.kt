package com.rr.numio.pdf.edit

/** The type filters in the marks list. */
enum class MarkFilter(val label: String, private val kinds: Set<MarkKind>) {
    ALL("All", MarkKind.entries.toSet()),
    HIGHLIGHT("Highlights", setOf(MarkKind.HIGHLIGHT)),
    LINE("Underlines", setOf(MarkKind.UNDERLINE, MarkKind.STRIKEOUT)),
    PEN("Pen", setOf(MarkKind.INK)),
    TEXT("Text", setOf(MarkKind.TEXT)),
    NOTE("Notes", setOf(MarkKind.NOTE)),
    SIGN("Signatures", setOf(MarkKind.SIGN)),
    CHANGE("Text changes", setOf(MarkKind.REPLACE)),
    ;

    fun matches(mark: Mark) = mark.kind in kinds
}

/** One row in the marks list: the mark, and the page's own words under it (highlights, changes). */
data class MarkEntry(val mark: Mark, val words: String = "")

/** Ordering, naming and exporting marks. Pure Kotlin, unit-tested. */
object MarkList {

    /** Page by page, then top to bottom, then left to right, as you'd read them. */
    fun sorted(entries: List<MarkEntry>): List<MarkEntry> = entries.sortedWith(
        compareBy<MarkEntry> { it.mark.page }
            .thenBy { it.mark.boxes.firstOrNull()?.top ?: 0f }
            .thenBy { it.mark.boxes.firstOrNull()?.left ?: 0f },
    )

    fun kindName(kind: MarkKind) = when (kind) {
        MarkKind.HIGHLIGHT -> "Highlight"
        MarkKind.UNDERLINE -> "Underline"
        MarkKind.STRIKEOUT -> "Strike-out"
        MarkKind.INK -> "Pen"
        MarkKind.TEXT -> "Text box"
        MarkKind.NOTE -> "Note"
        MarkKind.REPLACE -> "Text change"
        MarkKind.SIGN -> "Signature"
    }

    /** Colour names matter for highlights, lines, pen and notes; not for text, signatures or changes. */
    private fun showsColour(kind: MarkKind) =
        kind != MarkKind.REPLACE && kind != MarkKind.SIGN && kind != MarkKind.TEXT

    /** The nearest of the app's colours, so marks made in other apps get a name too. */
    fun colourName(color: Int): String {
        val nearest = MarkColors.all.minBy { c ->
            val dr = (c shr 16 and 0xFF) - (color shr 16 and 0xFF)
            val dg = (c shr 8 and 0xFF) - (color shr 8 and 0xFF)
            val db = (c and 0xFF) - (color and 0xFF)
            dr * dr + dg * dg + db * db
        }
        return MarkColors.name(nearest)
    }

    /** "Highlight · Yellow" */
    fun title(mark: Mark): String =
        if (showsColour(mark.kind)) "${kindName(mark.kind)} · ${colourName(mark.color)}" else kindName(mark.kind)

    /** What the row says under the title. */
    fun detail(entry: MarkEntry): String {
        val m = entry.mark
        val words = entry.words.ifBlank { m.text }.trim()
        return when (m.kind) {
            MarkKind.REPLACE -> when {
                m.text.isEmpty() && entry.words.isNotBlank() -> "Removed “${entry.words.trim()}”"
                m.text.isEmpty() -> "Text removed"
                entry.words.isNotBlank() -> "“${entry.words.trim()}” → “${m.text}”"
                else -> "Changed to “${m.text}”"
            }
            MarkKind.INK -> "Drawing"
            MarkKind.SIGN -> if (m.text.isNotBlank()) m.text else "Drawn signature"
            else -> words
        }
    }

    /** The counts per filter, for the chips. ALL counts everything. */
    fun counts(entries: List<MarkEntry>): Map<MarkFilter, Int> =
        MarkFilter.entries.associateWith { f -> entries.count { f.matches(it.mark) } }

    /** Every mark as plain text, page by page, to share or paste into notes. */
    fun export(name: String, entries: List<MarkEntry>): String = buildString {
        val list = sorted(entries)
        append("Marks in ").append(name).append('\n')
        append(if (list.size == 1) "1 mark" else "${list.size} marks").append('\n')
        var page = -1
        for (e in list) {
            if (e.mark.page != page) {
                page = e.mark.page
                append("\nPage ").append(page + 1).append('\n')
            }
            append("• ").append(title(e.mark))
            val d = detail(e)
            if (d.isNotEmpty()) {
                val quoted = when (e.mark.kind) {
                    MarkKind.HIGHLIGHT, MarkKind.UNDERLINE, MarkKind.STRIKEOUT -> "“$d”"
                    else -> d
                }
                append(": ").append(quoted.replace('\n', ' '))
            }
            append('\n')
        }
        append("\nFrom PDF Reader by Numio\n")
    }
}
