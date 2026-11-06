package com.rr.numio.pdf.ui.reader

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.ColorSample
import com.rr.numio.pdf.edit.Signature
import com.rr.numio.pdf.data.SignatureStore
import com.rr.numio.pdf.form.FieldKind
import com.rr.numio.pdf.form.FormField
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.edit.Ink
import com.rr.numio.pdf.edit.TextBox
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkColors
import com.rr.numio.pdf.edit.MarkKind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.rr.numio.pdf.text.Selection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.ReaderState
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val PAGE_GAP = 10.dp
private val PAGE_SIDE = 8.dp

@Composable
fun ReaderScreen(
    state: ReaderState.Open,
    starred: Boolean,
    edits: List<Edit>,
    onBack: () -> Unit,
    onStar: () -> Unit,
    onShare: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onMark: (Mark) -> Long,
    onRemoveMark: (Mark) -> Unit,
    onRecolorMark: (Mark, Int) -> Unit,
    onChangeMark: (old: Mark, new: Mark) -> Unit,
    onFill: (field: String, value: String) -> Unit,
    signatures: SignatureStore,
    signaturesUnlocked: Boolean,
    onSignaturesUnlocked: () -> Unit,
    onUndo: () -> Unit,
    onSave: (page: Int) -> Unit,
) {
    val doc = state.doc
    val count = doc.pageCount
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.startPage.coerceIn(0, count - 1))
    val scope = rememberCoroutineScope()
    var chrome by remember { mutableStateOf(true) }
    var askPage by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var markColor by rememberSaveable { mutableIntStateOf(MarkColors.YELLOW) }
    var drawing by rememberSaveable { mutableStateOf(false) }
    // Text box or note waiting for a tap on the page, then the one being written
    var placing by remember { mutableStateOf<MarkKind?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var textColor by rememberSaveable { mutableIntStateOf(MarkColors.BLACK) }
    var textSize by rememberSaveable { mutableIntStateOf(1) }
    var noteColor by rememberSaveable { mutableIntStateOf(MarkColors.YELLOW) }
    // A text box or note being dragged or pinched, shown in place of the saved one
    var preview by remember { mutableStateOf<Mark?>(null) }
    // Signing: the sheet is open, then the chosen signature waits for a tap on the page
    var signing by remember { mutableStateOf(false) }
    var pendingSignature by remember { mutableStateOf<Signature?>(null) }
    // A text or choice form field being filled
    var fillingField by remember { mutableStateOf<FormField?>(null) }

    /** A field's value as saved in the file (for radio groups, the ticked box anywhere in the form). */
    fun currentValue(field: FormField): String {
        if (field.kind != FieldKind.RADIO && field.kind != FieldKind.CHECK) return field.value
        return (0 until doc.pageCount).asSequence()
            .flatMap { doc.text.cachedFields(it).asSequence() }
            .filter { it.name == field.name }
            .map { it.value }
            .firstOrNull { it != "Off" } ?: "Off"
    }
    // Selected text waiting for its replacement words
    var replacing by remember { mutableStateOf<Selection?>(null) }

    /** Changes the page's own text under [sel] to [text] when saved (empty removes it). */
    fun replaceMark(sel: Selection, text: String): Mark {
        // Same size, line and font style as the first selected letter, for the preview
        val g = sel.firstGlyph
        val first = sel.boxes.first()
        val size = g?.size?.takeIf { it > 0f } ?: (first.height() * doc.pages[sel.page].aspect / 1.07f)
        val line = g?.baseline?.takeIf { it > 0f } ?: (first.bottom - first.height() * 0.25f)
        return Mark(
            sel.page, MarkKind.REPLACE, sel.boxes, 0x000000, text,
            fontSize = size, baseline = line, fontName = g?.font ?: "",
        )
    }

    /**
     * Adds a text change after reading the colours around it from the page, so
     * the preview shows e.g. white text on a red header, not black on white.
     */
    fun addReplace(sel: Selection, text: String) {
        val base = replaceMark(sel, text)
        // Changing the same words again replaces the earlier change instead of stacking
        Edits.apply(emptyList(), edits, sel.page)
            .filter { m -> m.kind == MarkKind.REPLACE && m.boxes.any { a -> base.boxes.any { b -> overlaps(a, b) } } }
            .forEach(onRemoveMark)
        scope.launch {
            val page = doc.render(sel.page, 800)
            val mark = if (page == null) base else {
                val w = page.width
                val h = page.height
                val px = IntArray(w * h)
                page.getPixels(px, 0, w, 0, 0, w, h)
                val b = sel.boxes.first()
                val (bg, ink) = ColorSample.colors(
                    px, w, h,
                    (b.left * w).toInt() - 2, (b.top * h).toInt() - 2,
                    (b.right * w).toInt() + 2, (b.bottom * h).toInt() + 2,
                )
                base.copy(color = ink, fill = bg)
            }
            onMark(mark)
        }
    }
    var penColor by rememberSaveable { mutableIntStateOf(MarkColors.BLACK) }
    var penWidth by rememberSaveable { mutableFloatStateOf(Ink.widths[1]) }
    // The mark tapped for changing (page and id); looked up fresh so colour changes and Undo show at once
    var editingRef by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val editing: Mark? = editingRef?.let { (page, id) ->
        Edits.apply(doc.text.cachedMarks(page), edits, page).firstOrNull { it.id == id }
    }

    fun tapped(hit: PageHit?) {
        val place = placing
        when {
            place == MarkKind.SIGN -> {
                val sig = pendingSignature
                if (hit != null && sig != null) {
                    // About a third of the page wide, centred on the finger
                    val aspect = doc.pages[hit.page].aspect
                    val w = 0.32f
                    val h = (w / sig.aspect) / aspect
                    val box = TextBox.moved(
                        Mark(hit.page, MarkKind.SIGN, listOf(NBox(0f, 0f, w, h)), 0),
                        hit.x - w / 2f, hit.y - h / 2f, 1f, aspect,
                    ).boxes.first()
                    val mark = Mark(
                        hit.page, MarkKind.SIGN, listOf(box), sig.color, sig.text,
                        ink = sig.strokes, width = sig.penWidth, fontName = sig.font,
                    )
                    editingRef = hit.page to onMark(mark)
                    placing = null
                    pendingSignature = null
                }
            }
            place != null -> if (hit != null) {
                draft = Draft(place, hit.page, hit.x.coerceIn(0f, 1f), hit.y.coerceIn(0f, 1f), null)
                placing = null
            }
            selection != null -> selection = null
            editingRef != null -> editingRef = null
            else -> {
                val mark = hit?.let { h ->
                    Edits.apply(doc.text.cachedMarks(h.page), edits, h.page).lastOrNull { it.contains(h.x, h.y) }
                }
                // A form box under the finger: tick it, or open it for typing / choosing
                val field = if (mark == null && hit != null) {
                    doc.text.cachedFields(hit.page).lastOrNull { !it.readOnly && it.contains(hit.x, hit.y) }
                } else {
                    null
                }
                when {
                    mark != null -> editingRef = mark.page to mark.id
                    field == null -> chrome = !chrome
                    field.kind == FieldKind.CHECK || field.kind == FieldKind.RADIO -> {
                        val now = Edits.fills(edits)[field.name] ?: currentValue(field)
                        onFill(field.name, FormField.tapped(field, now))
                    }
                    else -> fillingField = field
                }
            }
        }
    }
    var findingText by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Long-press: find the text on that page (read once, then kept) and select the word
    fun longPress(hit: PageHit) {
        editingRef = null
        scope.launch {
            val slow = launch {
                delay(250)
                findingText = true
            }
            val text = doc.text.page(hit.page)
            slow.cancel()
            findingText = false
            if (text.isEmpty) {
                Toast.makeText(context, "No text to select on this page. It may be a scanned image.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val i = text.hit(hit.x, hit.y) ?: return@launch
            val word = text.wordAt(i)
            selection = Selection(hit.page, text, word.first, word.last)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun dragHandle(start: Boolean, hit: PageHit) {
        val s = selection ?: return
        if (hit.page != s.page) return
        val i = s.text.nearest(hit.x, hit.y) ?: return
        val next = if (start) s.copy(start = i) else s.copy(end = i)
        if (next != s) selection = next
    }

    // After a jump (thumbnail or Go to page) that page is current until you scroll,
    // even if it is too short to reach the middle of the screen.
    var pinned by remember { mutableStateOf<Int?>(null) }
    val jumpJob = remember { arrayOfNulls<Job>(1) }
    fun jump(index: Int) {
        jumpJob[0]?.cancel()
        jumpJob[0] = scope.launch {
            listState.scrollToItem(index)
            pinned = index
            val here = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.first { it != here }
            pinned = null
        }
    }

    // Otherwise the page under the middle of the screen is the one being read.
    // At the very end the last page counts, so a short last page (like a wide
    // landscape one) can still become current.
    val current by remember {
        derivedStateOf {
            pinned ?: run {
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo
                if (!listState.canScrollForward && listState.canScrollBackward && visible.isNotEmpty()) {
                    visible.last().index
                } else {
                    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    visible.firstOrNull { it.offset <= middle && it.offset + it.size >= middle }?.index
                        ?: listState.firstVisibleItemIndex
                }
            }
        }
    }

    // Remember the reading place, a moment after scrolling settles
    val savePage by rememberUpdatedState(onPageChanged)
    LaunchedEffect(listState) {
        snapshotFlow { current }.collectLatest {
            delay(300)
            savePage(it)
        }
    }

    Box(Modifier.fillMaxSize().background(Numio.Bg)) {
        Pages(
            doc = doc,
            listState = listState,
            selection = selection,
            edits = edits,
            focused = editing?.id,
            movable = editing?.takeIf { it.kind == MarkKind.TEXT || it.kind == MarkKind.NOTE || it.kind == MarkKind.SIGN },
            preview = preview,
            onPreview = { preview = it },
            onMoved = { old, new -> onChangeMark(old, new) },
            pen = if (drawing) Pen(penColor, penWidth) else null,
            onStroke = { page, points ->
                val stroke = Ink.simplify(points)
                onMark(
                    Mark(
                        page, MarkKind.INK, listOf(Ink.bounds(listOf(stroke), penWidth)), penColor,
                        ink = listOf(stroke), width = penWidth,
                    ),
                )
            },
            onTap = ::tapped,
            onLongPress = ::longPress,
            onDragHandle = ::dragHandle,
        )

        AnimatedVisibility(
            visible = chrome || drawing,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(
                name = state.name,
                current = current,
                count = count,
                starred = starred,
                unsaved = edits.size,
                onBack = onBack,
                onStar = onStar,
                onUndo = onUndo,
                onSave = { onSave(current) },
            )
        }

        AnimatedVisibility(
            visible = chrome || listState.isScrollInProgress,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 80.dp, end = 14.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Numio.SurfaceHigh)
                    .clickable(onClickLabel = "Go to page", role = Role.Button) { askPage = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("${current + 1} / $count", style = MaterialTheme.typography.labelMedium)
            }
        }

        AnimatedVisibility(
            visible = selection != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            fun mark(kind: MarkKind) {
                val sel = selection ?: return
                onMark(Mark(sel.page, kind, sel.boxes, markColor, sel.string))
                selection = null
            }
            SelectionBar(
                color = markColor,
                onColor = { markColor = it },
                onHighlight = { mark(MarkKind.HIGHLIGHT) },
                onUnderline = { mark(MarkKind.UNDERLINE) },
                onCopy = {
                    selection?.let { copyText(context, it.string) }
                    selection = null
                },
                onShare = {
                    selection?.let { shareText(context, it.string) }
                },
                onReplace = {
                    selection?.let { replacing = it }
                    selection = null
                },
                onRemove = {
                    selection?.let { addReplace(it, "") }
                    selection = null
                },
                onSelectPage = {
                    selection = selection?.let { it.copy(start = it.text.firstIndex, end = it.text.lastIndex) }
                },
                onClose = { selection = null },
            )
        }

        if (findingText) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Numio.SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Finding text\u2026", style = MaterialTheme.typography.labelMedium)
            }
        }

        AnimatedVisibility(
            visible = drawing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PenBar(
                color = penColor,
                width = penWidth,
                onColor = { penColor = it },
                onWidth = { penWidth = it },
                onDone = { drawing = false },
            )
        }

        AnimatedVisibility(
            visible = editing != null && selection == null && !drawing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Keeps showing the last mark while sliding away
            val shown = remember { arrayOfNulls<Mark>(1) }
            editing?.let { shown[0] = it }
            shown[0]?.let { m ->
                val boxy = m.kind == MarkKind.TEXT || m.kind == MarkKind.NOTE || m.kind == MarkKind.SIGN
                MarkBar(
                    mark = m,
                    onColor = { c ->
                        editing?.let {
                            // Text boxes and notes are redrawn whole, so a colour change is a change
                            if (boxy) onChangeMark(it, it.copy(color = c)) else onRecolorMark(it, c)
                        }
                    },
                    onEdit = if (boxy && m.kind != MarkKind.SIGN) {
                        {
                            editing?.let { e ->
                                draft = Draft(e.kind, e.page, e.boxes.first().left, e.boxes.first().top, e)
                            }
                            editingRef = null
                        }
                    } else {
                        null
                    },
                    onCopy = {
                        copyText(context, m.text)
                        editingRef = null
                    },
                    onDelete = {
                        editing?.let(onRemoveMark)
                        editingRef = null
                    },
                    onClose = { editingRef = null },
                )
            }
        }

        AnimatedVisibility(
            visible = chrome && selection == null && editing == null && !drawing && placing == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(
                doc = doc,
                current = current,
                onJump = ::jump,
                onShare = onShare,
                onPen = {
                    selection = null
                    editingRef = null
                    drawing = true
                },
                onText = {
                    selection = null
                    editingRef = null
                    placing = MarkKind.TEXT
                },
                onNote = {
                    selection = null
                    editingRef = null
                    placing = MarkKind.NOTE
                },
                onSign = {
                    selection = null
                    editingRef = null
                    signing = true
                },
            )
        }
    }

    // "Tap where it goes" while placing a text box or note
    if (placing != null) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Numio.Surface)
                    .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (placing) {
                        MarkKind.NOTE -> "Tap where the note goes"
                        MarkKind.SIGN -> "Tap where the signature goes"
                        else -> "Tap where the text goes"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    placing = null
                    pendingSignature = null
                }) { Text("Cancel", color = Numio.TextDim) }
            }
        }
    }

    draft?.let { d -> key(d) {
        val old = d.existing
        val aspect = doc.pages[d.page].aspect
        WriteDialog(
            title = when {
                d.kind == MarkKind.NOTE && old == null -> "New note"
                d.kind == MarkKind.NOTE -> "Note"
                old == null -> "Add text"
                else -> "Edit text"
            },
            initialText = old?.text ?: "",
            initialColor = old?.color ?: if (d.kind == MarkKind.NOTE) noteColor else textColor,
            initialSize = if (d.kind == MarkKind.NOTE) null
            else old?.let { o -> Mark.fontSizes.indices.minByOrNull { kotlin.math.abs(Mark.fontSizes[it] - o.fontSize) } } ?: textSize,
            initialFill = (old?.fill ?: -1) >= 0,
            isNew = old == null,
            onDone = { text, color, size, fill ->
                val mark = if (d.kind == MarkKind.NOTE) {
                    noteColor = color
                    Mark(d.page, MarkKind.NOTE, listOf(TextBox.noteBox(d.x, d.y, aspect, centred = old == null)), color, text)
                } else {
                    textColor = color
                    textSize = size
                    val fs = Mark.fontSizes[size]
                    Mark(
                        d.page, MarkKind.TEXT, listOf(TextBox.box(text, fs, d.x, d.y, aspect, centred = old == null)),
                        color, text, fontSize = fs, fill = if (fill) 0xFFFFFF else -1,
                    )
                }
                if (old == null) {
                    // Picked straight away, so it can be dragged into place or pinched
                    editingRef = d.page to onMark(mark)
                } else {
                    onChangeMark(old, mark)
                    editingRef = d.page to old.id
                }
                draft = null
            },
            onDelete = old?.let { o -> { onRemoveMark(o); draft = null } },
            onDismiss = { draft = null },
        )
    } }

    replacing?.let { sel -> key(sel) {
        var text by remember { mutableStateOf(sel.string) }
        AlertDialog(
            onDismissRequest = { replacing = null },
            containerColor = Numio.Surface,
            title = { Text("Replace text", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.replace('\n', ' ') },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Numio.Amber,
                            cursorColor = Numio.Amber,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "The old words are taken out of the page and these are written in their place, " +
                            "in the closest standard font.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    addReplace(sel, text.trim())
                    replacing = null
                }) { Text("Replace", color = Numio.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { replacing = null }) { Text("Cancel", color = Numio.TextDim) }
            },
        )
    } }

    fillingField?.let { f -> key(f) {
        val now = Edits.fills(edits)[f.name] ?: f.value
        val done: (String) -> Unit = { v ->
            if (v != now) onFill(f.name, v)
            fillingField = null
        }
        if (f.kind == FieldKind.CHOICE && f.options.isNotEmpty()) {
            FieldChoiceDialog(f, now, onDone = done, onDismiss = { fillingField = null })
        } else {
            FieldTextDialog(f, now, onDone = done, onDismiss = { fillingField = null })
        }
    } }

    if (signing) {
        SignDialog(
            store = signatures,
            unlocked = signaturesUnlocked,
            onUnlocked = onSignaturesUnlocked,
            onUse = { sig ->
                signing = false
                pendingSignature = sig
                placing = MarkKind.SIGN
            },
            onDismiss = { signing = false },
        )
    }

    if (askPage) {
        GoToPageDialog(
            count = count,
            onDismiss = { askPage = false },
            onGo = {
                askPage = false
                jump(it)
            },
        )
    }
}

