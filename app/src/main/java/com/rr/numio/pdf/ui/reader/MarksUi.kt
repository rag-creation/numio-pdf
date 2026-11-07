package com.rr.numio.pdf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkEntry
import com.rr.numio.pdf.edit.MarkFilter
import com.rr.numio.pdf.edit.MarkKind
import com.rr.numio.pdf.edit.MarkList
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/**
 * Every mark in the PDF (saved ones and ones not saved yet) in one list:
 * filter by type, tap to go there, or share them all as text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksSheet(
    doc: PdfDoc,
    name: String,
    edits: List<Edit>,
    onOpen: (Mark) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<MarkEntry>?>(null) }
    var read by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(MarkFilter.ALL) }

    // Reads every page's marks (quick: no page text unless a highlight needs its words)
    LaunchedEffect(doc, edits.size) {
        val found = ArrayList<MarkEntry>()
        read = 0
        for (p in 0 until doc.pageCount) {
            val marks = Edits.apply(doc.text.marks(p), edits, p)
            for (m in marks) {
                val needsWords = m.kind == MarkKind.REPLACE ||
                    (m.text.isBlank() && (m.kind == MarkKind.HIGHLIGHT || m.kind == MarkKind.UNDERLINE || m.kind == MarkKind.STRIKEOUT))
                val words = if (needsWords) doc.text.page(p).textIn(m.boxes) else ""
                found += MarkEntry(m, words)
            }
            read = p + 1
            // Show what's found so far on long PDFs
            if (p % 20 == 19) entries = MarkList.sorted(found)
        }
        entries = MarkList.sorted(found)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Numio.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            val all = entries.orEmpty()
            val busy = read < doc.pageCount
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marks", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            busy -> "Reading page $read of ${doc.pageCount}…"
                            all.size == 1 -> "1 mark"
                            else -> "${all.size} marks"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val shown = all.filter { filter.matches(it.mark) }
                IconButton(onClick = { copyText(context, MarkList.export(name, shown)) }, enabled = shown.isNotEmpty() && !busy) {
                    Icon(PIcons.Copy, contentDescription = "Copy all as text", tint = if (shown.isNotEmpty() && !busy) Numio.Text else Numio.Line)
                }
                IconButton(onClick = { shareText(context, MarkList.export(name, shown)) }, enabled = shown.isNotEmpty() && !busy) {
                    Icon(PIcons.Share, contentDescription = "Share all as text", tint = if (shown.isNotEmpty() && !busy) Numio.Text else Numio.Line)
                }
            }

            // Type filters: only the kinds this PDF has
            val counts = MarkList.counts(all)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkFilter.entries.filter { it == MarkFilter.ALL || (counts[it] ?: 0) > 0 }.forEach { f ->
                    val on = f == filter
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) Numio.Amber else Numio.Bg)
                            .clickable(role = Role.RadioButton) { filter = f }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "${f.label} ${counts[f] ?: 0}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) Numio.OnAmber else Numio.Text,
                        )
                    }
                }
            }

            val shown = all.filter { filter.matches(it.mark) }
            when {
                entries == null -> Hint("Looking for marks…")
                shown.isEmpty() && !busy -> Hint(
                    "No marks in this PDF yet. Long-press text to highlight it, or use Pen, Text, Sign and Note below the page.",
                )
                else -> LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    // A page heading before each page's marks
                    val rows = buildList<Any> {
                        var page = -1
                        shown.forEach { e ->
                            if (e.mark.page != page) {
                                page = e.mark.page
                                add(page)
                            }
                            add(e)
                        }
                    }
                    items(rows, key = { r -> if (r is Int) "page$r" else "mark${(r as MarkEntry).mark.page}_${r.mark.id}" }) { r ->
                        if (r is Int) {
                            Text(
                                "PAGE ${r + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Numio.TextDim,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                            )
                        } else {
                            MarkRow(r as MarkEntry) { onOpen(r.mark) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkRow(entry: MarkEntry, onClick: () -> Unit) {
    val m = entry.mark
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Go to this mark", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Numio.Bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(m.kind), contentDescription = null, tint = Numio.TextDim, modifier = Modifier.size(20.dp))
            if (m.kind != MarkKind.REPLACE && m.kind != MarkKind.SIGN && m.kind != MarkKind.TEXT) {
                // The mark's own colour as a small dot
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000.toInt() or m.color))
                        .border(1.5.dp, Numio.Surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                MarkList.title(m) + if (m.saved) "" else " · not saved",
                style = MaterialTheme.typography.labelMedium,
                color = if (m.saved) Numio.TextDim else Numio.AmberText,
            )
            val detail = MarkList.detail(entry)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun kindIcon(kind: MarkKind): ImageVector = when (kind) {
    MarkKind.HIGHLIGHT -> PIcons.Highlight
    MarkKind.UNDERLINE, MarkKind.STRIKEOUT -> PIcons.Underline
    MarkKind.INK -> PIcons.Pen
    MarkKind.TEXT, MarkKind.REPLACE -> PIcons.TextBox
    MarkKind.NOTE -> PIcons.Note
    MarkKind.SIGN -> PIcons.Sign
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Numio.TextDim,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
}
