package com.rr.numio.pdf.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkColors
import com.rr.numio.pdf.edit.MarkKind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/** A finger on a page: page number from 0, and position as fractions of the page. */
data class PageHit(val page: Int, val x: Float, val y: Float)

/** Amber band over the selected lines, with a handle at each end. */
@Composable
fun BoxScope.SelectionOverlay(boxes: List<NBox>, scale: () -> Float) {
    Canvas(Modifier.matchParentSize()) {
        val band = Numio.Amber.copy(alpha = 0.33f)
        boxes.forEach { b ->
            drawRect(
                color = band,
                topLeft = Offset(b.left * size.width, b.top * size.height),
                size = Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height),
            )
        }
        // Handles stay the same size on screen at any zoom
        val r = 7.dp.toPx() / scale().coerceAtLeast(1f)
        val stem = 2.dp.toPx() / scale().coerceAtLeast(1f)
        val first = boxes.first()
        val last = boxes.last()
        listOf(
            Triple(first.left * size.width, first.top * size.height, first.bottom * size.height),
            Triple(last.right * size.width, last.top * size.height, last.bottom * size.height),
        ).forEach { (x, top, bottom) ->
            drawRect(Numio.Amber, topLeft = Offset(x - stem / 2, top), size = Size(stem, bottom - top))
            drawCircle(Numio.Amber, radius = r, center = Offset(x, bottom + r * 0.9f))
        }
    }
}

/** Saved and unsaved marks on a page, drawn the way paper highlighters look. */
@Composable
fun BoxScope.MarksOverlay(marks: List<Mark>, focused: Long?, scale: () -> Float) {
    Canvas(Modifier.matchParentSize()) {
        marks.forEach { m ->
            if (m.id == focused) {
                // The mark being changed gets a dashed amber outline
                val pad = 3.dp.toPx() / scale().coerceAtLeast(1f)
                val outline = Stroke(
                    width = 1.5.dp.toPx() / scale().coerceAtLeast(1f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()) ),
                )
                m.boxes.forEach { b ->
                    drawRect(
                        color = Numio.Amber,
                        topLeft = Offset(b.left * size.width - pad, b.top * size.height - pad),
                        size = Size((b.right - b.left) * size.width + pad * 2, (b.bottom - b.top) * size.height + pad * 2),
                        style = outline,
                    )
                }
            }
            val c = Color(0xFF000000.toInt() or m.color)
            m.boxes.forEach { b ->
                val left = b.left * size.width
                val right = b.right * size.width
                val top = b.top * size.height
                val bottom = b.bottom * size.height
                val thick = maxOf(1.5.dp.toPx(), (bottom - top) * 0.08f)
                when (m.kind) {
                    // Multiply lets the black text show through, like ink from a real highlighter
                    MarkKind.HIGHLIGHT -> drawRect(
                        color = c,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        blendMode = BlendMode.Multiply,
                    )
                    // Just under the box, so it clears the tails of g, y and p
                    MarkKind.UNDERLINE -> drawRect(
                        color = c,
                        topLeft = Offset(left, bottom),
                        size = Size(right - left, thick),
                    )
                    MarkKind.STRIKEOUT -> drawRect(
                        color = c,
                        topLeft = Offset(left, (top + bottom) / 2f - thick / 2f),
                        size = Size(right - left, thick),
                    )
                }
            }
        }
    }
}

/** Shown while text is selected, in place of the page strip. */
@Composable
fun SelectionBar(
    color: Int,
    onColor: (Int) -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSelectPage: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MarkColors.all.forEach { c ->
                val selected = c == color
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (selected) Modifier.border(2.dp, Numio.Text, CircleShape) else Modifier)
                        .padding(if (selected) 4.dp else 2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000.toInt() or c))
                        .border(1.dp, Numio.Line, CircleShape)
                        .clickable(onClickLabel = MarkColors.name(c), role = Role.RadioButton) { onColor(c) },
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(PIcons.Close, contentDescription = "Done", tint = Numio.TextDim)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(
                PIcons.Highlight, "Highlight", onHighlight,
                highlight = true, enabled = MarkColors.canHighlight(color),
            )
            BarAction(PIcons.Underline, "Underline", onUnderline)
            BarAction(PIcons.Copy, "Copy", onCopy)
            BarAction(PIcons.Share, "Share", onShare)
            BarAction(PIcons.SelectAll, "Page", onSelectPage)
        }
    }
}

/** Shown after tapping a highlight / underline: change its colour, copy its text or delete it. */
@Composable
fun MarkBar(
    mark: Mark,
    onColor: (Int) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val label = when (mark.kind) {
        MarkKind.HIGHLIGHT -> "Highlight"
        MarkKind.UNDERLINE -> "Underline"
        MarkKind.STRIKEOUT -> "Strike-out"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(PIcons.Close, contentDescription = "Done", tint = Numio.TextDim)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkColors.all.filter { mark.kind != MarkKind.HIGHLIGHT || MarkColors.canHighlight(it) }.forEach { c ->
                val selected = c == mark.color
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (selected) Modifier.border(2.dp, Numio.Text, CircleShape) else Modifier)
                        .padding(if (selected) 4.dp else 2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000.toInt() or c))
                        .border(1.dp, Numio.Line, CircleShape)
                        .clickable(onClickLabel = MarkColors.name(c), role = Role.RadioButton) { onColor(c) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (mark.text.isNotBlank()) BarAction(PIcons.Copy, "Copy", onCopy)
            BarAction(PIcons.Bin, "Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> Numio.Line
        danger -> Numio.Error
        highlight -> Numio.Amber
        else -> Numio.Text
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

fun copyText(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PDF text", text))
    // Android 13 and newer show their own "Copied" message
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    if (text.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share the text.", Toast.LENGTH_SHORT).show()
    }
}
