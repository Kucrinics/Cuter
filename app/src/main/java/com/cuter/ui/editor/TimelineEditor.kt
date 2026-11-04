package com.cuter.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuter.data.Clip
import com.cuter.data.MediaAsset
import com.cuter.data.MediaType
import com.cuter.data.Project
import com.cuter.data.Track
import com.cuter.data.TrackType
import com.cuter.media.FormatUtil
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Amber
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.Panel
import com.cuter.ui.theme.PanelHigh
import com.cuter.ui.theme.Pink
import com.cuter.ui.theme.Stroke
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private val RulerHeight = 24.dp

private fun trackHeight(type: TrackType): Dp = when (type) {
    TrackType.VIDEO -> 52.dp
    TrackType.OVERLAY -> 38.dp
    TrackType.AUDIO -> 32.dp
}

@Composable
fun TimelineEditor(
    modifier: Modifier = Modifier,
    project: Project,
    selectedClipId: String?,
    playheadMs: Long,
    isPlaying: Boolean,
    onSelect: (String?) -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onMove: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onPushUndo: () -> Unit,
    onToggleTrackMute: (String) -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var dpPerSec by remember { mutableFloatStateOf(74f) }
    val scroll = rememberScrollState()
    val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
    val pxPerMsState by rememberUpdatedState(pxPerMs)

    val totalMs = max(project.durationMs + 5000, 12000)
    val contentWidth = with(density) { (totalMs / 1000f * dpPerSec).dp }

    LaunchedEffect(playheadMs, isPlaying, pxPerMs) {
        if (!isPlaying) return@LaunchedEffect
        val x = (playheadMs * pxPerMs).toInt()
        val viewport = scroll.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        if (x < scroll.value || x > scroll.value + viewport * 0.85f) {
            scroll.scrollTo((x - viewport * 0.25f).toInt().coerceAtLeast(0))
        }
    }

    Column(modifier.background(Panel)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ТАЙМЛАЙН",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(FormatUtil.formatMs(project.durationMs), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            ZoomButton(Icons.Rounded.Remove) { dpPerSec = (dpPerSec - 18f).coerceAtLeast(32f) }
            ZoomButton(Icons.Rounded.Add) { dpPerSec = (dpPerSec + 18f).coerceAtMost(220f) }
        }

        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(56.dp).fillMaxHeight()) {
                Spacer(Modifier.height(RulerHeight))
                project.tracks.forEach { track ->
                    TrackLabel(
                        track = track,
                        height = trackHeight(track.type),
                        onToggleMute = { onToggleTrackMute(track.id) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.horizontalScroll(scroll)) {
                    Box(Modifier.width(contentWidth).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            TimelineRuler(
                                totalMs = totalMs,
                                pxPerMs = pxPerMs,
                                playheadMs = playheadMs,
                                onScrubStart = onScrubStart,
                                onSeek = onSeek,
                            )
                            project.tracks.forEach { track ->
                                TrackLane(
                                    project = project,
                                    track = track,
                                    selectedClipId = selectedClipId,
                                    pxPerMs = pxPerMs,
                                    onSelect = onSelect,
                                    onMove = onMove,
                                    onTrimStart = onTrimStart,
                                    onTrimEnd = onTrimEnd,
                                    onPushUndo = onPushUndo,
                                    thumbPathFor = { asset ->
                                        asset.thumbName?.let { name ->
                                            File(context.filesDir, "projects/${project.id}/thumbs/$name").absolutePath
                                        }
                                    },
                                )
                            }
                        }
                        val playheadX = with(density) { (playheadMs * pxPerMs).toDp() }
                        Box(
                            Modifier
                                .offset(x = playheadX - 1.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Accent.copy(alpha = 0.9f)),
                        )
                        Box(
                            Modifier
                                .offset(x = playheadX - 6.dp, y = 1.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(PanelHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TimelineRuler(
    totalMs: Long,
    pxPerMs: Float,
    playheadMs: Long,
    onScrubStart: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val seekState by rememberUpdatedState(onSeek)
    val scrubStartState by rememberUpdatedState(onScrubStart)
    val pxState by rememberUpdatedState(pxPerMs)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(RulerHeight)
            .background(Ink.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scrubStartState()
                    down.consume()
                    seekState((down.position.x / pxState).toLong())
                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        seekState((change.position.x / pxState).toLong())
                        change.consume()
                    }
                }
            },
    ) {
        val stepMs = if (pxPerMs > 0.09f) 500L else 1000L
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(150, 200, 205, 220)
            textSize = 9.sp.toPx()
            isAntiAlias = true
        }
        var t = 0L
        var index = 0
        while (t <= totalMs) {
            val x = t * pxPerMs
            val major = t % (stepMs * 4) == 0L
            drawLine(
                color = if (major) TextSecondary.copy(alpha = 0.55f) else TextSecondary.copy(alpha = 0.22f),
                start = Offset(x, size.height - if (major) 9.dp.toPx() else 5.dp.toPx()),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            if (major) {
                drawContext.canvas.nativeCanvas.drawText(
                    FormatUtil.formatMs(t),
                    x + 3.dp.toPx(),
                    10.dp.toPx(),
                    textPaint,
                )
            }
            index++
            t += stepMs
            if (index > 2000) break
        }
        val px = playheadMs * pxPerMs
        drawLine(
            color = Accent,
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun TrackLabel(track: Track, height: Dp, onToggleMute: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Ink.copy(alpha = 0.45f))
            .padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                fontSize = 9.sp,
                color = if (track.muted) TextSecondary.copy(alpha = 0.55f) else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 10.sp,
            )
        }
        if (track.type == TrackType.AUDIO || track.type == TrackType.VIDEO) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .pointerInput(track.id) {
                        awaitEachGesture {
                            awaitFirstDown().also { it.consume() }
                            onToggleMute()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (track.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = if (track.muted) Pink else TextSecondary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackLane(
    project: Project,
    track: Track,
    selectedClipId: String?,
    pxPerMs: Float,
    onSelect: (String?) -> Unit,
    onMove: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onPushUndo: () -> Unit,
    thumbPathFor: (MediaAsset) -> String?,
) {
    val height = trackHeight(track.type)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                if (track.type == TrackType.VIDEO) Ink.copy(alpha = 0.3f) else Panel.copy(alpha = 0.55f)
            )
            .pointerInput(track.id) {
                awaitEachGesture {
                    awaitFirstDown()
                    onSelect(null)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 1000L * pxPerMs
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Stroke.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
            }
        }
        track.clips.sortedBy { it.startMs }.forEach { clip ->
            ClipBar(
                clip = clip,
                track = track,
                selected = clip.id == selectedClipId,
                pxPerMs = pxPerMs,
                asset = project.asset(clip.assetId),
                thumbPath = project.asset(clip.assetId)?.let(thumbPathFor),
                onSelect = onSelect,
                onMove = onMove,
                onTrimStart = onTrimStart,
                onTrimEnd = onTrimEnd,
                onPushUndo = onPushUndo,
            )
        }
    }
}

@Composable
private fun BoxScope.ClipBar(
    clip: Clip,
    track: Track,
    selected: Boolean,
    pxPerMs: Float,
    asset: MediaAsset?,
    thumbPath: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Long) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit,
    onPushUndo: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val startDp = with(density) { (clip.startMs * pxPerMs).toDp() }
    val widthDp = with(density) { (clip.durationMs * pxPerMs).toDp() }.coerceAtLeast(6.dp)
    val startState by rememberUpdatedState(clip.startMs)
    val endState by rememberUpdatedState(clip.endMs)
    val pxState by rememberUpdatedState(pxPerMs)
    val selectState by rememberUpdatedState(onSelect)
    val moveState by rememberUpdatedState(onMove)
    val trimStartState by rememberUpdatedState(onTrimStart)
    val trimEndState by rememberUpdatedState(onTrimEnd)
    val undoState by rememberUpdatedState(onPushUndo)

    val bitmap = remember(thumbPath) {
        thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    val colorPair = when {
        clip.text != null -> Pink to Color(0xFF7A1F46)
        clip.sticker != null -> Color(0xFF9B7BFF) to Color(0xFF3A2A72)
        clip.type == MediaType.AUDIO -> Amber to Color(0xFF5A4318)
        else -> Accent to Color(0xFF0E4B40)
    }

    Box(
        Modifier
            .offset(x = startDp)
            .width(widthDp)
            .fillMaxHeight()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(colorPair.first.copy(alpha = 0.42f), colorPair.second.copy(alpha = 0.55f))
                )
            )
            .border(
                width = if (selected) 1.5.dp else 0.8.dp,
                color = if (selected) colorPair.first else colorPair.first.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp),
            )
            .pointerInput(clip.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selectState(clip.id)
                    down.consume()
                    var lastX = down.position.x
                    var dragged = false
                    var accumulated = 0f
                    val base = startState
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - lastX
                        if (!dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            dragged = true
                            accumulated = 0f
                            undoState()
                        }
                        if (dragged) {
                            accumulated += dx
                            change.consume()
                            moveState(clip.id, base + (accumulated / pxState).toLong())
                        }
                        lastX = change.position.x
                    }
                }
            },
    ) {
        if (bitmap != null && clip.type != MediaType.AUDIO) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f,
            )
        }
        if (clip.type == MediaType.AUDIO && !clip.isOverlay) {
            AudioWaveform(modifier = Modifier.fillMaxSize(), color = colorPair.first)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 5.dp, end = 4.dp, bottom = 3.dp),
        ) {
            val label = when {
                clip.text != null -> clip.text.text.ifBlank { "Текст" }
                clip.sticker != null -> if (clip.sticker.emoji.isBlank()) "Картинка" else clip.sticker.emoji
                else -> asset?.name ?: "Клип"
            }
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (widthDp > 54.dp) {
                Text(
                    text = FormatUtil.formatMs(clip.durationMs),
                    color = TextPrimary.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    maxLines = 1,
                )
            }
        }
        if (clip.muted) {
            Icon(
                Icons.Rounded.VolumeOff,
                contentDescription = null,
                tint = TextPrimary.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(10.dp),
            )
        }
        if (selected && widthDp > 26.dp) {
            TrimHandle(
                alignment = Alignment.CenterStart,
                color = colorPair.first,
                onDragStart = undoState,
                onDrag = { accumulated ->
                    trimStartState(clip.id, startState + (accumulated / pxState).toLong())
                },
            )
            TrimHandle(
                alignment = Alignment.CenterEnd,
                color = colorPair.first,
                onDragStart = undoState,
                onDrag = { accumulated ->
                    trimEndState(clip.id, endState + (accumulated / pxState).toLong())
                },
            )
        }
    }
}

@Composable
private fun BoxScope.TrimHandle(
    alignment: Alignment,
    color: Color,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    var accumulated by remember { mutableFloatStateOf(0f) }
    val startState by rememberUpdatedState(onDragStart)
    val dragState by rememberUpdatedState(onDrag)
    Box(
        Modifier
            .align(alignment)
            .width(11.dp)
            .fillMaxHeight()
            .background(color.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        startState()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        accumulated += amount.x
                        dragState(accumulated)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(1.5.dp).height(12.dp).background(Ink.copy(alpha = 0.7f)))
    }
}

@Composable
private fun AudioWaveform(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val bars = (size.width / 3.dp.toPx()).toInt().coerceIn(8, 400)
        val center = size.height / 2f
        for (i in 0 until bars) {
            val x = i * 3.dp.toPx() + 1.dp.toPx()
            val amplitude = (sin(i * 0.7f) * 0.5f + 0.5f) * 0.65f + 0.12f
            val h = size.height * amplitude
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(x, center - h / 2f),
                end = Offset(x, center + h / 2f),
                strokeWidth = 1.2.dp.toPx(),
            )
        }
    }
}


