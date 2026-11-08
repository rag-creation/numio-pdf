package com.rr.numio.pdf.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Library rules, kept free of Android so they can be unit-tested. */
object Library {
    /** Unstarred files beyond this many are dropped, oldest first. Starred and private ones always stay. */
    const val MAX_RECENT = 100

    /** Newest first, starred or not. */
    fun ordered(files: List<RecentFile>): List<RecentFile> = files.sortedByDescending { it.lastOpened }

    fun starred(files: List<RecentFile>): List<RecentFile> = ordered(files).filter { it.starred }

    /** Everything except Private: what Recent, Starred, Continue reading and search see. */
    fun shown(files: List<RecentFile>): List<RecentFile> = files.filterNot { it.isPrivate }

    fun privateFiles(files: List<RecentFile>): List<RecentFile> = ordered(files).filter { it.isPrivate }

    fun setPrivate(files: List<RecentFile>, uri: String, value: Boolean): List<RecentFile> =
        files.map { if (it.uri == uri) it.copy(isPrivate = value) else it }

    /** Adds or refreshes a file and moves it to the top, keeping its star and reading place. */
    fun opened(
        files: List<RecentFile>,
        uri: String,
        name: String,
        sizeBytes: Long,
        pageCount: Int,
        now: Long,
    ): List<RecentFile> {
        val old = files.firstOrNull { it.uri == uri }
        val fresh = RecentFile(
            uri = uri,
            name = name,
            pageCount = pageCount,
            lastPage = (old?.lastPage ?: 0).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            lastOpened = now,
            starred = old?.starred ?: false,
            sizeBytes = sizeBytes,
            isPrivate = old?.isPrivate ?: false,
        )
        return trim(listOf(fresh) + files.filter { it.uri != uri })
    }

    fun withPage(files: List<RecentFile>, uri: String, page: Int): List<RecentFile> =
        files.map { if (it.uri == uri) it.copy(lastPage = page.coerceAtLeast(0)) else it }

    fun toggleStar(files: List<RecentFile>, uri: String): List<RecentFile> =
        files.map { if (it.uri == uri) it.copy(starred = !it.starred) else it }

    fun remove(files: List<RecentFile>, uri: String): List<RecentFile> = files.filter { it.uri != uri }

    private fun trim(files: List<RecentFile>): List<RecentFile> {
        var unstarred = 0
        return ordered(files).filter { it.starred || it.isPrivate || ++unstarred <= MAX_RECENT }
    }

    /** Every word typed must appear in the name, in any order, ignoring case. */
    fun matches(name: String, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val n = name.lowercase()
        return words.all { n.contains(it) }
    }

    fun search(files: List<RecentFile>, query: String): List<RecentFile> =
        ordered(files).filter { matches(it.name, query) }

    /** "Today", "Yesterday", "Mon", "3 Nov", or "3 Nov 2025" for older years. */
    fun whenLabel(
        now: Long,
        then: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(then).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        }
    }

    fun pagesLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"

    /** Fraction read, for the progress line on the "Continue reading" card. */
    fun progress(file: RecentFile): Float =
        if (file.pageCount <= 0) 0f else ((file.lastPage + 1).toFloat() / file.pageCount).coerceIn(0f, 1f)
}
