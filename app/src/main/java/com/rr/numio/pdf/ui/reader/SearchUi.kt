package com.rr.numio.pdf.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.text.SearchHit
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/** What the search has found so far, for the bar. */
data class SearchStatus(
    val found: Int,
    /** Which result is shown, counted from 0; -1 for none yet. */
    val at: Int,
    val searched: Int,
    val pages: Int,
    /** Every page read and none had text: a scanned PDF. */
    val noText: Boolean,
)

/** Replaces the top bar while searching: the words, how many found, previous / next. */
@Composable
fun SearchBar(
    query: String,
    status: SearchStatus,
    onQuery: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val busy = status.searched < status.pages
    Row(
        Modifier
            .fillMaxWidth()
            .background(Numio.Bg.copy(alpha = 0.96f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(PIcons.Back, contentDescription = "Close search", tint = Numio.Text)
        }
        Box(Modifier.weight(1f).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Find in this PDF", style = MaterialTheme.typography.bodyLarge, color = Numio.TextDim)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                cursorBrush = SolidColor(Numio.AmberText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onNext()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotBlank()) {
            val label = when {
                status.noText -> "No text"
                status.found == 0 && busy -> "…"
                status.found == 0 -> "None"
                status.at >= 0 -> "${status.at + 1}/${status.found}${if (busy) "+" else ""}"
                else -> "${status.found}${if (busy) "+" else ""}"
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (status.found == 0 && !busy) Numio.TextDim else Numio.Text,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        IconButton(onClick = onPrevious, enabled = status.found > 0) {
            Icon(PIcons.Up, contentDescription = "Previous result", tint = if (status.found > 0) Numio.Text else Numio.Line)
        }
        IconButton(onClick = onNext, enabled = status.found > 0) {
            Icon(PIcons.Down, contentDescription = "Next result", tint = if (status.found > 0) Numio.Text else Numio.Line)
        }
    }
}

/** Search results on a page: every one softly lit, the current one outlined. */
@Composable
fun BoxScope.SearchOverlay(hits: List<SearchHit>, active: SearchHit?, scale: () -> Float) {
    Canvas(Modifier.matchParentSize()) {
        val soft = SearchLit.copy(alpha = 0.32f)
        val strong = SearchLit.copy(alpha = 0.55f)
        val pad = 1.5.dp.toPx() / scale().coerceAtLeast(1f)
        hits.forEach { hit ->
            val on = hit == active
            hit.boxes.forEach { b ->
                val topLeft = Offset(b.left * size.width - pad, b.top * size.height - pad)
                val box = Size((b.right - b.left) * size.width + pad * 2, (b.bottom - b.top) * size.height + pad * 2)
                val corner = CornerRadius(2.dp.toPx() / scale().coerceAtLeast(1f))
                drawRoundRect(if (on) strong else soft, topLeft, box, corner)
                if (on) {
                    drawRoundRect(
                        SearchRing, topLeft, box, corner,
                        style = Stroke(width = 2.dp.toPx() / scale().coerceAtLeast(1f)),
                    )
                }
            }
        }
    }
}

// On the page itself (paper), so the same in light and dark themes
private val SearchLit = Color(0xFFFFC21A)
private val SearchRing = Color(0xFFE07A00)
