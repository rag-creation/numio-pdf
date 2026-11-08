package com.rr.numio.pdf.pages

import android.content.Context
import android.net.Uri
import com.rr.numio.pdf.edit.SaveFailure
import com.rr.numio.pdf.pdf.Unlock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.IdentityHashMap

/**
 * Writes a PDF with its pages arranged as planned: turned, deleted, moved,
 * and with pages of other PDFs added. Everything else on the kept pages
 * (marks, links, form fields) stays as it was.
 */
object PageWriter {

    /**
     * [source] is the open PDF, [added] the PDFs added to it (source 1, 2…),
     * [items] the pages to write in order. The result goes to [target], locked
     * with [password] if the open PDF was (saving never removes a password).
     */
    suspend fun write(context: Context, source: Uri, added: List<Uri>, items: List<PageItem>, target: Uri, password: String? = null) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) throw SaveFailure("A PDF needs at least one page.")
            val ctx = context.applicationContext
            PDFBoxResourceLoader.init(ctx)
            val temp = File(ctx.cacheDir, "pages.pdf")
            val opened = ArrayList<PDDocument>()
            try {
                val doc = load(ctx, source, "This PDF couldn't be changed. It may be locked or damaged.")
                opened += doc
                // Added PDFs are only read from, but must stay open until the result is saved
                val others = added.map { u ->
                    load(ctx, u, "One of the added PDFs couldn't be read, so nothing was saved.").also { opened += it }
                }

                val own = (0 until doc.numberOfPages).map { doc.getPage(it) }
                own.forEach(::standAlone)

                // Empty the page tree, then put pages back in their new order
                val root = doc.pages.cosObject
                root.setItem(COSName.KIDS, COSArray())
                root.setInt(COSName.COUNT, 0)
                for (item in items) {
                    val page = if (item.source == 0) {
                        own.getOrNull(item.index)?.also { doc.pages.add(it) }
                    } else {
                        others.getOrNull(item.source - 1)?.let { other ->
                            if (item.index !in 0 until other.numberOfPages) return@let null
                            val p = other.getPage(item.index)
                            standAlone(p)
                            doc.importPage(p)
                        }
                    } ?: throw SaveFailure("A page couldn't be found, so nothing was saved.")
                    page.rotation = Math.floorMod(page.rotation + 90 * item.turn, 360)
                }

                // Form fields whose boxes were all on deleted pages are taken out
                val kept = IdentityHashMap<COSDictionary, Boolean>()
                doc.pages.forEach { p ->
                    (p.cosObject.getDictionaryObject(COSName.ANNOTS) as? COSArray)?.forEach { a ->
                        (deref(a) as? COSDictionary)?.let { kept[it] = true }
                    }
                }
                doc.documentCatalog.acroForm?.cosObject?.let { form ->
                    (form.getDictionaryObject(COSName.FIELDS) as? COSArray)?.let { prune(it, kept) }
                }

                if (password != null) Unlock.lockAgain(doc, password)
                doc.save(temp)
                val out = try {
                    ctx.contentResolver.openOutputStream(target, "wt")
                } catch (e: SecurityException) {
                    throw SaveFailure("PDF Reader isn't allowed to change this file. Save a copy instead.")
                } catch (e: Exception) {
                    runCatching { ctx.contentResolver.openOutputStream(target, "w") }.getOrNull()
                } ?: throw SaveFailure("Couldn't write to that file.")
                out.use { o -> temp.inputStream().use { it.copyTo(o) } }
            } catch (e: SaveFailure) {
                throw e
            } catch (e: OutOfMemoryError) {
                throw SaveFailure("This PDF is too big to change on this phone.")
            } catch (e: Exception) {
                throw SaveFailure("Saving didn't work, so nothing was changed.")
            } finally {
                opened.forEach { runCatching { it.close() } }
                temp.delete()
            }
        }

    private fun load(ctx: Context, uri: Uri, failure: String): PDDocument {
        val input = runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull()
            ?: throw SaveFailure(failure)
        return input.use {
            try {
                PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: Exception) {
                throw SaveFailure(failure)
            }
        }
    }

    /**
     * Pages can take their size, turn and fonts from the group they sit in.
     * Writing them onto the page itself keeps them right once it moves.
     */
    private fun standAlone(page: PDPage) {
        val media = page.mediaBox
        val crop = page.cropBox
        val resources = page.resources
        val rotation = page.rotation
        page.mediaBox = media
        page.cropBox = crop
        if (resources != null) page.resources = resources
        page.rotation = rotation
    }

    private fun deref(b: COSBase?): COSBase? = if (b is COSObject) b.getObject() else b

    /** Removes form fields left with no box on any page; returns true if [fields] ended up empty. */
    private fun prune(fields: COSArray, kept: IdentityHashMap<COSDictionary, Boolean>) {
        for (i in fields.size() - 1 downTo 0) {
            val d = deref(fields.get(i)) as? COSDictionary ?: continue
            val kids = d.getDictionaryObject(COSName.KIDS) as? COSArray
            if (kids != null) {
                prune(kids, kept)
                if (kids.size() == 0) fields.remove(i)
            } else if (d.containsKey(COSName.RECT) && kept[d] != true) {
                // A field's box (widget) that was on a deleted page
                fields.remove(i)
            }
        }
    }
}
