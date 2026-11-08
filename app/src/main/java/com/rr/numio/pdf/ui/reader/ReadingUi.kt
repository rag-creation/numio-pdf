package com.rr.numio.pdf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.data.Bookmark
import com.rr.numio.pdf.data.OutlineEntry
import com.rr.numio.pdf.data.PageLayout
import com.rr.numio.pdf.data.PageLook
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/**
 * Night pages: light and dark swapped, but colours keep their hue (a red
 * heading stays reddish, not cyan), and pure white becomes a soft near-black.
 * It's "turn everything over" followed by a half-turn of the colour wheel.
 */
private val darkMatrix = ColorMatrix(
    floatArrayOf(
        0.5166f, -1.287f, -0.1296f, 0f, 240f,
        -0.3834f, -0.387f, -0.1296f, 0f, 240f,
        -0.3834f, -1.287f, 0.7704f, 0f, 240f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Warm paper: white turns cream, black stays black. */
private val sepiaMatrix = ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 0.93f, 0f, 0f, 0f,
        0f, 0f, 0.80f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

fun pageFilter(look: PageLook): ColorFilter? = when (look) {
    PageLook.NORMAL -> null
    PageLook.DARK -> ColorFilter.colorMatrix(darkMatrix)
    PageLook.SEPIA -> ColorFilter.colorMatrix(sepiaMatrix)
}

/** The colour behind a page while it's still being drawn. */
fun pageBlank(look: PageLook): Color = when (look) {
    PageLook.NORMAL -> Numio.Paper
    PageLook.DARK -> Color(0xFF151514)
    PageLook.SEPIA -> Color(0xFFFFEDCC)
}

/**
 * On night pages, marks drawn in black (pen, text) would vanish, so very dark
 * colours are shown light instead; other colours stay as they are. Only how
 * they look on screen changes, never what's saved.
 */
fun onPage(color: Int, look: PageLook): Int {
    if (look != PageLook.DARK) return color
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    val light = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    return if (light < 0.25f) 0xE6E4DE else color
}

/** The ⋮ menu: contents, marks, page colour, layout and go to page. */
@Composable
fun ReadingMenu(
    open: Boolean,
    look: PageLook,
    layout: PageLayout,
    starred: Boolean,
    onDismiss: () -> Unit,
    onContents: () -> Unit,
    onMarks: () -> Unit,
    onOrganise: () -> Unit,
    onLook: (PageLook) -> Unit,
    onLayout: (PageLayout) -> Unit,
    onGoTo: () -> Unit,
    onStar: () -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = Numio.SurfaceHigh) {
        DropdownMenuItem(
            text = { Text("Contents & bookmarks") },
            leadingIcon = { Icon(PIcons.Contents, contentDescription = null, tint = Numio.Text) },
            onClick = { onDismiss(); onContents() },
        )
        DropdownMenuItem(
            text = { Text("Marks: highlights, notes…") },
            leadingIcon = { Icon(PIcons.Highlight, contentDescription = null, tint = Numio.Text) },
            onClick = { onDismiss(); onMarks() },
        )
        DropdownMenuItem(
            text = { Text("Organise pages") },
            leadingIcon = { Icon(PIcons.Pages, contentDescription = null, tint = Numio.Text) },
            onClick = { onDismiss(); onOrganise() },
        )
        DropdownMenuItem(
            text = { Text("Go to page") },
            leadingIcon = { Icon(PIcons.Page, contentDescription = null, tint = Numio.Text) },
            onClick = { onDismiss(); onGoTo() },
        )
        DropdownMenuItem(
            text = { Text(if (starred) "Remove star" else "Star this PDF") },
            leadingIcon = {
                Icon(if (starred) PIcons.StarFilled else PIcons.Star, contentDescription = null, tint = if (starred) Numio.AmberText else Numio.Text)
            },
            onClick = { onDismiss(); onStar() },
        )
        HorizontalDivider(color = Numio.Line)
        MenuLabel("Page colour")
        listOf(PageLook.NORMAL to "Normal", PageLook.DARK to "Night", PageLook.SEPIA to "Sepia").forEach { (value, label) ->
            CheckItem(label, value == look) { onLook(value) }
        }
        HorizontalDivider(color = Numio.Line)
        MenuLabel("Layout")
        listOf(
            PageLayout.SCROLL to "Scroll",
            PageLayout.ONE_PAGE to "One page at a time",
            PageLayout.BOOK to "Book: full screen, swipe pages",
        ).forEach { (value, label) ->
            CheckItem(label, value == layout) { onLayout(value) }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Numio.TextDim,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (checked) Numio.AmberText else Numio.Text) },
        leadingIcon = {
            if (checked) Icon(PIcons.Check, contentDescription = "Selected", tint = Numio.AmberText)
            else Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
    )
}

/** The PDF's table of contents and the reader's own bookmarks, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsSheet(
    doc: PdfDoc,
    current: Int,
    bookmarks: List<Bookmark>,
    onJump: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onRename: (page: Int, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val outline by produceState<List<OutlineEntry>?>(null, doc) { value = doc.text.outline() }
    var tab by remember { mutableIntStateOf(if (bookmarks.isNotEmpty()) 1 else 0) }
    var renaming by remember { mutableStateOf<Bookmark?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Numio.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Numio.Bg).padding(4.dp),
            ) {
                listOf("Contents", "Bookmarks (${bookmarks.size})").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (i == tab) Numio.SurfaceHigh else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (i == tab) Numio.Text else Numio.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) {
                val entries = outline
                when {
                    entries == null -> Empty("Reading the contents…")
                    entries.isEmpty() -> Empty("This PDF has no table of contents. Bookmarks work on any PDF.")
                    else -> LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(entries) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = e.page >= 0) { onJump(e.page) }
                                    .padding(start = 20.dp + (e.depth.coerceAtMost(4) * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    e.title,
                                    style = if (e.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (e.page >= 0) {
                                    Text(
                                        "${e.page + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (e.page == current) Numio.AmberText else Numio.TextDim,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val here = bookmarks.any { it.page == current }
                TextButton(
                    onClick = { onToggleBookmark(current) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(if (here) PIcons.BookmarkFilled else PIcons.Bookmark, contentDescription = null, tint = Numio.AmberText)
                    Spacer(Modifier.width(8.dp))
                    Text(if (here) "Remove bookmark on page ${current + 1}" else "Bookmark page ${current + 1}", color = Numio.AmberText)
                }
                if (bookmarks.isEmpty()) {
                    Empty("No bookmarks yet. Tap the bookmark at the top while reading, or the button above.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(bookmarks, key = { it.page }) { b ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onJump(b.page) }
                                    .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(PIcons.BookmarkFilled, contentDescription = null, tint = Numio.AmberText, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Page ${b.page + 1}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { renaming = b }) {
                                    Icon(PIcons.Pen, contentDescription = "Rename", tint = Numio.TextDim)
                                }
                                IconButton(onClick = { onToggleBookmark(b.page) }) {
                                    Icon(PIcons.Bin, contentDescription = "Remove bookmark", tint = Numio.Error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { b ->
        var name by remember(b) { mutableStateOf(b.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Numio.Surface,
            title = { Text("Bookmark name", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace('\n', ' ').take(80) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Numio.AmberText, cursorColor = Numio.AmberText),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(b.page, name); renaming = null }) { Text("Save", color = Numio.AmberText) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = Numio.TextDim) } },
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Numio.TextDim,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
    )
}
