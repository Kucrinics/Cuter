package com.cuter.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.cuter.data.CanvasRatio
import com.cuter.data.Clip
import com.cuter.data.MediaType
import com.cuter.data.Project
import com.cuter.data.ProjectRepository
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@UnstableApi
object CanvasSize {
    fun of(ratio: CanvasRatio, preview: Boolean): Pair<Int, Int> {
        val shortSide = if (preview) 540 else 1080
        val w: Int
        val h: Int
        if (ratio.w >= ratio.h) {
            w = (shortSide.toFloat() * ratio.w / ratio.h).roundToInt()
            h = shortSide
        } else {
            w = shortSide
            h = (shortSide.toFloat() * ratio.h / ratio.w).roundToInt()
        }
        return even(w) to even(h)
    }

    private fun even(v: Int): Int = if (v % 2 == 0) v else v + 1
}

@UnstableApi
class CompositionFactory(
    context: Context,
    private val repository: ProjectRepository,
) {
    private val appContext = context.applicationContext

    fun build(project: Project, preview: Boolean): Composition? {
        val projectDir = repository.projectDir(project.id)
        val (canvasW, canvasH) = CanvasSize.of(project.ratio, preview)
        val renderScale = if (preview) 0.55f else 1f
        val black = BlackFrameFactory.get(appContext)

        val baseItems = mutableListOf<EditedMediaItem>()
        val clips = project.videoTrack?.clips?.sortedBy { it.startMs }.orEmpty()
        var cursor = 0L
        for (clip in clips) {
            if (clip.startMs > cursor + 40) {
                baseItems += imageItem(black, clip.startMs - cursor)
            }
            val item = visualItem(project, clip, projectDir)
            if (item != null) {
                baseItems += item
                cursor = clip.endMs
            }
        }
        val totalDuration = project.durationMs.coerceAtLeast(1000L)
        when {
            baseItems.isEmpty() -> baseItems += imageItem(black, totalDuration)
            cursor < totalDuration - 40 -> baseItems += imageItem(black, totalDuration - cursor)
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence(baseItems)

        for (track in project.tracks.filter { it.type == com.cuter.data.TrackType.AUDIO }) {
            if (track.muted) continue
            for (clip in track.clips.sortedBy { it.startMs }) {
                if (clip.muted || clip.volume <= 0.005f) continue
                val asset = project.asset(clip.assetId) ?: continue
                val file = repository.fileFor(project.id, asset)
                if (!file.exists()) continue
                val items = mutableListOf<EditedMediaItem>()
                if (clip.startMs > 40) {
                    items += silentItem(SilenceFactory.get(appContext, clip.startMs))
                }
                val sourceEnd = min(asset.durationMs, clip.trimStartMs + clip.durationMs)
                if (sourceEnd - clip.trimStartMs < 80) continue
                val media = MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMediaId(clip.id)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(sourceEnd)
                            .build()
                    )
                    .build()
                val gain = GainAudioProcessor(
                    gain = clip.volume.coerceIn(0f, 2f),
                    fadeInMs = clip.fadeInMs,
                    fadeOutMs = clip.fadeOutMs,
                    totalDurationMs = sourceEnd - clip.trimStartMs,
                )
                items += EditedMediaItem.Builder(media)
                    .setRemoveVideo(true)
                    .setEffects(Effects(listOf(gain), emptyList()))
                    .build()
                sequences += EditedMediaItemSequence(items)
            }
        }

        val overlayClips = project.tracks
            .filter { it.type == com.cuter.data.TrackType.OVERLAY }
            .flatMap { it.clips }
            .filter { it.isOverlay }
            .sortedBy { it.startMs }

        val overlays = overlayClips.map { clip ->
            ClipOverlay(clip, canvasW, canvasH, renderScale, projectDir)
        }

        val compositionVideoEffects = mutableListOf<Effect>()
        if (overlays.isNotEmpty()) {
            compositionVideoEffects += OverlayEffect(overlays)
        }
        compositionVideoEffects += OverlayEffect(
            listOf(TransitionOverlay(project, canvasW, canvasH))
        )

        return runCatching {
            Composition.Builder(sequences)
                .setVideoCompositorSettings(FitCanvasCompositor(canvasW, canvasH))
                .setEffects(Effects(emptyList(), compositionVideoEffects))
                .experimentalSetForceAudioTrack(true)
                .build()
        }.getOrNull()
    }

    private fun visualItem(project: Project, clip: Clip, projectDir: File): EditedMediaItem? {
        val asset = project.asset(clip.assetId) ?: return null
        val file = repository.fileFor(project.id, asset)
        if (!file.exists()) return null
        if (clip.durationMs < 50) return null

        if (asset.type == MediaType.IMAGE) {
            return imageItem(file, clip.durationMs)
        }

        val sourceSpan = (clip.durationMs * clip.speed).toLong()
        val sourceEnd = min(asset.durationMs, clip.trimStartMs + sourceSpan)
        if (sourceEnd - clip.trimStartMs < 80) return null
        val media = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(clip.id)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(sourceEnd)
                    .build()
            )
            .build()

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        if (abs(clip.speed - 1f) > 0.01f) {
            val speed = Effects.createExperimentalSpeedChangingEffect(ConstantSpeedProvider(clip.speed))
            audioProcessors += speed.first
            videoEffects += speed.second
        }
        videoEffects += VideoFilters.effectsFor(clip.filter)
        if (!clip.muted && clip.volume > 0.005f) {
            audioProcessors += GainAudioProcessor(
                gain = clip.volume,
                fadeInMs = clip.fadeInMs,
                fadeOutMs = clip.fadeOutMs,
                totalDurationMs = clip.durationMs,
            )
        }

        return EditedMediaItem.Builder(media)
            .setRemoveAudio(clip.muted || clip.volume <= 0.005f)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }

    private fun imageItem(file: File, durationMs: Long): EditedMediaItem {
        val safeDuration = durationMs.coerceAtLeast(60L)
        val media = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setImageDurationMs(safeDuration)
            .build()
        return EditedMediaItem.Builder(media)
            .setDurationUs(safeDuration * 1000)
            .setRemoveAudio(true)
            .build()
    }

    private fun silentItem(file: File): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
            .setRemoveVideo(true)
            .build()
}

@UnstableApi
private class FitCanvasCompositor(
    private val width: Int,
    private val height: Int,
) : VideoCompositorSettings {

    private var inputSizes: List<Size> = emptyList()

    override fun getOutputSize(inputSizes: MutableList<Size>): Size {
        this.inputSizes = inputSizes.toList()
        return Size(width, height)
    }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val size = inputSizes.getOrNull(inputId)
            ?: return OverlaySettings.Builder().build()
        if (size.width <= 0 || size.height <= 0) return OverlaySettings.Builder().build()
        val scale = min(width.toFloat() / size.width, height.toFloat() / size.height)
        return OverlaySettings.Builder()
            .setScale(scale, scale)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
    }
}
