package com.rr.numio.pdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rr.numio.pdf.AppViewModel
import com.rr.numio.pdf.ReaderState
import com.rr.numio.pdf.ui.library.LibraryScreen
import com.rr.numio.pdf.ui.reader.ReaderScreen
import com.rr.numio.pdf.ui.theme.Numio

@Composable
fun PdfApp(
    vm: AppViewModel,
    onOpenPicker: () -> Unit,
    onShare: (uri: String, name: String) -> Unit,
    onLeave: () -> Unit,
) {
    val files by vm.store.files.collectAsStateWithLifecycle()

    fun back() {
        if (vm.openedFromOutside) onLeave() else vm.closeReader()
    }

    Box(Modifier.fillMaxSize().background(Numio.Bg)) {
        when (val r = vm.reader) {
            is ReaderState.Open -> {
                BackHandler { back() }
                val starred = files.firstOrNull { it.uri == r.uri }?.starred == true
                ReaderScreen(
                    state = r,
                    starred = starred,
                    onBack = ::back,
                    onStar = { vm.toggleStar(r.uri) },
                    onShare = { onShare(r.uri, r.name) },
                    onPageChanged = { vm.pageChanged(r.uri, it) },
                )
            }
            is ReaderState.Loading -> {
                BackHandler { back() }
                Loading(r.name)
            }
            else -> LibraryScreen(
                files = files,
                onOpen = { vm.open(android.net.Uri.parse(it.uri), fromOutside = false) },
                onOpenPicker = onOpenPicker,
                onStar = { vm.toggleStar(it.uri) },
                onRemove = { vm.remove(it.uri) },
            )
        }

        val failed = vm.reader as? ReaderState.Failed
        if (failed != null) {
            AlertDialog(
                onDismissRequest = vm::closeReader,
                containerColor = Numio.Surface,
                title = { Text("Couldn't open this PDF", style = MaterialTheme.typography.titleLarge) },
                text = { Text(failed.message, style = MaterialTheme.typography.bodyLarge, color = Numio.TextDim) },
                confirmButton = {
                    TextButton(onClick = vm::closeReader) { Text("OK", color = Numio.Amber) }
                },
                dismissButton = if (failed.uri != null) {
                    { TextButton(onClick = { vm.remove(failed.uri) }) { Text("Remove from list", color = Numio.TextDim) } }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun Loading(name: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Numio.Amber, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        if (name.isNotEmpty()) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Numio.TextDim, textAlign = TextAlign.Center)
        }
    }
}
