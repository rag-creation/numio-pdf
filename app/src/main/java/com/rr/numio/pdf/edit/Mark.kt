package com.rr.numio.pdf.edit

import com.rr.numio.pdf.text.NBox

/** The kinds of text marks PDF Reader draws and saves. */
enum class MarkKind { HIGHLIGHT, UNDERLINE, STRIKEOUT, INK }

/**
 * A highlight / underline over text on one page. [boxes] are one per line,
 * as fractions of the page (see [NBox]); [color] is 0xRRGGBB.
 *
 * [id] tells marks apart while editing. [ref] is where a mark already saved in
 * the file sits in its page's annotation list (-1 for a new mark).
 */
data class Mark(
    val page: Int,
    val kind: MarkKind,
    val boxes: List<NBox>,
    val color: Int,
    val text: String = "",
    val id: Long = 0L,
    val ref: Int = -1,
    /** Pen drawing ([MarkKind.INK]): each stroke as u0, v0, u1, v1, … fractions of the page. */
    val ink: List<FloatArray> = emptyList(),
    /** Pen width as a fraction of the page width. */
    val width: Float = 0f,
) {
    val saved: Boolean get() = ref >= 0

    /** True if a finger at ([x], [y]) on the page is on this mark (with a little slack). */
    fun contains(x: Float, y: Float, slack: Float = 0.01f): Boolean = boxes.any {
        x >= it.left - slack && x <= it.right + slack && y >= it.top - slack && y <= it.bottom + slack
    }

    companion object {
        /** A stable id for a mark already in the file. Never clashes with new marks (those are negative). */
        fun savedId(page: Int, ref: Int): Long = page.toLong() * 1_000_000L + ref + 1
    }
}

/** Pen colours offered for marks; yellow first because it's what most people reach for. */
object MarkColors {
    const val YELLOW = 0xFFD43B
    const val GREEN = 0x8CE99A
    const val BLUE = 0x74C0FC
    const val PINK = 0xF783AC
    const val RED = 0xE03131
    const val BLACK = 0x1A1A1A

    val all = listOf(YELLOW, GREEN, BLUE, PINK, RED, BLACK)

    /**
     * Colours a highlighter can use. Black would hide the words, which looks
     * like blacking out private text but isn't (the words stay in the file).
     */
    fun canHighlight(color: Int) = color != BLACK

    fun name(color: Int) = when (color) {
        YELLOW -> "Yellow"
        GREEN -> "Green"
        BLUE -> "Blue"
        PINK -> "Pink"
        RED -> "Red"
        BLACK -> "Black"
        else -> "Colour"
    }
}
