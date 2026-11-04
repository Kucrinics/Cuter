package com.cuter.ui.editor

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.cuter.CuterApp
import com.cuter.data.CanvasRatio
import com.cuter.data.Clip
import com.cuter.data.FilterType
import com.cuter.data.MediaAsset
import com.cuter.data.MediaType
import com.cuter.data.Project
import com.cuter.data.StickerSpec
import com.cuter.data.TextSpec
import com.cuter.data.Track
import com.cuter.data.TrackType
import com.cuter.data.TransitionType
import com.cuter.data.newId
import com.cuter.media.CompositionFactory
import com.cuter.media.GalleryPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val done: Int, val total: Int) : ImportState
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val uri: Uri, val fileName: String, val cachedFile: File) : ExportState
    data class Failed(val message: String) : ExportState
}

@OptIn(UnstableApi::class)
class EditorViewModel(
    app: Application,
    private val projectId: String,
) : AndroidViewModel(app) {

    private val repository = (app as CuterApp).repository
    private val importer = (app as CuterApp).importer
    private val compositionFactory = CompositionFactory(app, repository)

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project

    val selectedClipId = MutableStateFlow<String?>(null)
    val playheadMs = MutableStateFlow(0L)
    val isPlaying = MutableStateFlow(false)
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var saveJob: Job? = null
    private var transformer: Transformer? = null
    private var lastContinuousPush = 0L

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.load(projectId) }
            if (loaded != null) _project.value = loaded
        }
    }

    val selectedClip: Clip?
        get() = _project.value?.clip(selectedClipId.value)

    fun currentProject(): Project? = _project.value

    fun compositionForExport(project: Project): Composition? =
        compositionFactory.build(project, preview = false)

    fun dismissMessage() {
        message.value = null
    }

    fun select(clipId: String?) {
        selectedClipId.value = clipId
    }

    fun seekTo(positionMs: Long) {
        val duration = _project.value?.durationMs ?: 0
        playheadMs.value = positionMs.coerceIn(0, max(0, duration))
    }

    fun tick(positionMs: Long) {
        playheadMs.value = positionMs.coerceAtLeast(0)
    }

    fun onPlaybackEnded() {
        isPlaying.value = false
        playheadMs.value = _project.value?.durationMs ?: playheadMs.value
    }

    fun pushUndo() {
        val current = _project.value ?: return
        undoStack.addLast(current)
        while (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        refreshHistory()
    }

    fun undo() {
        val current = _project.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _project.value = previous
        refreshHistory()
        scheduleSave(previous)
        if (previous.clip(selectedClipId.value) == null) selectedClipId.value = null
        playheadMs.value = playheadMs.value.coerceIn(0, previous.durationMs)
    }

    fun redo() {
        val current = _project.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _project.value = next
        refreshHistory()
        scheduleSave(next)
        if (next.clip(selectedClipId.value) == null) selectedClipId.value = null
        playheadMs.value = playheadMs.value.coerceIn(0, next.durationMs)
    }

    private fun refreshHistory() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    fun edit(recordHistory: Boolean = true, transform: (Project) -> Project) {
        val current = _project.value ?: return
        if (recordHistory) pushUndo()
        val next = transform(current)
        _project.value = next
        scheduleSave(next)
        if (next.clip(selectedClipId.value) == null) selectedClipId.value = null
        playheadMs.value = playheadMs.value.coerceIn(0, next.durationMs)
    }

    /** Coalesces rapid updates (sliders) into one undo step. */
    fun editContinuous(transform: (Project) -> Project) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastContinuousPush > 800) {
            pushUndo()
            lastContinuousPush = now
        }
        edit(recordHistory = false, transform)
    }

    private fun scheduleSave(project: Project) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            withContext(Dispatchers.IO) { repository.save(project) }
        }
    }

    fun renameProject(name: String) {
        val trimmed = name.trim().take(60)
        if (trimmed.isBlank()) return
        edit { it.copy(name = trimmed) }
    }

    fun setRatio(ratio: CanvasRatio) {
        if (_project.value?.ratio == ratio) return
        edit { it.copy(ratio = ratio) }
    }

    // ---------------------------------------------------------------- media

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importState.value = ImportState.Running(0, uris.size)
            var added = 0
            uris.forEachIndexed { index, uri ->
                val result = importer.import(projectId, uri)
                result.onSuccess { asset ->
                    if (appendAsset(asset)) added++
                }.onFailure { error ->
                    message.value = error.message ?: "Не удалось импортировать файл"
                }
                importState.value = ImportState.Running(index + 1, uris.size)
            }
            importState.value = ImportState.Idle
            if (added > 0) message.value = "Добавлено файлов: $added"
        }
    }

    private fun appendAsset(asset: MediaAsset): Boolean {
        val project = _project.value ?: return false
        val track = when (asset.type) {
            MediaType.AUDIO -> project.tracks.firstOrNull { it.id == "music" } ?: project.audioTracks.firstOrNull()
            else -> project.videoTrack
        } ?: return false
        val duration = when (asset.type) {
            MediaType.IMAGE -> 3000L
            else -> asset.durationMs.coerceAtLeast(300L)
        }
        val start = track.endMs.coerceAtLeast(0)
        val clip = Clip(
            id = newId(),
            type = asset.type,
            startMs = start,
            durationMs = duration,
            assetId = asset.id,
        )
        pushUndo()
        val next = project.copy(assets = project.assets + asset).addClip(track.id, clip)
        _project.value = next
        selectedClipId.value = clip.id
        scheduleSave(next)
        return true
    }

    fun addAudioClips(): List<MediaAsset> = _project.value?.assets?.filter { it.type == MediaType.AUDIO }.orEmpty()

    fun addExistingAsset(assetId: String) {
        val project = _project.value ?: return
        val asset = project.asset(assetId) ?: return
        val track = if (asset.type == MediaType.AUDIO) {
            project.tracks.firstOrNull { it.id == "music" } ?: return
        } else {
            project.videoTrack ?: return
        }
        val duration = if (asset.type == MediaType.IMAGE) 3000L else asset.durationMs.coerceAtLeast(300L)
        val clip = Clip(
            id = newId(),
            type = asset.type,
            startMs = track.endMs,
            durationMs = duration,
            assetId = asset.id,
        )
        pushUndo()
        val next = project.addClip(track.id, clip)
        _project.value = next
        selectedClipId.value = clip.id
        scheduleSave(next)
    }

    // -------------------------------------------------------------- overlays

    fun addText(initial: String = "Ваш текст") {
        val project = _project.value ?: return
        val track = project.overlayTracks.firstOrNull() ?: return
        val duration = 3000L
        val start = freeStart(track, playheadMs.value, duration)
        val clip = Clip(
            id = newId(),
            type = MediaType.IMAGE,
            startMs = start,
            durationMs = duration,
            text = TextSpec(text = initial),
        )
        pushUndo()
        val next = project.addClip(track.id, clip)
        _project.value = next
        selectedClipId.value = clip.id
        scheduleSave(next)
    }

    fun addSticker(emoji: String) {
        val project = _project.value ?: return
        val track = project.overlayTracks.firstOrNull() ?: return
        val duration = 3000L
        val start = freeStart(track, playheadMs.value, duration)
        val clip = Clip(
            id = newId(),
            type = MediaType.IMAGE,
            startMs = start,
            durationMs = duration,
            sticker = StickerSpec(emoji = emoji),
        )
        pushUndo()
        val next = project.addClip(track.id, clip)
        _project.value = next
        selectedClipId.value = clip.id
        scheduleSave(next)
    }

    fun addImageSticker(uri: Uri) {
        viewModelScope.launch {
            importState.value = ImportState.Running(0, 1)
            importer.import(projectId, uri)
                .onSuccess { asset ->
                    val project = _project.value
                    if (project == null) {
                        message.value = "Проект не загружен"
                    } else {
                        val track = project.overlayTracks.firstOrNull()
                        if (track == null) {
                            message.value = "Нет дорожки для стикеров"
                        } else {
                            val start = freeStart(track, playheadMs.value, 3000)
                            val clip = Clip(
                                id = newId(),
                                type = MediaType.IMAGE,
                                startMs = start,
                                durationMs = 3000,
                                sticker = StickerSpec(emoji = "", imagePath = asset.relPath, scale = 0.7f),
                            )
                            pushUndo()
                            val next = project.copy(assets = project.assets + asset).addClip(track.id, clip)
                            _project.value = next
                            selectedClipId.value = clip.id
                            scheduleSave(next)
                        }
                    }
                }
                .onFailure { message.value = it.message ?: "Не удалось добавить картинку" }
            importState.value = ImportState.Idle
        }
    }

    private fun freeStart(track: Track, desired: Long, duration: Long): Long {
        var start = desired.coerceAtLeast(0)
        for (clip in track.clips.sortedBy { it.startMs }) {
            if (start + duration <= clip.startMs) break
            if (start < clip.endMs) start = clip.endMs
        }
        return start
    }

    // ---------------------------------------------------------- clip actions

    fun updateSelected(recordHistory: Boolean = true, transform: (Clip) -> Clip) {
        val clip = selectedClip ?: return
        edit(recordHistory) { project -> project.withClip(transform(clip)) }
    }

    fun editSelectedContinuous(transform: (Clip) -> Clip) {
        val clip = selectedClip ?: return
        editContinuous { project -> project.withClip(transform(clip)) }
    }

    fun setFilter(filter: FilterType) = updateSelected { it.copy(filter = filter) }
    fun setTransition(transition: TransitionType) = updateSelected { it.copy(transition = transition) }
    fun setMuted(muted: Boolean) = updateSelected { it.copy(muted = muted) }
    fun setSpeed(speed: Float) {
        updateSelected { clip ->
            val next = speed.coerceIn(0.25f, 4f)
            val sourceSpan = clip.durationMs * clip.speed
            val newDuration = (sourceSpan / next).toLong().coerceAtLeast(200)
            clip.copy(speed = next, durationMs = newDuration)
        }
    }
    fun setVolume(volume: Float) = updateSelected { it.copy(volume = volume.coerceIn(0f, 2f)) }
    fun setFadeIn(ms: Long) = updateSelected { it.copy(fadeInMs = ms) }
    fun setFadeOut(ms: Long) = updateSelected { it.copy(fadeOutMs = ms) }
    fun setText(spec: TextSpec) = updateSelected { it.copy(text = spec) }
    fun setSticker(spec: StickerSpec) = updateSelected { it.copy(sticker = spec) }

    fun splitSelected() {
        val project = _project.value ?: return
        val clip = project.clip(selectedClipId.value) ?: return
        val at = playheadMs.value
        if (at <= clip.startMs + 120 || at >= clip.endMs - 120) {
            message.value = "Поставьте курсор внутрь клипа"
            return
        }
        val track = project.trackOfClip(clip.id) ?: return
        val leftDuration = at - clip.startMs
        val right = clip.copy(
            id = newId(),
            startMs = at,
            durationMs = clip.durationMs - leftDuration,
            trimStartMs = clip.trimStartMs + (leftDuration * clip.speed).toLong(),
        )
        pushUndo()
        var next = project.withClip(clip.copy(durationMs = leftDuration))
        next = next.addClip(track.id, right)
        _project.value = next
        selectedClipId.value = right.id
        scheduleSave(next)
    }

    fun deleteSelected() {
        val project = _project.value ?: return
        val clip = project.clip(selectedClipId.value) ?: return
        pushUndo()
        val next = project.withoutClip(clip.id)
        _project.value = next
        selectedClipId.value = null
        scheduleSave(next)
    }

    fun duplicateSelected() {
        val project = _project.value ?: return
        val clip = project.clip(selectedClipId.value) ?: return
        val track = project.trackOfClip(clip.id) ?: return
        val copy = clip.copy(id = newId(), startMs = clip.endMs)
        pushUndo()
        val next = project.addClip(track.id, copy)
        _project.value = next
        selectedClipId.value = copy.id
        scheduleSave(next)
    }

    fun moveClip(clipId: String, requestedStartMs: Long) {
        val project = _project.value ?: return
        val clip = project.clip(clipId) ?: return
        val track = project.trackOfClip(clipId) ?: return
        val others = track.clips.filter { it.id != clipId }
        val previousEnd = others.filter { it.startMs <= clip.startMs }.maxOfOrNull { it.endMs } ?: 0L
        val nextStart = others.filter { it.startMs > clip.startMs }.minOfOrNull { it.startMs }
        val upper = if (nextStart != null) nextStart - clip.durationMs else Long.MAX_VALUE / 2
        val start = requestedStartMs.coerceIn(min(previousEnd, upper), max(previousEnd, upper))
        val next = project.withClip(clip.copy(startMs = start))
        _project.value = next
        scheduleSave(next)
    }

    fun trimClipStart(clipId: String, requestedStartMs: Long) {
        val project = _project.value ?: return
        val clip = project.clip(clipId) ?: return
        val track = project.trackOfClip(clipId) ?: return
        val others = track.clips.filter { it.id != clipId }
        val previousEnd = others.filter { it.startMs < clip.startMs }.maxOfOrNull { it.endMs } ?: 0L
        val asset = project.asset(clip.assetId)
        val minStartFromSource = if (clip.type == MediaType.VIDEO || clip.type == MediaType.AUDIO) {
            clip.startMs - (clip.trimStartMs / clip.speed).toLong()
        } else 0L
        val lower = max(previousEnd, minStartFromSource)
        val upper = clip.endMs - 200
        if (upper <= lower) return
        val start = requestedStartMs.coerceIn(lower, upper)
        val delta = start - clip.startMs
        val trim = if (clip.type == MediaType.VIDEO || clip.type == MediaType.AUDIO) {
            (clip.trimStartMs + (delta * clip.speed).toLong()).coerceAtLeast(0)
        } else clip.trimStartMs
        val duration = (clip.durationMs - delta).coerceAtLeast(200)
        edit(recordHistory = false) { it.withClip(clip.copy(startMs = start, trimStartMs = trim, durationMs = duration)) }
    }

    fun trimClipEnd(clipId: String, requestedEndMs: Long) {
        val project = _project.value ?: return
        val clip = project.clip(clipId) ?: return
        val track = project.trackOfClip(clipId) ?: return
        val others = track.clips.filter { it.id != clipId }
        val nextStart = others.filter { it.startMs > clip.startMs }.minOfOrNull { it.startMs }
        val asset = project.asset(clip.assetId)
        var maxEnd = Long.MAX_VALUE / 2
        if (nextStart != null) maxEnd = nextStart
        if (asset != null && (clip.type == MediaType.VIDEO || clip.type == MediaType.AUDIO)) {
            val sourceLeft = (asset.durationMs - clip.trimStartMs).coerceAtLeast(0)
            val maxBySource = clip.startMs + (sourceLeft / clip.speed).toLong()
            maxEnd = min(maxEnd, maxBySource)
        }
        val end = requestedEndMs.coerceIn(clip.startMs + 200, max(maxEnd, clip.startMs + 200))
        edit(recordHistory = false) { it.withClip(clip.copy(durationMs = end - clip.startMs)) }
    }

    fun setTrackMuted(trackId: String, muted: Boolean) {
        edit { project ->
            project.tracks.firstOrNull { it.id == trackId }
                ?.let { track -> project.withTrack(track.copy(muted = muted)) }
                ?: project
        }
    }

    // ---------------------------------------------------------------- export

    fun export() {
        val project = _project.value ?: return
        if (exportState.value is ExportState.Running) return
        isPlaying.value = false
        val composition = compositionFactory.build(project, preview = false)
        if (composition == null) {
            exportState.value = ExportState.Failed("Не удалось собрать проект для экспорта")
            return
        }
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val target = File(dir, GalleryPublisher.exportFileName(project.name))
        exportState.value = ExportState.Running(0f)

        val t = Transformer.Builder(getApplication())
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    viewModelScope.launch {
                        val published = GalleryPublisher.publish(getApplication(), target)
                        exportState.value = published.fold(
                            onSuccess = { uri -> ExportState.Done(uri, target.name, target) },
                            onFailure = { ExportState.Failed(it.message ?: "Не удалось сохранить видео") },
                        )
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    exportState.value = ExportState.Failed(friendlyError(exportException))
                }
            })
            .build()
        transformer = t
        try {
            t.start(composition, target.absolutePath)
        } catch (error: Exception) {
            exportState.value = ExportState.Failed(friendlyError(error))
            return
        }

        viewModelScope.launch {
            val holder = ProgressHolder()
            while (exportState.value is ExportState.Running) {
                val state = runCatching { t.getProgress(holder) }.getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    exportState.value = ExportState.Running((holder.progress / 100f).coerceIn(0f, 1f))
                }
                delay(300)
            }
        }
    }

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        exportState.value = ExportState.Idle
    }

    fun resetExport() {
        if (exportState.value !is ExportState.Running) {
            exportState.value = ExportState.Idle
        }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("codec", true) -> "Устройство не поддерживает этот формат кодирования"
            raw.contains("space", true) -> "Недостаточно места для экспорта"
            raw.isBlank() -> "Ошибка экспорта"
            else -> raw.take(220)
        }
    }

    override fun onCleared() {
        super.onCleared()
        transformer?.cancel()
        transformer = null
        _project.value?.let { project -> runCatching { repository.save(project) } }
    }

    private fun Project.clipCount(): Int = tracks.sumOf { it.clips.size }

    fun totalClipCount(): Int = _project.value?.tracks?.sumOf { it.clips.size } ?: 0

    fun hasContent(): Boolean = (_project.value?.tracks?.sumOf { it.clips.size } ?: 0) > 0

    @Suppress("unused")
    private fun isZero(value: Float): Boolean = abs(value) < 0.0001f
}