/**
 * All pages in one scrolling column. Pinch or double-tap to zoom; once zoomed,
 * one finger pans and the gesture code scrolls the list itself, so moving a
 * finger moves the page by the same distance on screen at any zoom.
 */
@Composable
private fun Pages(
    doc: PdfDoc,
    listState: LazyListState,
    selection: Selection?,
    edits: List<Edit>,
    focused: Long?,
    movable: Mark?,
    preview: Mark?,
    onPreview: (Mark?) -> Unit,
    onMoved: (old: Mark, new: Mark) -> Unit,
    pen: Pen?,
    onStroke: (page: Int, points: FloatArray) -> Unit,
    onTap: (PageHit?) -> Unit,
    onLongPress: (PageHit) -> Unit,
    onDragHandle: (start: Boolean, hit: PageHit) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val zoomed by remember { derivedStateOf { scale > 1.001f } }
    val sharp by remember { derivedStateOf { scale >= 1.6f } }
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val dragHandle by rememberUpdatedState(onDragHandle)
    val currentSelection by rememberUpdatedState(selection)
    val currentPen by rememberUpdatedState(pen)
    val currentMovable by rememberUpdatedState(movable)
    val showPreview by rememberUpdatedState(onPreview)
    val moved by rememberUpdatedState(onMoved)
    val stroke by rememberUpdatedState(onStroke)
    // The stroke under the finger right now
    var live by remember { mutableStateOf<LiveStroke?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val pageWidth: Dp = maxWidth - PAGE_SIDE * 2
        val baseWidthPx = with(density) { pageWidth.roundToPx() }.coerceAtLeast(1)
        // Zoomed in far enough: draw pages at twice the size so text stays sharp
        val renderWidth = if (sharp) baseWidthPx * 2 else baseWidthPx

        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

        fun clamp(w: Float, h: Float) {
            offX = offX.coerceIn(w - scale * w, 0f)
            offY = offY.coerceIn(h - scale * h, 0f)
        }

        fun zoomTo(target: Float, focus: Offset, w: Float, h: Float) {
            val from = scale
            val startX = offX
            val startY = offY
            val endScale = target.coerceIn(1f, MAX_ZOOM)
            val endX = (focus.x - (focus.x - startX) * (endScale / from)).coerceIn(w - endScale * w, 0f)
            val endY = (focus.y - (focus.y - startY) * (endScale / from)).coerceIn(h - endScale * h, 0f)
            scope.launch {
                animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                    scale = from + (endScale - from) * t
                    offX = startX + (endX - startX) * t
                    offY = startY + (endY - startY) * t
                }
            }
        }

        val taps = remember { TapMemory() }

        // Where a finger is: which page, and where on it as fractions of the page.
        // [page] given: measure against that page even if the finger is off it.
        fun locate(pos: Offset, w: Float, page: Int? = null): PageHit? {
            val lx = (pos.x - offX) / scale
            val ly = (pos.y - offY) / scale
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { item ->
                val top = item.offset - info.viewportStartOffset
                if (page != null) item.index == page else ly >= top && ly <= top + item.size
            } ?: return null
            val top = item.offset - info.viewportStartOffset
            val left = (w - baseWidthPx) / 2f
            return PageHit(item.index, (lx - left) / baseWidthPx, (ly - top) / item.size.coerceAtLeast(1))
        }

        // Where the two selection handles are on screen
        fun handles(sel: Selection, w: Float): Pair<Offset, Offset>? {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == sel.page } ?: return null
            val boxes = sel.boxes
            if (boxes.isEmpty()) return null
            val top = item.offset - info.viewportStartOffset
            val left = (w - baseWidthPx) / 2f
            fun screen(nx: Float, ny: Float) =
                Offset(offX + scale * (left + nx * baseWidthPx), offY + scale * (top + ny * item.size))
            return screen(boxes.first().left, boxes.first().bottom) to screen(boxes.last().right, boxes.last().bottom)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(baseWidthPx) {
                    val slop = viewConfiguration.touchSlop
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    val grab = 36.dp.toPx()
                    awaitEachGesture {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                        // A picked text box or note: one finger drags it, two fingers resize the text
                        val mv = currentMovable
                        val onIt = mv?.let { m -> locate(down.position, w, m.page)?.takeIf { m.contains(it.x, it.y, 0.02f) } }
                        if (mv != null && onIt != null) {
                            down.consume()
                            val info = listState.layoutInfo
                            val itemH = info.visibleItemsInfo.firstOrNull { it.index == mv.page }?.size?.toFloat() ?: h
                            val aspect = doc.pages[mv.page].aspect
                            var now = mv
                            var dx = 0f
                            var dy = 0f
                            var zoom = 1f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) break
                                val pan = event.calculatePan()
                                dx += pan.x / scale / baseWidthPx
                                dy += pan.y / scale / itemH
                                if (event.changes.count { it.pressed } >= 2) zoom *= event.calculateZoom()
                                now = TextBox.moved(mv, dx, dy, zoom, aspect)
                                showPreview(now)
                                event.changes.forEach { it.consume() }
                            }
                            showPreview(null)
                            if (dx != 0f || dy != 0f || zoom != 1f) moved(mv, now)
                            return@awaitEachGesture
                        }

                        // Pen: one finger draws; a second finger cancels the stroke and scrolls / zooms
                        val penNow = currentPen
                        val start = if (penNow != null) locate(down.position, w) else null
                        if (penNow != null && start != null) {
                            down.consume()
                            val pts = ArrayList<Float>()
                            pts += start.x.coerceIn(0f, 1f)
                            pts += start.y.coerceIn(0f, 1f)
                            live = LiveStroke(start.page, pts.toFloatArray(), penNow)
                            var twoFingers = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    twoFingers = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                locate(change.position, w, start.page)?.let { h ->
                                    val x = h.x.coerceIn(0f, 1f)
                                    val y = h.y.coerceIn(0f, 1f)
                                    val lx = pts[pts.size - 2]
                                    val ly = pts[pts.size - 1]
                                    // Skip points too close to the last to matter
                                    if (kotlin.math.hypot(x - lx, y - ly) > 0.0012f / scale) {
                                        pts += x
                                        pts += y
                                        live = LiveStroke(start.page, pts.toFloatArray(), penNow)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                            live = null
                            if (!twoFingers) {
                                stroke(start.page, pts.toFloatArray())
                                return@awaitEachGesture
                            }
                        }

                        // Grabbing a selection handle: drag it, nothing else
                        val sel = currentSelection
                        val ends = sel?.let { handles(it, w) }
                        val grabbed = when {
                            ends == null -> null
                            (down.position - ends.second).getDistance() <= grab -> false
                            (down.position - ends.first).getDistance() <= grab -> true
                            else -> null
                        }
                        if (sel != null && grabbed != null) {
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                locate(change.position, w, sel.page)?.let { dragHandle(grabbed, it) }
                                event.changes.forEach { it.consume() }
                            }
                            return@awaitEachGesture
                        }

                        if (zoomed) scope.launch { listState.stopScroll() }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var multi = false
                        var moved = false
                        var released = false
                        var travel = Offset.Zero
                        var upAt = down.uptimeMillis

                        // One step of pan / zoom. False once every finger is up.
                        fun step(event: androidx.compose.ui.input.pointer.PointerEvent): Boolean {
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                upAt = event.changes.firstOrNull()?.uptimeMillis ?: upAt
                                return false
                            }
                            if (pressed.size >= 2) multi = true
                            val pan = event.calculatePan()
                            travel += pan
                            if (multi || travel.getDistance() > slop) moved = true

                            if (multi || zoomed) {
                                if (multi) {
                                    val zoom = event.calculateZoom()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                    val k = newScale / scale
                                    offX = centroid.x - (centroid.x - offX) * k
                                    offY = centroid.y - (centroid.y - offY) * k
                                    scale = newScale
                                }
                                if (moved) {
                                    offX += pan.x
                                    // Move within the zoomed screen first, then scroll the pages
                                    val before = offY
                                    offY = (offY + pan.y).coerceIn(h - scale * h, 0f)
                                    val rest = pan.y - (offY - before)
                                    if (rest != 0f) listState.dispatchRawDelta(-rest / scale)
                                }
                                clamp(w, h)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            if (!multi) pressed.first().let { tracker.addPosition(it.uptimeMillis, it.position) }
                            return true
                        }

                        // Wait to see if this becomes a long-press
                        val timedOut = withTimeoutOrNull(longPressMs) {
                            while (true) {
                                if (!step(awaitPointerEvent(PointerEventPass.Initial))) {
                                    released = true
                                    break
                                }
                                if (moved || multi) break
                            }
                            false
                        } ?: true

                        if (timedOut && !moved && !multi && !released) {
                            // Long-press: select the word, then keep dragging to select more
                            val hit = locate(down.position, w)
                            if (hit != null) longPress(hit)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (hit != null) locate(change.position, w, hit.page)?.let { dragHandle(false, it) }
                                event.changes.forEach { it.consume() }
                            }
                            return@awaitEachGesture
                        }

                        if (!released) {
                            while (step(awaitPointerEvent(PointerEventPass.Initial))) Unit
                        }

                        when {
                            !moved && !multi && upAt - down.uptimeMillis < 400 -> {
                                if (down.uptimeMillis - taps.lastAt < 300) {
                                    // Double tap: zoom in at the finger, or back out
                                    taps.job?.cancel()
                                    taps.lastAt = 0L
                                    if (zoomed) zoomTo(1f, down.position, w, h)
                                    else zoomTo(DOUBLE_TAP_ZOOM, down.position, w, h)
                                } else {
                                    taps.lastAt = upAt
                                    val at = locate(down.position, w)
                                    taps.job = scope.launch {
                                        delay(260)
                                        tap(at)
                                    }
                                }
                            }
                            zoomed && !multi && moved -> {
                                val vy = tracker.calculateVelocity().y
                                scope.launch {
                                    listState.scroll { with(fling) { performFling(-vy / scale) } }
                                }
                            }
                        }
                        if (scale < 1.02f) {
                            scale = 1f
                            offX = 0f
                            offY = 0f
                        }
                    }
                },
        ) {
            val fills = Edits.fills(edits)
            LazyColumn(
                state = listState,
                userScrollEnabled = !zoomed,
                contentPadding = PaddingValues(top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offX
                        translationY = offY
                    },
            ) {
                items(doc.pageCount, key = { it }) { index ->
                    PageView(
                        doc, index, pageWidth, baseWidthPx, renderWidth,
                        selected = selection?.takeIf { it.page == index }?.boxes,
                        edits = edits,
                        fills = fills,
                        focused = focused,
                        preview = preview?.takeIf { it.page == index },
                        live = live?.takeIf { it.page == index },
                        scale = { scale },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageView(
    doc: PdfDoc,
    index: Int,
    width: Dp,
    baseWidthPx: Int,
    renderWidthPx: Int,
    selected: List<NBox>?,
    edits: List<Edit>,
    fills: Map<String, String>,
    focused: Long?,
    preview: Mark?,
    live: LiveStroke?,
    scale: () -> Float,
) {
    // Marks already saved in the file: Android's renderer doesn't draw them, so we do
    val saved by produceState(emptyList<Mark>(), index, doc) { value = doc.text.marks(index) }
    // Fillable form boxes, also left out by Android's renderer
    val fields by produceState(emptyList<FormField>(), index, doc) { value = doc.text.fields(index) }
    val aspect = doc.pages[index].aspect
    val bitmap by produceState(
        initialValue = doc.cached(index, renderWidthPx) ?: doc.cached(index, baseWidthPx),
        index, renderWidthPx,
    ) {
        doc.render(index, renderWidthPx)?.let { value = it }
    }
    Box(
        Modifier
            .width(width)
            .aspectRatio(1f / aspect)
            .background(Numio.Paper),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            val image = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = Numio.Line)
        }
        if (fields.isNotEmpty()) FormOverlay(fields, fills)
        val applied = Edits.apply(saved, edits, index)
        val marks = if (preview == null) applied else applied.map { if (it.id == preview.id) preview else it }
        if (marks.isNotEmpty()) MarksOverlay(marks, focused, scale)
        if (live != null) LiveInk(live.points, live.pen.width, live.pen.color)
        if (!selected.isNullOrEmpty()) SelectionOverlay(selected, scale)
    }
}

@Composable
private fun TopBar(
    name: String,
    current: Int,
    count: Int,
    starred: Boolean,
    unsaved: Int,
    onBack: () -> Unit,
    onStar: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Numio.Bg.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(PIcons.Back, contentDescription = "Back", tint = Numio.Text)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Page ${current + 1} of $count", style = MaterialTheme.typography.bodySmall)
        }
        if (unsaved > 0) {
            IconButton(onClick = onUndo) {
                Icon(PIcons.Undo, contentDescription = "Undo last mark", tint = Numio.Text)
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Numio.Amber)
                    .clickable(onClickLabel = "Save marks into the PDF", role = Role.Button, onClick = onSave)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge, color = Numio.OnAmber)
            }
        } else {
            IconButton(onClick = onStar) {
                Icon(
                    if (starred) PIcons.StarFilled else PIcons.Star,
                    contentDescription = if (starred) "Remove star" else "Star",
                    tint = if (starred) Numio.Amber else Numio.Text,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    doc: PdfDoc,
    current: Int,
    onJump: (Int) -> Unit,
    onShare: () -> Unit,
    onPen: () -> Unit,
    onText: () -> Unit,
    onNote: () -> Unit,
    onSign: () -> Unit,
) {
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LaunchedEffect(current) {
        val visible = strip.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == current } || visible.firstOrNull()?.index == current || visible.lastOrNull()?.index == current) {
            strip.animateScrollToItem((current - 3).coerceAtLeast(0))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        LazyRow(
            state = strip,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(doc.pageCount, key = { it }) { index ->
                StripPage(doc, index, selected = index == current, onClick = { onJump(index) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(PIcons.Pen, "Pen", onPen)
            BarAction(PIcons.TextBox, "Text", onText)
            BarAction(PIcons.Sign, "Sign", onSign)
            BarAction(PIcons.Note, "Note", onNote)
            BarAction(PIcons.Share, "Share", onShare)
        }
    }
}

/** Pen colour (0xRRGGBB) and width (fraction of the page width). */
data class Pen(val color: Int, val width: Float)

/** The stroke being drawn: page and points so far. */
class LiveStroke(val page: Int, val points: FloatArray, val pen: Pen)

private fun overlaps(a: NBox, b: NBox): Boolean =
    a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

/** A text box or note about to be written ([existing] null) or changed. */
data class Draft(val kind: MarkKind, val page: Int, val x: Float, val y: Float, val existing: Mark?)

/** Single tap waits briefly in case it becomes a double tap. */
private class TapMemory {
    var job: Job? = null
    var lastAt = 0L
}

private val STRIP_HEIGHT = 48.dp
private val STRIP_MAX_WIDTH = 72.dp

@Composable
private fun StripPage(doc: PdfDoc, index: Int, selected: Boolean, onClick: () -> Unit) {
    val aspect = doc.pages[index].aspect
    // Tall pages are 48 dp high; wide (landscape) pages are capped in width and fitted
    val width = minOf(STRIP_HEIGHT / aspect, STRIP_MAX_WIDTH)
    val height = minOf(STRIP_HEIGHT, width * aspect)
    // Drawn at twice the shown size so the little pages stay crisp
    val px = with(LocalDensity.current) { width.roundToPx() * 2 }.coerceIn(32, 400)
    val bitmap by produceState<Bitmap?>(doc.cached(index, px), index, px) {
        doc.render(index, px)?.let { value = it }
    }
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .height(STRIP_HEIGHT)
            .clickable(onClickLabel = "Go to page ${index + 1}", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width, height)
                .then(if (selected) Modifier.border(2.dp, Numio.Amber, shape) else Modifier)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(shape)
                .background(Numio.Paper),
        ) {
            bitmap?.let { b ->
                val image = remember(b) { b.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GoToPageDialog(count: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val number = text.toIntOrNull()?.takeIf { it in 1..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text("Go to page", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                label = { Text("Page 1 to $count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Numio.Amber,
                    focusedLabelColor = Numio.Amber,
                    cursorColor = Numio.Amber,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { number?.let { onGo(it - 1) } }, enabled = number != null) {
                Text("Go", color = if (number != null) Numio.Amber else Numio.TextDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.TextDim) }
        },
    )
}
