package com.rr.numio.pdf.edit

import com.rr.numio.pdf.text.NBox

/** The kinds of text marks PDF Reader draws and saves. */
enum class MarkKind { HIGHLIGHT, UNDERLINE, STRIKEOUT }

/**
 * A highlight / underline over text on one page. [boxes] are one per line,
 * as fractions of the page (see [NBox]); [color] is 0xRRGGBB.
 */
data class Mark(
    val page: Int,
    val kind: MarkKind,
    val boxes: List<NBox>,
    val color: Int,
    val text: String = "",
)

/** Pen colours offered for marks; yellow first because it's what most people reach for. */
object MarkColors {
    const val YELLOW = 0xFFD43B
    const val GREEN = 0x8CE99A
    const val BLUE = 0x74C0FC
    const val PINK = 0xF783AC
    const val RED = 0xE03131

    val all = listOf(YELLOW, GREEN, BLUE, PINK, RED)

    fun name(color: Int) = when (color) {
        YELLOW -> "Yellow"
        GREEN -> "Green"
        BLUE -> "Blue"
        PINK -> "Pink"
        RED -> "Red"
        else -> "Colour"
    }
}
