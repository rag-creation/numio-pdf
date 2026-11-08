package com.rr.numio.pdf.data

import java.net.URLDecoder
import java.net.URLEncoder

/** One PDF in the library. Only a pointer to the file is kept, never a copy. */
data class RecentFile(
    val uri: String,
    val name: String,
    val pageCount: Int = 0,
    /** Last page read, counted from 0. */
    val lastPage: Int = 0,
    val lastOpened: Long = 0L,
    val starred: Boolean = false,
    val sizeBytes: Long = -1L,
    /** Kept in Private: hidden from the library until unlocked with the phone's lock. */
    val isPrivate: Boolean = false,
)

/**
 * Saves the library as plain text: one file per line, fields split by tabs,
 * text fields URL-encoded so a tab or newline in a file name can't break a line.
 * Pure Kotlin, so it is unit-tested without a phone.
 */
object RecentCodec {
    private const val UTF8 = "UTF-8"

    fun encode(files: List<RecentFile>): String = files.joinToString("\n") { f ->
        listOf(
            URLEncoder.encode(f.uri, UTF8),
            URLEncoder.encode(f.name, UTF8),
            f.pageCount.toString(),
            f.lastPage.toString(),
            f.lastOpened.toString(),
            if (f.starred) "1" else "0",
            f.sizeBytes.toString(),
            if (f.isPrivate) "1" else "0",
        ).joinToString("\t")
    }

    fun decode(text: String): List<RecentFile> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 7) return@mapNotNull null
        try {
            RecentFile(
                uri = URLDecoder.decode(p[0], UTF8),
                name = URLDecoder.decode(p[1], UTF8),
                pageCount = p[2].toInt(),
                lastPage = p[3].toInt(),
                lastOpened = p[4].toLong(),
                starred = p[5] == "1",
                sizeBytes = p[6].toLong(),
                // Lists saved before Private existed have 7 fields
                isPrivate = p.getOrNull(7) == "1",
            )
        } catch (e: IllegalArgumentException) {
            null // a damaged line is skipped, the rest of the library survives
        }
    }.toList()
}
