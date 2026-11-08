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
import com.rr.numio.pdf.ui.pages.OrganiseScreen
import com.rr.numio.pdf.ui.reader.ReaderScreen
import com.rr.numio.pdf.ui.settings.SettingsScreen
import com.rr.numio.pdf.ui.theme.Numio
import com.rr.numio.pdf.security.DeviceLock

@Composable
fun PdfApp(
    vm: AppViewModel,
    onOpenPicker: () -> Unit,
    onShare: (uri: String, name: String) -> Unit,
    onSaveCopy: (name: String) -> Unit,
    onAddPdf: () -> Unit,
    onSavePagesCopy: (name: String) -> Unit,
    onExtract: (name: String) -> Unit,
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

    /** Asks for the phone's lock; [then] runs once it's given. */
    fun guard(title: String, subtitle: String, then: () -> Unit) {
        vm.outing = true // Android's PIN screen may take the app off screen for a moment
        DeviceLock.ask(context, title, subtitle, onOk = then)
    }

    /** Private needs a screen lock on the phone to mean anything. */
    fun needsScreenLock(): Boolean {
        if (DeviceLock.available(context)) return false
        vm.notice = "Set a screen lock on your phone (PIN, pattern or fingerprint) to use this."
        return true
    }

    val notice = vm.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            vm.notice = null
        }
    }

    // Locked: nothing of the library or the open PDF is drawn at all
    if (vm.locked) {
        LockScreen(
            onUnlock = { guard("PDF Reader", "Unlock to open PDF Reader") { vm.unlocked() } },
            onLeave = onLeave,
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Numio.Bg)) {
        when (val r = vm.reader) {
            is ReaderState.Open -> if (vm.organiser != null) {
                val org = vm.organiser!!
                OrganiseScreen(
                    org = org,
                    name = r.name,
                    busy = vm.organiseBusy,
                    onAddPdf = onAddPdf,
                    onExtract = { onExtract(r.name) },
                    onSave = { if (vm.canWriteOriginal()) vm.saveOrganised(null) else onSavePagesCopy(r.name) },
                    onClose = vm::stopOrganising,
                    onNotice = { vm.notice = it },
                )
            } else {
                fun leave() {
                    if (vm.edits.isNotEmpty()) askLeave = true else back()
                }
                BackHandler { leave() }
                val starred = files.firstOrNull { it.uri == r.uri }?.starred == true
                ReaderScreen(
                    state = r,
                    starred = starred,
                    edits = vm.edits,
                    onBack = ::leave,
                    onStar = { vm.toggleStar(r.uri) },
                    onShare = { onShare(r.uri, r.name) },
                    onPageChanged = { vm.pageChanged(r.uri, it) },
                    onMark = vm::addMark,
                    onRemoveMark = vm::removeMark,
                    onRecolorMark = vm::recolorMark,
                    onChangeMark = vm::changeMark,
                    onFill = vm::fill,
                    signatures = vm.signatures,
                    signaturesUnlocked = vm.signaturesUnlocked,
                    onSignaturesUnlocked = { vm.signaturesUnlocked = true },
                    look = vm.look,
                    layout = vm.layout,
                    onLook = vm::chooseLook,
                    onLayout = vm::chooseLayout,
                    bookmarks = vm.bookmarks,
                    onToggleBookmark = vm::toggleBookmark,
                    onRenameBookmark = vm::renameBookmark,
                    onUndo = vm::undoMark,
                    onSave = { page -> save(page, r.name) },
                    onOrganise = vm::startOrganising,
                )
                if (askLeave) {
                    val n = vm.edits.size
                    AlertDialog(
                        onDismissRequest = { askLeave = false },
                        containerColor = Numio.Surface,
                        title = { Text("Save your changes?", style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                if (n == 1) "1 change isn't saved in the PDF yet."
                                else "$n changes aren't saved in the PDF yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Numio.TextDim,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                askLeave = false
                                save(files.firstOrNull { it.uri == r.uri }?.lastPage ?: 0, r.name)
                            }) { Text("Save", color = Numio.AmberText) }
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
            else -> if (vm.settingsOpen) {
                BackHandler { vm.settingsOpen = false }
                var signatureCount by remember { mutableStateOf(vm.signatures.load().size) }
                SettingsScreen(
                    look = vm.look,
                    layout = vm.layout,
                    appLock = vm.appLock,
                    lockAfter = vm.lockAfter,
                    onAppLock = { on ->
                        if (!needsScreenLock()) {
                            guard(
                                if (on) "Turn on the app lock" else "Turn off the app lock",
                                "Confirm it's you",
                            ) { vm.setAppLock(on) }
                        }
                    },
                    onLockAfter = vm::chooseLockAfter,
                    theme = vm.appTheme,
                    onTheme = vm::chooseTheme,
                    savedSignatures = signatureCount,
                    recentFiles = files.size,
                    onLook = vm::chooseLook,
                    onLayout = vm::chooseLayout,
                    onClearRecent = vm::clearRecent,
                    onClearSignatures = {
                        vm.signatures.clear()
                        signatureCount = 0
                    },
                    onBack = { vm.settingsOpen = false },
                )
            } else {
                LibraryScreen(
                    files = files,
                    privateOpen = vm.privateOpen,
                    onOpenPrivate = {
                        if (!needsScreenLock()) guard("Private PDFs", "Unlock to see your private PDFs") { vm.privateOpen = true }
                    },
                    onSetPrivate = { f, value ->
                        if (!value || !needsScreenLock()) vm.setPrivate(f.uri, value)
                    },
                    onOpen = { vm.open(android.net.Uri.parse(it.uri), fromOutside = false) },
                    onOpenPicker = onOpenPicker,
                    onStar = { vm.toggleStar(it.uri) },
                    onRemove = { vm.remove(it.uri) },
                    onSettings = { vm.settingsOpen = true },
                )
            }
        }

        val locked = vm.reader as? ReaderState.Locked
        if (locked != null) {
            PasswordDialog(
                name = locked.name,
                wrong = locked.wrong,
                onOpen = vm::unlock,
                onCancel = ::back,
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
                    TextButton(onClick = vm::closeReader) { Text("OK", color = Numio.AmberText) }
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
        CircularProgressIndicator(color = Numio.AmberText, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        if (name.isNotEmpty()) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Numio.TextDim, textAlign = TextAlign.Center)
        }
    }
}
