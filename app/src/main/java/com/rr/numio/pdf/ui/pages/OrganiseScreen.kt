package com.rr.numio.pdf.ui.pages

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rr.numio.pdf.pages.Organiser
import com.rr.numio.pdf.pages.PageItem
import com.rr.numio.pdf.pages.PagePlan
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Every page as a little picture: tap to pick pages, then turn, delete or
 * extract them; hold and drag to move one; add another PDF's pages at the end.
 */
@Composable
fun OrganiseScreen(
    org: Organiser,
    name: String,
    busy: Boolean,
    onAddPdf: () -> Unit,
    onExtract: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onNotice: (String) -> Unit,
) {
    var askLeave by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    val selecting = org.selected.isNotEmpty()
    BackHandler {
        when {
            selecting -> org.selected.clear()
            org.changed -> askLeave = true
            else -> onClose()
        }
    }

    Column(Modifier.fillMaxSize().background(Numio.Bg).statusBarsPadding().navigationBarsPadding()) {
        // ---- Top bar
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selecting) org.selected.clear() else if (org.changed) askLeave = true else onClose() }) {
                Icon(if (selecting) PIcons.Close else PIcons.Back, contentDescription = if (selecting) "Clear selection" else "Back", tint = Numio.Text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (selecting) "${org.selected.size} selected" else "Organise pages",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    if (org.items.size == 1) "1 page" else "${org.items.size} pages",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = org::selectAll) {
                Icon(PIcons.SelectAll, contentDescription = "Select all", tint = Numio.Text)
            }
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (org.changed && !busy) Numio.Amber else Numio.SurfaceHigh)
                    .clickable(enabled = org.changed && !busy) { askSave = true }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = if (org.changed && !busy) Numio.OnAmber else Numio.TextDim)
            }
        }

        Text(
            "Tap pages to pick them · hold and drag to move",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )

        // ---- The pages
        Box(Modifier.weight(1f)) {
            PageGrid(org)
            if (busy) {
                CircularProgressIndicator(
                    color = Numio.AmberText,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                )
            }
        }

        // ---- What to do with them
        Row(
            Modifier.fillMaxWidth().background(Numio.Surface).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (selecting) {
                Action(PIcons.RotateLeft, "Turn left", !busy) { org.rotate(-1) }
                Action(PIcons.RotateRight, "Turn right", !busy) { org.rotate(1) }
                Action(PIcons.Bin, "Delete", !busy) {
                    if (!org.delete()) onNotice("A PDF needs at least one page, so the last page can't be deleted.")
                }
                Action(PIcons.Extract, "Extract", !busy, onClick = onExtract)
            } else {
                Action(PIcons.Plus, "Add a PDF", !busy, onClick = onAddPdf)
            }
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            containerColor = Numio.Surface,
            title = { Text("Leave without saving?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    PagePlan.summary(org.items, org.doc.pageCount) + ". These changes will be lost.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Numio.TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    onClose()
                }) { Text("Leave", color = Numio.Error) }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Keep editing", color = Numio.TextDim) } },
        )
    }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            containerColor = Numio.Surface,
            title = { Text("Save the pages?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    PagePlan.summary(org.items, org.doc.pageCount) + " in “$name”.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Numio.TextDim,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askSave = false
                    onSave()
                }) { Text("Save", color = Numio.AmberText) }
            },
            dismissButton = { TextButton(onClick = { askSave = false }) { Text("Cancel", color = Numio.TextDim) } },
        )
    }
}

@Composable
private fun PageGrid(org: Organiser) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var autoScroll by remember { mutableStateOf<Job?>(null) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }

    fun itemAt(pos: Offset): LazyGridItemInfo? = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
            pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
    }

    /** While dragging, the page under the finger swaps places with the one being moved. */
    fun follow() {
        val key = dragKey ?: return
        val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val center = Offset(
            info.offset.x + info.size.width / 2f + dragOffset.x,
            info.offset.y + info.size.height / 2f + dragOffset.y,
        )
        val target = itemAt(center)?.takeIf { it.key != key } ?: return
        // The moved page now sits where the target was; keep it under the finger
        dragOffset += Offset(
            (info.offset.x - target.offset.x).toFloat(),
            (info.offset.y - target.offset.y).toFloat(),
        )
        org.move(info.index, target.index)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        state = grid,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(org) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        itemAt(pos)?.let {
                            dragKey = it.key as Long
                            dragOffset = Offset.Zero
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragKey == null) return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragOffset += amount
                        follow()
                        // Near the top or bottom: keep scrolling while the finger stays there
                        val y = change.position.y
                        val height = grid.layoutInfo.viewportSize.height
                        val speed = when {
                            y < edge -> -((edge - y) / edge) * 24f
                            y > height - edge -> ((y - (height - edge)) / edge) * 24f
                            else -> 0f
                        }
                        autoScroll?.cancel()
                        if (speed != 0f) {
                            autoScroll = scope.launch {
                                while (isActive) {
                                    val used = grid.scrollBy(speed)
                                    if (used == 0f) break
                                    dragOffset += Offset(0f, used)
                                    follow()
                                    delay(16)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        autoScroll?.cancel()
                        dragKey = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        autoScroll?.cancel()
                        dragKey = null
                        dragOffset = Offset.Zero
                    },
                )
            },
    ) {
        itemsIndexed(org.items, key = { _, it -> it.key }) { i, item ->
            val dragging = item.key == dragKey
            PageCell(
                org = org,
                item = item,
                number = i + 1,
                selected = item.key in org.selected,
                modifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.06f
                            scaleY = 1.06f
                            shadowElevation = 12f
                        }
                } else {
                    Modifier.animateItem()
                },
            )
        }
    }
}

@Composable
private fun PageCell(org: Organiser, item: PageItem, number: Int, selected: Boolean, modifier: Modifier) {
    val doc = org.docFor(item)
    val px = with(LocalDensity.current) { 104.dp.roundToPx() * 2 }.coerceIn(64, 360)
    val bitmap by produceState<Bitmap?>(doc?.cached(item.index, px), doc, item.index, px) {
        doc?.render(item.index, px)?.let { value = it }
    }
    val from = if (item.source == 0) null else org.added.getOrNull(item.source - 1)?.name
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(10.dp))
                .background(Numio.Desk)
                .then(if (selected) Modifier.border(3.dp, Numio.AmberText, RoundedCornerShape(10.dp)) else Modifier)
                .clickable(role = Role.Checkbox, onClickLabel = if (selected) "Unpick page $number" else "Pick page $number") {
                    org.toggle(item.key)
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { b ->
                Image(
                    b.asImageBitmap(),
                    contentDescription = "Page $number",
                    contentScale = ContentScale.Fit,
                    // A turned page is shown turned, shrunk so a sideways page still fits
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = 90f * item.turn
                            if (item.turn % 2 == 1) {
                                val cellW = size.width
                                val cellH = size.height
                                val a = b.width.toFloat() / b.height
                                val (fw, fh) = if (a > cellW / cellH) cellW to cellW / a else cellH * a to cellH
                                val s = minOf(1f, cellW / fh, cellH / fw)
                                scaleX = s
                                scaleY = s
                            }
                        },
                )
            } ?: Box(Modifier.fillMaxSize(0.8f).background(Numio.Paper))
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Numio.Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PIcons.Check, contentDescription = null, tint = Numio.OnAmber, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (from == null) "$number" else "$number · $from",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Numio.AmberText else Numio.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Numio.Text else Numio.Line, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) Numio.Text else Numio.Line)
    }
}
