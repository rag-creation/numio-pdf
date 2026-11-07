package com.rr.numio.pdf.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.data.PageLayout
import com.rr.numio.pdf.data.PageLook
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    look: PageLook,
    layout: PageLayout,
    savedSignatures: Int,
    recentFiles: Int,
    onLook: (PageLook) -> Unit,
    onLayout: (PageLayout) -> Unit,
    onClearRecent: () -> Unit,
    onClearSignatures: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    var confirm by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    var licences by remember { mutableStateOf(false) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Numio.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(PIcons.Back, contentDescription = "Back", tint = Numio.Text) }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Section("Reading")
            Label("Page colour")
            Choices(
                listOf(PageLook.NORMAL to "Normal", PageLook.DARK to "Night", PageLook.SEPIA to "Sepia"),
                look, onLook,
            )
            Label("Layout")
            Choices(
                listOf(PageLayout.SCROLL to "Scroll", PageLayout.ONE_PAGE to "One page", PageLayout.BOOK to "Book"),
                layout, onLayout,
            )
            Hint(
                "Book: each page full screen, swipe right to left, with a reading lock. " +
                    "Also in the ⋮ menu while reading. Night and sepia only change the screen, never your files.",
            )

            Section("Privacy")
            Hint(
                "PDF Reader has no internet permission: nothing you open, mark or sign ever leaves this phone. " +
                    "It only sees the files you open with it.",
            )
            Item(
                title = "Clear the recent list",
                detail = if (recentFiles == 1) "1 PDF in the list. The files themselves aren't touched." else "$recentFiles PDFs in the list. The files themselves aren't touched.",
                enabled = recentFiles > 0,
            ) { confirm = "recent" }
            Item(
                title = "Delete saved signatures",
                detail = if (savedSignatures == 1) "1 saved signature" else "$savedSignatures saved signatures",
                enabled = savedSignatures > 0,
                danger = true,
            ) { confirm = "signatures" }

            Section("About")
            Item(title = "Version", detail = version) { taps++ }
            Item(title = "Numio", detail = "getnumio.org: more private apps") { open("https://getnumio.org") }
            Item(title = "Source code", detail = "GPL-3.0, on GitHub") { open("https://github.com/rag-creation/numio-pdf") }
            Item(title = "Open-source licences", detail = "Inter font, PdfBox-Android") { licences = true }

            // A small thank-you for the curious: tap the version seven times
            AnimatedVisibility(visible = taps >= 7, enter = fadeIn()) { EasterEgg() }

            Spacer(Modifier.height(24.dp))
            Text(
                "Made by a Keralite 💛",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Numio.Surface,
            title = {
                Text(
                    if (what == "recent") "Clear the recent list?" else "Delete saved signatures?",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    if (what == "recent") "Your PDFs stay where they are; only this app's list, stars and bookmarks of them are cleared."
                    else "They're removed from this phone. Signatures already placed in PDFs stay there.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Numio.TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "recent") onClearRecent() else onClearSignatures()
                    confirm = null
                }) { Text(if (what == "recent") "Clear" else "Delete", color = Numio.Error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Numio.TextDim) } },
        )
    }

    if (licences) {
        AlertDialog(
            onDismissRequest = { licences = false },
            containerColor = Numio.Surface,
            title = { Text("Open-source licences", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PDF Reader is free software under the GNU GPL-3.0.", style = MaterialTheme.typography.bodyMedium)
                    Text("PdfBox-Android: Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Inter typeface: SIL Open Font License 1.1", style = MaterialTheme.typography.bodyMedium)
                    Text("AndroidX and Jetpack Compose: Apache License 2.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Built by RR with help from Claude.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licences = false }) { Text("Close", color = Numio.Amber) } },
        )
    }
}

@Composable
private fun EasterEgg() {
    val first = "Thanks for choosing Numio 💛"
    val second = "With Lo❤️e, RR"
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // Letter by letter, like the other Numio apps
        while (shown < first.length + second.length) {
            delay(55)
            shown++
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Numio.Surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(first.take(shown), style = MaterialTheme.typography.titleMedium, color = Numio.Amber)
        Spacer(Modifier.height(6.dp))
        Text(second.take((shown - first.length).coerceAtLeast(0)), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Numio.TextDim,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Numio.Surface).padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Numio.Amber else Numio.Surface)
                    .clickable(role = Role.RadioButton) { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Numio.OnAmber else Numio.Text)
            }
        }
    }
}

@Composable
private fun Item(title: String, detail: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> Numio.Line
                    danger -> Numio.Error
                    else -> Numio.Text
                },
            )
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        if (enabled) Icon(PIcons.Chevron, contentDescription = null, tint = Numio.TextDim, modifier = Modifier.size(18.dp))
    }
}
