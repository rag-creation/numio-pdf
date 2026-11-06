package com.rr.numio.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * First-page pictures for the library list. Drawn once, then kept as small
 * files in the app's cache folder (Android may clear it; they are redrawn).
 */
object Thumbnails {
    private const val WIDTH = 180
    private val lock = Mutex()
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(uri: String): Bitmap? = memory.get(uri)

    suspend fun load(context: Context, uri: String): Bitmap? {
        memory.get(uri)?.let { return it }
        return lock.withLock {
            memory.get(uri) ?: withContext(Dispatchers.IO) {
                val file = File(File(context.cacheDir, "thumbs").apply { mkdirs() }, hash(uri) + ".png")
                val fromDisk = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
                fromDisk ?: draw(context, uri)?.also { bitmap ->
                    runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
            }?.also { memory.put(uri, it) }
        }
    }

    fun forget(context: Context, uri: String) {
        memory.remove(uri)
        runCatching { File(File(context.cacheDir, "thumbs"), hash(uri) + ".png").delete() }
    }

    private fun draw(context: Context, uri: String): Bitmap? = try {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                val page = renderer.openPage(0)
                try {
                    val h = (WIDTH * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceIn(1, WIDTH * 3)
                    Bitmap.createBitmap(WIDTH, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        }
    } catch (e: Exception) {
        null // no access any more, locked or damaged: the list shows a plain icon instead
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
