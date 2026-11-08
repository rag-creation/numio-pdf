package com.rr.numio.pdf.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/**
 * Password-locked PDFs. The password is never written anywhere: it lives in
 * memory while the PDF is open, and the unlocked working copy sits in the app's
 * private cache only until the PDF is closed.
 */
object Unlock {

    private const val NAME = "unlocked.pdf"

    private fun file(context: Context) = File(File(context.cacheDir, "opened").apply { mkdirs() }, NAME)

    /** Unlocks [uri] with [password] into the private working copy. */
    fun toCache(context: Context, uri: Uri, password: String): File {
        val ctx = context.applicationContext
        PDFBoxResourceLoader.init(ctx)
        val input = runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull()
            ?: throw OpenFailure("This file couldn't be opened.")
        val out = file(ctx)
        input.use { stream ->
            val doc = try {
                PDDocument.load(stream, password, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw OpenFailure("That password isn't right. Try again.", locked = true, wrongPassword = true)
            } catch (e: OutOfMemoryError) {
                throw OpenFailure("This PDF is too big to unlock on this phone.")
            } catch (e: Exception) {
                throw OpenFailure("This PDF couldn't be unlocked. It may be damaged.")
            }
            doc.use {
                it.isAllSecurityToBeRemoved = true
                try {
                    it.save(out)
                } catch (e: Exception) {
                    out.delete()
                    throw OpenFailure("This PDF couldn't be unlocked. It may be damaged.")
                }
            }
        }
        return out
    }

    /** Locks a PDF that's about to be saved with [password] again (AES-256), so saving never removes it. */
    fun lockAgain(doc: PDDocument, password: String) {
        val policy = StandardProtectionPolicy(password, password, AccessPermission())
        policy.encryptionKeyLength = 256
        doc.protect(policy)
    }

    /** Removes the unlocked working copy (when the PDF is closed, and at start-up). */
    fun forget(context: Context) {
        runCatching { file(context.applicationContext).delete() }
    }
}
