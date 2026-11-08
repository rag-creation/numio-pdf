package com.rr.numio.pdf.pages

/**
 * One page in the organised PDF: which PDF it comes from ([source] 0 is the
 * open one, 1 and up are PDFs added to it), which page there, and how many
 * extra quarter turns clockwise. [key] stays with the page as it moves.
 */
data class PageItem(val key: Long, val source: Int, val index: Int, val turn: Int = 0)

/** Rotating, deleting, moving and adding pages, as plain lists. Unit-tested. */
object PagePlan {

    /** The open PDF as it is: every page, in order, unturned. */
    fun start(count: Int): List<PageItem> = List(count) { PageItem(it.toLong(), 0, it) }

    /** Turns pages a quarter at a time; [quarters] is +1 clockwise, -1 the other way. */
    fun rotate(items: List<PageItem>, keys: Set<Long>, quarters: Int): List<PageItem> =
        items.map { if (it.key in keys) it.copy(turn = Math.floorMod(it.turn + quarters, 4)) else it }

    fun delete(items: List<PageItem>, keys: Set<Long>): List<PageItem> = items.filterNot { it.key in keys }

    /** The page at [from] taken out and put back at [to]. */
    fun move(items: List<PageItem>, from: Int, to: Int): List<PageItem> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Every page of another PDF added at the end. */
    fun add(items: List<PageItem>, source: Int, count: Int, firstKey: Long): List<PageItem> =
        items + List(count) { PageItem(firstKey + it, source, it) }

    /** True once anything differs from the PDF as opened. */
    fun changed(items: List<PageItem>, count: Int): Boolean =
        items.size != count || items.withIndex().any { (i, p) -> p.source != 0 || p.index != i || p.turn != 0 }

    /** Where each page of the open PDF ends up (old page → new page); deleted pages are left out. */
    fun moved(items: List<PageItem>): Map<Int, Int> =
        items.withIndex().filter { it.value.source == 0 }.associate { it.value.index to it.index }

    /** The selected pages, in the order they're arranged in. */
    fun selected(items: List<PageItem>, keys: Set<Long>): List<PageItem> = items.filter { it.key in keys }

    /** "3 pages turned, 2 deleted" style summary for the save button's question. */
    fun summary(items: List<PageItem>, count: Int): String {
        val kept = items.count { it.source == 0 }
        val parts = ArrayList<String>()
        val deleted = count - kept
        val added = items.size - kept
        val turned = items.count { it.turn != 0 }
        val own = items.filter { it.source == 0 }.map { it.index }
        if (own != own.sorted() || items.withIndex().any { (i, p) -> p.source != 0 && items.drop(i + 1).any { it.source == 0 } }) parts += "pages moved"
        if (turned > 0) parts += if (turned == 1) "1 page turned" else "$turned pages turned"
        if (deleted > 0) parts += if (deleted == 1) "1 page deleted" else "$deleted pages deleted"
        if (added > 0) parts += if (added == 1) "1 page added" else "$added pages added"
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}
