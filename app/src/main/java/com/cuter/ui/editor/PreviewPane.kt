package com.cuter.ui.editor

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import com.cuter.CuterApp
import com.cuter.data.Clip
import com.cuter.data.Project
import com.cuter.media.CompositionFactory
import com.cuter.media.FormatUtil
import com.cuter.media.PreviewController
import com.cuter.ui.theme.Accent
import com.cuter.ui.theme.Ink
import com.cuter.ui.theme.PanelHigh
import com.cuter.ui.theme.Pink
import com.cuter.ui.theme.Stroke
import com.cuter.ui.theme.TextPrimary
import com.cuter.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@UnstableApi
@Composable
fun PreviewPane(
    modifier: Modifier = Modifier,
    project: Project,
    controller: PreviewController,
    selectedClip: Clip?,
    playheadMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onTick: (Long) -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit,
    onMoveOverlay: (Float, Float) -> Unit,
    onPushUndo: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CuterApp
    val factory = remember(app) { CompositionFactory(app, app.repository) }
    var composition by remember(project.id) { mutableStateOf<Composition?>(null) }
    var textureReady by remember { mutableIntStateOf(0) }

    val tickState by rememberUpdatedState(onTick)
    val endedState by rememberUpdatedState(onEnded)
    val errorState by rememberUpdatedState(onError)

    LaunchedEffect(project) {
        delay(240)
        val built = withContext(Dispatchers.Default) { factory.build(project, preview = true) }
        composition = built
    }

    DisposableEffect(controller) {
        controller.onEnded = { endedState() }
        controller.onError = { message -> errorState(message) }
        onDispose {
            controller.onEnded = null
            controller.onError = null
        }
    }

    LaunchedEffect(composition, textureReady) {
        val built = composition ?: return@LaunchedEffect
        if (textureReady == 0) return@LaunchedEffect
        controller.bind(built, playheadMs, isPlaying)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            tickState(controller.position())
            delay(40)
        }
    }

    val hasClips = project.tracks.any { it.clips.isNotEmpty() }
    val overlaySelected = selectedClip?.isOverlay == true

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = project.ratio.w.toFloat() / project.ratio.h.toFloat()
        val previewWidth = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        val previewHeight = previewWidth / ratio

        Box(
            Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, Stroke, RoundedCornerShape(10.dp)),
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        controller.attach(view)
                        textureReady += 1
                    }
                },
                update = { view -> controller.attach(view) },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (overlaySelected) {
                            Modifier.pointerInput(selectedClip.id) {
                                detectDragGestures(
                                    onDragStart = { onPushUndo() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val dx = if (size.width > 0) amount.x / size.width else 0f
                                        val dy = if (size.height > 0) amount.y / size.height else 0f
                                        onMoveOverlay(dx, dy)
                                    },
                                )
                            }
                        } else {
                            Modifier.clickable { onTogglePlay() }
                        }
                    ),
            )

            if (!hasClips) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Пустой проект", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Добавьте видео или фото", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Accent)
                            .clickable(onClick = onOpenMedia)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Ink, modifier = Modifier.size(16.dp))
                        Text("Медиа", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = FormatUtil.formatMs(playheadMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Pink.copy(alpha = 0.92f) else Accent.copy(alpha = 0.95f))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        tint = Ink,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = FormatUtil.formatMs(project.durationMs),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            Text(
                text = project.ratio.label,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(PanelHigh.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
