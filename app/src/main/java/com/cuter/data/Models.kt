package com.cuter.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class CanvasRatio(val label: String, val w: Int, val h: Int) {
    RATIO_9_16("9:16", 9, 16),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_5("4:5", 4, 5);

    val portrait: Boolean get() = h > w
}

@Serializable
enum class MediaType { VIDEO, IMAGE, AUDIO }

@Serializable
enum class TrackType { VIDEO, OVERLAY, AUDIO }

@Serializable
enum class FilterType(val label: String) {
    NONE("Без фильтра"),
    MONO("Ч/Б"),
    SEPIA("Сепия"),
    WARM("Тёплый"),
    COOL("Холодный"),
    VINTAGE("Винтаж"),
    CONTRAST("Контраст"),
    FADE("Матовый"),
    INVERT("Негатив"),
    BLUR("Блюр");

    val isDefault: Boolean get() = this == NONE
}

@Serializable
enum class TransitionType(val label: String) {
    NONE("Нет"),
    FADE_BLACK("В затемнение"),
    FADE_WHITE("Вспышка")
}

@Serializable
enum class TextAnimation(val label: String) {
    NONE("Без анимации"),
    FADE("Появление"),
    POP("Пружина"),
    SLIDE_UP("Подъём"),
    TYPEWRITER("Печатная машинка")
}

@Serializable
enum class FontPreset(val label: String) {
    SANS("Гротеск"),
    SERIF("Антиква"),
    MONO("Моно"),
    CURSIVE("Рукописный"),
    BLACK("Жирный")
}

@Serializable
enum class TextAlignPreset(val label: String) {
    LEFT("Слева"),
    CENTER("По центру"),
    RIGHT("Справа")
}

@Serializable
data class MediaAsset(
    val id: String,
    val relPath: String,
    val name: String,
    val type: MediaType,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val thumbName: String? = null,
)

@Serializable
data class TextSpec(
    val text: String = "Ваш текст",
    val size: Float = 62f,
    val color: Long = 0xFFFFFFFF,
    val background: Long = 0x00000000,
    val backgroundPadding: Float = 0.35f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val font: FontPreset = FontPreset.BLACK,
    val align: TextAlignPreset = TextAlignPreset.CENTER,
    val animation: TextAnimation = TextAnimation.POP,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val shadow: Boolean = true,
    val letterSpacing: Float = 0f,
)

@Serializable
data class StickerSpec(
    val emoji: String = "🔥",
    val imagePath: String? = null,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

@Serializable
data class Clip(
    val id: String,
    val type: MediaType,
    val startMs: Long = 0L,
    val durationMs: Long = 3000L,
    val assetId: String? = null,
    val trimStartMs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val filter: FilterType = FilterType.NONE,
    val transition: TransitionType = TransitionType.NONE,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val text: TextSpec? = null,
    val sticker: StickerSpec? = null,
) {
    val endMs: Long get() = startMs + durationMs
    val isOverlay: Boolean get() = text != null || sticker != null
    val title: String
        get() = when {
            text != null -> text.text.ifBlank { "Текст" }
            sticker != null -> sticker.emoji.ifBlank { "Стикер" }
            else -> ""
        }
}

@Serializable
data class Track(
    val id: String,
    val type: TrackType,
    val name: String,
    val clips: List<Clip> = emptyList(),
    val muted: Boolean = false,
    val locked: Boolean = false,
) {
    val endMs: Long get() = clips.maxOfOrNull { it.endMs } ?: 0L
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val ratio: CanvasRatio = CanvasRatio.RATIO_9_16,
    val tracks: List<Track> = defaultTracks(),
    val assets: List<MediaAsset> = emptyList(),
) {
    val videoTrack: Track? get() = tracks.firstOrNull { it.type == TrackType.VIDEO }
    val overlayTracks: List<Track> get() = tracks.filter { it.type == TrackType.OVERLAY }
    val audioTracks: List<Track> get() = tracks.filter { it.type == TrackType.AUDIO }

    val durationMs: Long
        get() {
            val videoEnd = videoTrack?.endMs ?: 0L
            val all = tracks.flatMap { it.clips }.maxOfOrNull { it.endMs } ?: 0L
            return maxOf(videoEnd, all)
        }

    fun asset(id: String?): MediaAsset? = assets.firstOrNull { it.id == id }

    fun clip(id: String?): Clip? = tracks.asSequence().flatMap { it.clips }.firstOrNull { it.id == id }

    fun trackOfClip(clipId: String): Track? = tracks.firstOrNull { t -> t.clips.any { it.id == clipId } }

    fun withTrack(track: Track): Project =
        copy(tracks = tracks.map { if (it.id == track.id) track else it })

    fun withClip(clip: Clip): Project {
        val track = trackOfClip(clip.id) ?: return this
        return withTrack(track.copy(clips = track.clips.map { if (it.id == clip.id) clip else it }))
    }

    fun withoutClip(clipId: String): Project {
        val track = trackOfClip(clipId) ?: return this
        return withTrack(track.copy(clips = track.clips.filterNot { it.id == clipId }))
    }

    fun addClip(trackId: String, clip: Clip): Project =
        tracks.firstOrNull { it.id == trackId }?.let { t ->
            withTrack(t.copy(clips = (t.clips + clip).sortedBy { it.startMs }))
        } ?: this

    companion object {
        fun defaultTracks(): List<Track> = listOf(
            Track(id = "video", type = TrackType.VIDEO, name = "Видео"),
            Track(id = "overlay", type = TrackType.OVERLAY, name = "Текст"),
            Track(id = "music", type = TrackType.AUDIO, name = "Музыка"),
            Track(id = "voice", type = TrackType.AUDIO, name = "Озвучка"),
        )
    }
}

@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val durationMs: Long,
    val ratio: CanvasRatio,
    val thumbPath: String? = null,
    val clipCount: Int = 0,
)
