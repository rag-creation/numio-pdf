package com.rr.numio.pdf.ui.reader

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
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
    onMark: (Mark) -> Unit,
    onRemoveMark: (Mark) -> Unit,
    onRecolorMark: (Mark, Int) -> Unit,
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
    // The mark tapped for changing (page and id); looked up fresh so colour changes and Undo show at once
    var editingRef by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val editing: Mark? = editingRef?.let { (page, id) ->
        Edits.apply(doc.text.cachedMarks(page), edits, page).firstOrNull { it.id == id }
    }

    fun tapped(hit: PageHit?) {
        when {
            selection != null -> selection = null
            editingRef != null -> editingRef = null
            else -> {
                val mark = hit?.let { h ->
                    Edits.apply(doc.text.cachedMarks(h.page), edits, h.page).lastOrNull { it.contains(h.x, h.y) }
                }
                if (mark != null) editingRef = mark.page to mark.id else chrome = !chrome
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
            onTap = ::tapped,
            onLongPress = ::longPress,
            onDragHandle = ::dragHandle,
        )

        AnimatedVisibility(
            visible = chrome,
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
            visible = editing != null && selection == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Keeps showing the last mark while sliding away
            val shown = remember { arrayOfNulls<Mark>(1) }
            editing?.let { shown[0] = it }
            shown[0]?.let { m ->
                MarkBar(
                    mark = m,
                    onColor = { c -> editing?.let { onRecolorMark(it, c) } },
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
            visible = chrome && selection == null && editing == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(
                doc = doc,
                current = current,
                onJump = ::jump,
                onShare = onShare,
            )
        }
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
                        focused = focused,
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
    focused: Long?,
    scale: () -> Float,
) {
    // Marks already saved in the file: Android's renderer doesn't draw them, so we do
    val saved by produceState(emptyList<Mark>(), index, doc) { value = doc.text.marks(index) }
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
        val marks = Edits.apply(saved, edits, index)
        if (marks.isNotEmpty()) MarksOverlay(marks, focused, scale)
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
private fun BottomBar(doc: PdfDoc, current: Int, onJump: (Int) -> Unit, onShare: () -> Unit) {
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LaunchedEffect(current) {
        val visible = strip.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == current } || visible.firstOrNull()?.index == current || visible.lastOrNull()?.index == current) {
            strip.animateScrollToItem((current - 3).coerceAtLeast(0))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = strip,
            modifier = Modifier.weight(1f).height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(doc.pageCount, key = { it }) { index ->
                StripPage(doc, index, selected = index == current, onClick = { onJump(index) })
            }
        }
        IconButton(onClick = onShare) {
            Icon(PIcons.Share, contentDescription = "Share", tint = Numio.Text)
        }
    }
}

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
