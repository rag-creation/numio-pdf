package com.rr.numio.pdf.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.R
import com.rr.numio.pdf.data.Library
import com.rr.numio.pdf.data.RecentFile
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

private enum class Filter(val label: String) { Recent("Recent"), Starred("Starred") }

@Composable
fun LibraryScreen(
    files: List<RecentFile>,
    onOpen: (RecentFile) -> Unit,
    onOpenPicker: () -> Unit,
    onStar: (RecentFile) -> Unit,
    onRemove: (RecentFile) -> Unit,
    onSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.Recent) }
    val now = remember(files) { System.currentTimeMillis() }

    val searching = query.isNotBlank()
    val shown = when {
        searching -> Library.search(files, query)
        filter == Filter.Starred -> Library.starred(files)
        else -> Library.ordered(files)
    }
    val continueFile = if (searching) null else Library.ordered(files).firstOrNull()

    Box(Modifier.fillMaxSize().background(Numio.Bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { Header(onSettings) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Your PDFs", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    SearchField(query, onChange = { query = it })
                }
            }

            if (files.isEmpty()) {
                item { EmptyState() }
            } else {
                if (continueFile != null) {
                    item {
                        Column {
                            SectionLabel("Continue reading")
                            ContinueCard(continueFile, onClick = { onOpen(continueFile) })
                        }
                    }
                }
                if (!searching) {
                    item {
                        Row(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Filter.entries.forEach { f ->
                                Chip(f.label, selected = f == filter, onClick = { filter = f })
                            }
                        }
                    }
                } else {
                    item { SectionLabel(if (shown.isEmpty()) "No PDFs match “${query.trim()}”" else "Results") }
                }
                if (!searching && shown.isEmpty() && filter == Filter.Starred) {
                    item {
                        Text(
                            "Star a PDF to keep it here. Starred PDFs never drop off the list.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                items(shown, key = { it.uri }) { f ->
                    FileRow(f, now, onClick = { onOpen(f) }, onStar = { onStar(f) }, onRemove = { onRemove(f) })
                }
            }
        }

        OpenButton(
            onClick = onOpenPicker,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // RR's own Numio wordmark, used exactly as drawn
        Image(
            painter = painterResource(R.drawable.numio_wordmark),
            contentDescription = "Numio",
            modifier = Modifier.height(24.dp),
        )
        Text("PDF Reader", style = MaterialTheme.typography.bodyMedium, color = Numio.TextDim, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(PIcons.Settings, contentDescription = "Settings", tint = Numio.TextDim)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Numio.Surface)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PIcons.Search, contentDescription = null, tint = Numio.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search your PDFs by name", style = MaterialTheme.typography.bodyLarge, color = Numio.TextDim)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                cursorBrush = SolidColor(Numio.AmberText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(PIcons.Close, contentDescription = "Clear search", tint = Numio.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Numio.TextDim,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun ContinueCard(file: RecentFile, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Numio.Surface)
            .clickable(onClickLabel = "Continue reading", role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(file.uri, width = 74.dp, height = 98.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (file.pageCount > 0) {
                Text("Page ${file.lastPage + 1} of ${file.pageCount}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Numio.SurfaceHigh)) {
                    Box(
                        Modifier
                            .fillMaxWidth(Library.progress(file))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Numio.Amber),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Numio.Text) else Modifier.border(1.dp, Numio.Line, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Numio.Bg else Numio.Text)
    }
}

@Composable
private fun FileRow(
    file: RecentFile,
    now: Long,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Thumb(file.uri, width = 40.dp, height = 52.dp)
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            val parts = buildList {
                if (file.pageCount > 0) add(Library.pagesLabel(file.pageCount))
                add(Library.whenLabel(now, file.lastOpened))
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (file.starred) {
            Icon(PIcons.StarFilled, contentDescription = "Starred", tint = Numio.AmberText, modifier = Modifier.size(16.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(PIcons.More, contentDescription = "More options for ${file.name}", tint = Numio.TextDim)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = Numio.SurfaceHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(if (file.starred) "Remove star" else "Star") },
                    onClick = { menu = false; onStar() },
                )
                DropdownMenuItem(
                    text = { Text("Remove from list") },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PIcons.Page, contentDescription = null, tint = Numio.AmberText, modifier = Modifier.size(48.dp))
        Text("No PDFs yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Open a PDF from your phone, or tap one in Files, WhatsApp or email and choose PDF Reader. " +
                "Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Numio.TextDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun OpenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = Numio.Amber, spotColor = Numio.Amber)
            .clip(RoundedCornerShape(28.dp))
            .background(Numio.Amber)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PIcons.Plus, contentDescription = null, tint = Numio.OnAmber, modifier = Modifier.size(20.dp))
        Text("Open PDF", style = MaterialTheme.typography.titleSmall, color = Numio.OnAmber)
    }
}
