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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import com.rr.numio.pdf.edit.Ink
import com.rr.numio.pdf.edit.TextBox
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.nativeCanvas
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
                    // Preview until saved: the old words hidden behind the page's own white
                    MarkKind.REPLACE -> drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                    )
                    MarkKind.INK, MarkKind.TEXT, MarkKind.NOTE -> Unit // drawn below
                }
            }
            when (m.kind) {
                MarkKind.INK -> drawInk(m.ink, m.width, c)
                MarkKind.TEXT -> {
                    val b = m.boxes.first()
                    TextBox.draw(
                        drawContext.canvas.nativeCanvas, m.text, m.fontSize * size.width,
                        0xFF000000.toInt() or m.color, b.left * size.width, b.top * size.height,
                        m.fill, b.width() * size.width, b.height() * size.height,
                    )
                }
                MarkKind.NOTE -> drawNote(m.boxes.first(), c)
                MarkKind.REPLACE -> if (m.text.isNotEmpty()) {
                    val b = m.boxes.first()
                    TextBox.draw(
                        drawContext.canvas.nativeCanvas, m.text, m.fontSize * size.width,
                        0xFF000000.toInt(), b.left * size.width - m.fontSize * size.width * 0.35f,
                        b.top * size.height - m.fontSize * size.width * 0.2f,
                    )
                }
                else -> Unit
            }
        }
    }
}

/** A sticky note icon: a little coloured sheet with three lines of "writing". */
fun DrawScope.drawNote(b: NBox, color: Color) {
    val l = b.left * size.width
    val t = b.top * size.height
    val w = b.width() * size.width
    val h = b.height() * size.height
    val ink = Color(0xFF2A2925)
    drawRoundRect(color, topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(w * 0.14f))
    drawRoundRect(ink, topLeft = Offset(l, t), size = Size(w, h), cornerRadius = CornerRadius(w * 0.14f), style = Stroke(w * 0.05f))
    for (k in 0 until 3) {
        val y = t + h * (0.32f + k * 0.18f)
        drawLine(ink, Offset(l + w * 0.24f, y), Offset(l + w * (if (k == 2) 0.55f else 0.76f), y), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}

/** Pen strokes with round ends; a single point becomes a dot. */
fun DrawScope.drawInk(strokes: List<FloatArray>, width: Float, color: Color) {
    val w = (width * size.width).coerceAtLeast(1f)
    strokes.forEach { p ->
        if (p.size < 2) return@forEach
        if (p.size == 2) {
            drawCircle(color, radius = w / 2f, center = Offset(p[0] * size.width, p[1] * size.height))
            return@forEach
        }
        val path = Path().apply {
            moveTo(p[0] * size.width, p[1] * size.height)
            var i = 2
            while (i + 1 < p.size) {
                lineTo(p[i] * size.width, p[i + 1] * size.height)
                i += 2
            }
        }
        drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The stroke being drawn right now, before the finger lifts. */
@Composable
fun BoxScope.LiveInk(points: FloatArray, width: Float, color: Int) {
    Canvas(Modifier.matchParentSize()) {
        drawInk(listOf(points), width, Color(0xFF000000.toInt() or color))
    }
}

/** Shown while drawing: pen colour, pen size and Done. */
@Composable
fun PenBar(
    color: Int,
    width: Float,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onDone: () -> Unit,
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
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Pen", style = MaterialTheme.typography.titleSmall)
                Text("Two fingers to scroll or zoom", style = MaterialTheme.typography.bodySmall)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Numio.Amber)
                    .clickable(role = Role.Button, onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text("Done", style = MaterialTheme.typography.labelLarge, color = Numio.OnAmber)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            Ink.widths.forEachIndexed { i, w ->
                val selected = w == width
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (selected) Numio.SurfaceHigh else Color.Transparent)
                        .clickable(onClickLabel = listOf("Fine", "Medium", "Bold")[i], role = Role.RadioButton) { onWidth(w) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size((4 + i * 4).dp)
                            .clip(CircleShape)
                            .background(if (selected) Numio.Amber else Numio.Text),
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
    onReplace: () -> Unit,
    onRemove: () -> Unit,
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
        // Changing the page's own text
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextChip("Replace text", onReplace, Modifier.weight(1f))
            TextChip("Remove text", onRemove, Modifier.weight(1f))
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
    onEdit: (() -> Unit)? = null,
) {
    val hint = when (mark.kind) {
        MarkKind.TEXT -> "Drag to move \u00b7 pinch to resize"
        MarkKind.NOTE -> "Drag to move"
        else -> null
    }
    val label = when (mark.kind) {
        MarkKind.HIGHLIGHT -> "Highlight"
        MarkKind.UNDERLINE -> "Underline"
        MarkKind.STRIKEOUT -> "Strike-out"
        MarkKind.INK -> "Drawing"
        MarkKind.TEXT -> "Text"
        MarkKind.NOTE -> "Note"
        MarkKind.REPLACE -> if (mark.text.isEmpty()) "Removed text" else "Replaced text"
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
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) {
                Icon(PIcons.Close, contentDescription = "Done", tint = Numio.TextDim)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarkColors.all.filter {
                mark.kind != MarkKind.REPLACE && (mark.kind != MarkKind.HIGHLIGHT || MarkColors.canHighlight(it))
            }.forEach { c ->
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
            if (onEdit != null) BarAction(PIcons.Pen, "Edit", onEdit)
            else if (mark.text.isNotBlank()) BarAction(PIcons.Copy, "Copy", onCopy)
            BarAction(PIcons.Bin, "Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun TextChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Numio.Line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BarAction(
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
