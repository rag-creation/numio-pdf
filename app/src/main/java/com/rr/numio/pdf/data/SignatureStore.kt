package com.rr.numio.pdf.data

import android.content.Context
import com.rr.numio.pdf.edit.Signature
import com.rr.numio.pdf.edit.SignatureCodec
import java.io.File

/** Saved signatures, in the app's private storage on this phone only. */
class SignatureStore(context: Context) {
    private val file = File(context.filesDir, "signatures.txt")

    fun load(): List<Signature> = runCatching { SignatureCodec.decode(file.readText()) }.getOrDefault(emptyList())

    /** Newest first; at most [MAX] are kept. */
    fun add(signature: Signature) = save(listOf(signature) + load().take(MAX - 1))

    fun remove(index: Int) = save(load().filterIndexed { i, _ -> i != index })

    fun clear() {
        file.delete()
    }

    private fun save(list: List<Signature>) {
        runCatching { file.writeText(SignatureCodec.encode(list)) }
    }

    private companion object {
        const val MAX = 6
    }
}
