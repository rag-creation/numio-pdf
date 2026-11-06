package com.rr.numio.pdf.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rr.numio.pdf.data.PageLook
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Reading like a book: every page full screen, swipe right to left for the
 * next one, the phone's bars hidden and the screen kept on. Tap for a small
 * bar (back, title, lock); the reading lock ignores every touch except page
 * swipes until its button is held down.
 */
@Composable
fun BookReader(
    doc: PdfDoc,
    name: String,
    start: Int,
    look: PageLook,
    edits: List<Edit>,
    onPage: (Int) -> Unit,
    onExit: (page: Int) -> Unit,
) {
    val count = doc.pageCount
    val pager = rememberPagerState(initialPage = start.coerceIn(0, count - 1)) { count }
    var bars by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var lockHint by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    val fills = Edits.fills(edits)
    val pageChanged by rememberUpdatedState(onPage)

    // Full screen and screen kept on, put back as it was when leaving
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collectLatest {
            delay(300)
            pageChanged(it)
        }
    }

    // Back leaves book mode, except while locked (that's the point of the lock)
    BackHandler {
        if (locked) lockHint++ else onExit(pager.currentPage)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0A))) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            // A little depth as pages turn: the outgoing page sinks back slightly
            val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
            BookPage(
                doc = doc,
                index = index,
                look = look,
                edits = edits,
                fills = fills,
                locked = locked,
                onZoomed = { if (index == pager.currentPage) zoomed = it },
                onTap = { if (locked) lockHint++ else bars = !bars },
                modifier = Modifier.graphicsLayer {
                    val t = abs(offset).coerceIn(0f, 1f)
                    val s = 1f - 0.06f * t
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.25f * t
                    transformOrigin = TransformOrigin(if (offset > 0) 1f else 0f, 0.5f)
                },
            )
        }

        // Small bar, only when asked for
        AnimatedVisibility(
            visible = bars && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0F0F0E))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onExit(pager.currentPage) }) {
                    Icon(PIcons.Back, contentDescription = "Leave book mode", tint = Numio.Text)
                }
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    locked = true
                    bars = false
                    lockHint++
                }) {
                    Icon(PIcons.Lock, contentDescription = "Lock: only page swipes work", tint = Numio.Amber)
                }
            }
        }
        AnimatedVisibility(
            visible = bars && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC2A2925))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("${pager.currentPage + 1} / $count", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (locked) {
            UnlockButton(
                hint = lockHint,
                onUnlock = {
                    locked = false
                    bars = true
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
            )
        }
    }
}

/** One page, fitted whole to the screen; pinch or double-tap to zoom when not locked. */
@Composable
private fun BookPage(
    doc: PdfDoc,
    index: Int,
    look: PageLook,
    edits: List<Edit>,
    fills: Map<String, String>,
    locked: Boolean,
    onZoomed: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val tap by rememberUpdatedState(onTap)
    val zoomedNow by rememberUpdatedState(onZoomed)
    LaunchedEffect(locked) {
        if (locked) {
            scale = 1f
            off = Offset.Zero
            zoomedNow(false)
        }
    }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val aspect = doc.pages[index].aspect
        val wPx = with(density) { minOf(maxWidth.toPx(), maxHeight.toPx() / aspect) }.toInt().coerceAtLeast(1)
        val w = with(density) { wPx.toDp() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) {
                        detectTapGestures(onTap = { tap() })
                        return@pointerInput
                    }
                    detectTapGestures(
                        onTap = { tap() },
                        onDoubleTap = { p ->
                            if (scale > 1.01f) {
                                scale = 1f
                                off = Offset.Zero
                            } else {
                                scale = 2.5f
                                off = Offset((size.width / 2f - p.x) * 1.5f, (size.height / 2f - p.y) * 1.5f)
                            }
                            zoomedNow(scale > 1.01f)
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    // Two fingers zoom; once zoomed, one finger moves around. A swipe
                    // at normal size is left to the pages, to turn them.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            if (pressed >= 2 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val c = event.calculateCentroid(useCurrent = true) - Offset(size.width / 2f, size.height / 2f)
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                off = (off - c) * (newScale / scale) + c + event.calculatePan()
                                scale = newScale
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                off = Offset(off.x.coerceIn(-maxX, maxX), off.y.coerceIn(-maxY, maxY))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        if (scale < 1.02f) {
                            scale = 1f
                            off = Offset.Zero
                        }
                        zoomedNow(scale > 1.01f)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = off.x
                    translationY = off.y
                },
            contentAlignment = Alignment.Center,
        ) {
            PageView(
                doc, index, w, wPx, if (scale > 1.6f) wPx * 2 else wPx,
                look = look,
                selected = null,
                edits = edits,
                fills = fills,
                focused = null,
                preview = null,
                live = null,
                scale = { scale },
            )
        }
    }
}

/** While locked: a small lock that unlocks only when held, with a ring filling up. */
@Composable
private fun UnlockButton(hint: Int, onUnlock: () -> Unit, modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    // Shown for a moment after locking or after a touch, then fades so the page is clear
    LaunchedEffect(hint) {
        visible = true
        delay(2500)
        if (progress.value == 0f) visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC1B1A18))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            visible = true
                            val fill = scope.launch {
                                progress.animateTo(1f, tween(1500, easing = LinearEasing))
                                onUnlock()
                            }
                            do {
                                val e = awaitPointerEvent()
                            } while (e.changes.any { it.pressed })
                            if (progress.value < 1f) {
                                fill.cancel()
                                scope.launch { progress.snapTo(0f) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(56.dp)) {
                    drawArc(
                        Numio.Amber, -90f, 360f * progress.value, false,
                        style = Stroke(3.dp.toPx()), size = Size(size.width, size.height),
                    )
                }
                Icon(PIcons.Lock, contentDescription = "Hold to unlock", tint = Numio.Amber)
            }
            Text(
                "Hold to unlock",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC1B1A18)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
