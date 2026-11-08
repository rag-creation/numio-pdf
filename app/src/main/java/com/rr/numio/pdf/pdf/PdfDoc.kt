package com.rr.numio.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.rr.numio.pdf.text.TextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Width and height of a page in PDF points. */
data class PageSize(val width: Int, val height: Int) {
    val aspect: Float get() = if (width <= 0) 1.414f else height.toFloat() / width
}

/**
 * Why a file couldn't be opened, in words for the person, not an exception.
 * [locked]: it needs a password; [wrongPassword]: the one given didn't fit.
 */
class OpenFailure(message: String, val locked: Boolean = false, val wrongPassword: Boolean = false) : Exception(message)

/**
 * One open PDF, drawn by Android's own renderer (android.graphics.pdf), so no
 * PDF library is bundled. The renderer can only work on one page at a time,
 * so every call goes through one lock.
 */
class PdfDoc private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pages: List<PageSize>,
    /** Where the words are on each page, for selecting and copying text. */
    val text: TextExtractor,
    /** What was actually opened: the file itself, or the app's working copy of it. */
    val source: Uri,
    /**
     * The password it was unlocked with, kept only in memory while it's open,
     * so saving can lock it again with the same one. Null for unlocked PDFs.
     */
    val password: String? = null,
) {
    val pageCount: Int get() = pages.size

    private val lock = Mutex()
    private var closed = false

    // Rendered pages, newest kept, up to a quarter of the app's memory
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get(key(index, widthPx))

    /** The page drawn [widthPx] wide on white, or null once the document is closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val key = key(index, widthPx)
        cache.get(key)?.let { return it }
        return lock.withLock {
            cache.get(key) ?: withContext(Dispatchers.IO) {
                if (closed || index !in pages.indices) return@withContext null
                val page = renderer.openPage(index)
                try {
                    val w = widthPx.coerceIn(1, MAX_SIDE)
                    val h = (w * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, MAX_SIDE)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }?.also { cache.put(key, it) }
        }
    }

    suspend fun close() {
        lock.withLock {
            if (!closed) {
                closed = true
                cache.evictAll()
                withContext(Dispatchers.IO) {
                    runCatching { renderer.close() }
                    runCatching { fd.close() }
                }
                text.close()
            }
        }
    }

    private fun key(index: Int, widthPx: Int) = "$index@$widthPx"

    companion object {
        /** Big enough for a sharp zoomed page, small enough not to run out of memory. */
        const val MAX_SIDE = 4096

        /**
         * Opens [uri]. A password-locked PDF needs [password]: it's unlocked into a
         * private working copy on this phone (Android's renderer can't read locked files).
         * [copyName]: the working copy's file name, if one is needed (each open PDF needs its own).
         */
        suspend fun open(
            context: Context,
            uri: Uri,
            password: String? = null,
            copyName: String = "current.pdf",
        ): PdfDoc = withContext(Dispatchers.IO) {
            if (password != null) {
                val unlocked = Unlock.toCache(context, uri, password)
                return@withContext try {
                    openDirect(context, Uri.fromFile(unlocked), password)
                } catch (e: Unreadable) {
                    throw OpenFailure(DAMAGED)
                }
            }
            try {
                openDirect(context, uri)
            } catch (e: Unreadable) {
                // Some apps (email, chat) hand over a stream Android's renderer can't
                // jump around in. Copy it into the app's cache on this phone and try that.
                val copy = copyToCache(context, uri, copyName) ?: throw OpenFailure(DAMAGED)
                try {
                    openDirect(context, Uri.fromFile(copy))
                } catch (e2: Unreadable) {
                    throw OpenFailure(DAMAGED)
                }
            }
        }

        private const val DAMAGED = "This doesn't look like a working PDF. The file may be damaged."

        /** The renderer couldn't read the file as it was handed over. */
        private class Unreadable : Exception()

        private fun openDirect(context: Context, uri: Uri, password: String? = null): PdfDoc {
            val fd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (e: SecurityException) {
                throw OpenFailure("PDF Reader no longer has permission to open this file. Open it again from where it is saved.")
            } catch (e: java.io.FileNotFoundException) {
                throw OpenFailure("This file isn't there any more. It may have been moved or deleted.")
            } catch (e: Exception) {
                throw OpenFailure("This file couldn't be opened.")
            } ?: throw OpenFailure("This file couldn't be opened.")

            val renderer = try {
                PdfRenderer(fd)
            } catch (e: SecurityException) {
                runCatching { fd.close() }
                throw OpenFailure("This PDF is locked with a password.", locked = true)
            } catch (e: Exception) {
                runCatching { fd.close() }
                throw Unreadable()
            }

            val pages = try {
                (0 until renderer.pageCount).map { i ->
                    val page = renderer.openPage(i)
                    try {
                        PageSize(page.width, page.height)
                    } finally {
                        page.close()
                    }
                }
            } catch (e: Exception) {
                runCatching { renderer.close() }
                runCatching { fd.close() }
                throw OpenFailure(DAMAGED)
            }

            if (pages.isEmpty()) {
                runCatching { renderer.close() }
                runCatching { fd.close() }
                throw OpenFailure("This PDF has no pages.")
            }
            return PdfDoc(fd, renderer, pages, TextExtractor(context.applicationContext, uri), uri, password)
        }

        /** One working copy per name; the last one with that name is replaced. */
        private fun copyToCache(context: Context, uri: Uri, name: String): java.io.File? {
            return try {
                val dir = java.io.File(context.cacheDir, "opened").apply { mkdirs() }
                val file = java.io.File(dir, name)
                val input = context.contentResolver.openInputStream(uri) ?: return null
                input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
                file
            } catch (e: Exception) {
                null
            }
        }
    }
}
