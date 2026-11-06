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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
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
    onSaveCopy: (name: String) -> Unit,
    onLeave: () -> Unit,
) {
    val files by vm.store.files.collectAsStateWithLifecycle()
    var askLeave by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun back() {
        if (vm.openedFromOutside) onLeave() else vm.closeReader()
    }

    /** Into the file itself when Android allows it, otherwise as a new PDF the person places. */
    fun save(page: Int, name: String) {
        vm.rememberSavePage(page)
        if (vm.canWriteOriginal()) vm.save(null) else onSaveCopy(name)
    }

    val notice = vm.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            vm.notice = null
        }
    }

    Box(Modifier.fillMaxSize().background(Numio.Bg)) {
        when (val r = vm.reader) {
            is ReaderState.Open -> {
                fun leave() {
                    if (vm.pending.isNotEmpty()) askLeave = true else back()
                }
                BackHandler { leave() }
                val starred = files.firstOrNull { it.uri == r.uri }?.starred == true
                ReaderScreen(
                    state = r,
                    starred = starred,
                    pending = vm.pending,
                    onBack = ::leave,
                    onStar = { vm.toggleStar(r.uri) },
                    onShare = { onShare(r.uri, r.name) },
                    onPageChanged = { vm.pageChanged(r.uri, it) },
                    onMark = vm::addMark,
                    onUndo = vm::undoMark,
                    onSave = { page -> save(page, r.name) },
                )
                if (askLeave) {
                    val n = vm.pending.size
                    AlertDialog(
                        onDismissRequest = { askLeave = false },
                        containerColor = Numio.Surface,
                        title = { Text("Save your marks?", style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                if (n == 1) "1 highlight or underline isn't saved in the PDF yet."
                                else "$n highlights or underlines aren't saved in the PDF yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Numio.TextDim,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                askLeave = false
                                save(files.firstOrNull { it.uri == r.uri }?.lastPage ?: 0, r.name)
                            }) { Text("Save", color = Numio.Amber) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                askLeave = false
                                vm.discardMarks()
                                back()
                            }) { Text("Don't save", color = Numio.TextDim) }
                        },
                    )
                }
            }
            is ReaderState.Loading -> {
                // While saving, back waits so the file isn't left half written
                BackHandler { if (!r.saving) back() }
                Loading(if (r.saving) "Saving\u2026" else r.name)
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
