package com.rr.numio.pdf.data

import java.net.URLDecoder
import java.net.URLEncoder

/** How pages are coloured: as printed, light-on-dark for night reading, or warm paper. */
enum class PageLook { NORMAL, DARK, SEPIA }

/** Pages one under another, or one page at a time, each fitted to the screen. */
enum class PageLayout { SCROLL, ONE_PAGE }

/** A page the reader marked to come back to, with a name they can change. */
data class Bookmark(val page: Int, val name: String)

/** An entry of a PDF's own table of contents. */
data class OutlineEntry(val title: String, val page: Int, val depth: Int)

/** Bookmark rules and storage format. Pure Kotlin, unit-tested. */
object Bookmarks {
    private const val UTF8 = "UTF-8"

    fun defaultName(page: Int) = "Page ${page + 1}"

    /** Adds a bookmark for [page], or removes it if there is one. Kept in page order. */
    fun toggle(list: List<Bookmark>, page: Int): List<Bookmark> =
        if (list.any { it.page == page }) list.filter { it.page != page }
        else (list + Bookmark(page, defaultName(page))).sortedBy { it.page }

    fun rename(list: List<Bookmark>, page: Int, name: String): List<Bookmark> =
        list.map { if (it.page == page) it.copy(name = name.trim().ifEmpty { defaultName(page) }) else it }

    fun encode(list: List<Bookmark>): String =
        list.joinToString("\n") { "${it.page}\t${URLEncoder.encode(it.name, UTF8)}" }

    fun decode(text: String): List<Bookmark> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 2) return@mapNotNull null
        try {
            Bookmark(p[0].toInt(), URLDecoder.decode(p[1], UTF8))
        } catch (e: IllegalArgumentException) {
            null
        }
    }.distinctBy { it.page }.sortedBy { it.page }.toList()
}
