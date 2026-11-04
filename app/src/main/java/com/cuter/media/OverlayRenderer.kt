package com.cuter.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.cuter.data.Clip
import com.cuter.data.FontPreset
import com.cuter.data.StickerSpec
import com.cuter.data.TextAlignPreset
import com.cuter.data.TextAnimation
import com.cuter.data.TextSpec
import com.cuter.data.TransitionType
import com.cuter.data.Project
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private const val FADE_MS = 220f

/** Draws one text or sticker clip as a video overlay, animated over time. */
class ClipOverlay(
    private val clip: Clip,
    private val canvasWidth: Int,
    private val canvasHeight: Int,
    renderScale: Float,
    private val projectDir: File,
) : BitmapOverlay() {

    private val width = (canvasWidth * renderScale).toInt().coerceAtLeast(2)
    private val height = (canvasHeight * renderScale).toInt().coerceAtLeast(2)
    private val scaleFactor = width.toFloat() / canvasWidth

    private var bitmap: Bitmap? = null
    private var bitmapKey: Any? = null
    private var avatar: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000
        val key = contentKey(timeMs)
        val existing = bitmap
        if (existing != null && bitmapKey == key) return existing
        val created = if (clip.text != null) drawText(clip.text, timeMs) else drawSticker(clip.sticker)
        bitmap = created
        bitmapKey = key
        return created
    }

    private fun contentKey(timeMs: Long): Any {
        val text = clip.text ?: return "sticker"
        if (text.animation != TextAnimation.TYPEWRITER) return "text:${text.text}"
        val progress = typewriterProgress(timeMs)
        return "text:${text.text}:${(text.text.length * progress).toInt()}"
    }

    private fun drawText(spec: TextSpec, timeMs: Long): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.color.toInt()
            textSize = (spec.size / 1000f) * height
            letterSpacing = spec.letterSpacing.coerceIn(-0.05f, 0.4f)
            typeface = typefaceFor(spec)
            if (spec.shadow) {
                setShadowLayer(textSize * 0.07f, 0f, textSize * 0.05f, 0x99000000.toInt())
            }
            isSubpixelText = true
        }
        val progress = typewriterProgress(timeMs)
        val raw = if (spec.animation == TextAnimation.TYPEWRITER) {
            spec.text.take((spec.text.length * progress).toInt())
        } else spec.text
        val content = raw.ifEmpty { " " }
        val lines = content.split("\n")
        val metrics = paint.fontMetrics
        val lineHeight = paint.fontSpacing
        val blockHeight = lineHeight * lines.size

        paint.textAlign = when (spec.align) {
            TextAlignPreset.LEFT -> Paint.Align.LEFT
            TextAlignPreset.CENTER -> Paint.Align.CENTER
            TextAlignPreset.RIGHT -> Paint.Align.RIGHT
        }
        val margin = paint.textSize * 0.6f
        val x = when (spec.align) {
            TextAlignPreset.LEFT -> margin
            TextAlignPreset.CENTER -> width / 2f
            TextAlignPreset.RIGHT -> width - margin
        }

        if (Color.alpha(spec.background.toInt()) > 0) {
            val widest = lines.maxOf { paint.measureText(it) }
            val padX = spec.backgroundPadding * paint.textSize
            val padY = spec.backgroundPadding * paint.textSize * 0.55f
            val left = when (spec.align) {
                TextAlignPreset.LEFT -> x - padX
                TextAlignPreset.CENTER -> x - widest / 2f - padX
                TextAlignPreset.RIGHT -> x - widest - padX
            }
            val top = height / 2f - blockHeight / 2f - padY
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.background.toInt() }
            val radius = paint.textSize * 0.25f
            canvas.drawRoundRect(
                RectF(left, top, left + widest + padX * 2, top + blockHeight + padY * 2),
                radius, radius, bgPaint,
            )
        }

        val firstBaseline = height / 2f - blockHeight / 2f + lineHeight / 2f - (metrics.ascent + metrics.descent) / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, firstBaseline + index * lineHeight, paint)
        }
        return bmp
    }

    private fun drawSticker(spec: StickerSpec?): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val image = spec?.imagePath?.let { loadBitmap(it) }
        if (image != null) {
            val targetWidth = width * 0.36f
            val ratio = targetWidth / image.width
            val targetHeight = image.height * ratio
            val rect = RectF(
                width / 2f - targetWidth / 2f,
                height / 2f - targetHeight / 2f,
                width / 2f + targetWidth / 2f,
                height / 2f + targetHeight / 2f,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(image, null, rect, paint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = height * 0.17f
                textAlign = Paint.Align.CENTER
            }
            val metrics = paint.fontMetrics
            val y = height / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(spec?.emoji ?: "🔥", width / 2f, y, paint)
        }
        return bmp
    }

    private fun loadBitmap(path: String): Bitmap? {
        avatar?.let { return it }
        val file = if (File(path).isAbsolute) File(path) else File(projectDir, path)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) > 1200) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()?.also { avatar = it }
    }

    private fun typewriterProgress(timeMs: Long): Float {
        val local = (timeMs - clip.startMs).coerceAtLeast(0L)
        val window = (clip.durationMs * 0.6f).coerceAtLeast(1f)
        return (local / window).coerceIn(0f, 1f)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        if (timeMs < clip.startMs || timeMs > clip.endMs) {
            return OverlaySettings.Builder().setAlphaScale(0f).build()
        }
        val local = (timeMs - clip.startMs).toFloat()
        val duration = clip.durationMs.toFloat().coerceAtLeast(1f)
        val spec = clip.text
        val baseAlpha = min(1f, local / FADE_MS) * min(1f, (duration - local) / FADE_MS)
        var alpha = baseAlpha.coerceIn(0f, 1f)
        var scale = spec?.scale ?: clip.sticker?.scale ?: 1f
        var yAnchor = spec?.y ?: clip.sticker?.y ?: 0.5f
        val xAnchor = spec?.x ?: clip.sticker?.x ?: 0.5f
        val rotation = spec?.rotation ?: clip.sticker?.rotation ?: 0f

        val animation = spec?.animation ?: TextAnimation.NONE
        if (animation != TextAnimation.NONE) {
            val window = 380f
            val p = (local / window).coerceIn(0f, 1f)
            when (animation) {
                TextAnimation.FADE -> alpha *= easeOut(p)
                TextAnimation.POP -> {
                    val overshoot = 1.08f
                    scale *= 0.72f + (overshoot - 0.72f) * easeOutBack(p)
                    alpha *= easeOut(p)
                }
                TextAnimation.SLIDE_UP -> {
                    yAnchor += (1f - easeOutBack(p)) * 0.08f
                    alpha *= easeOut(p)
                }
                TextAnimation.TYPEWRITER -> Unit
                TextAnimation.NONE -> Unit
            }
        }

        return OverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(scale, scale)
            .setRotationDegrees(rotation)
            .setBackgroundFrameAnchor(xAnchor * 2f - 1f, -(yAnchor * 2f - 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .build()
    }

    override fun release() {
        super.release()
        bitmap?.recycle()
        bitmap = null
        avatar?.recycle()
        avatar = null
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f)
    }

    private fun typefaceFor(spec: TextSpec): Typeface {
        val base = when (spec.font) {
            FontPreset.SANS -> Typeface.SANS_SERIF
            FontPreset.SERIF -> Typeface.SERIF
            FontPreset.MONO -> Typeface.MONOSPACE
            FontPreset.CURSIVE -> Typeface.create("casual", Typeface.NORMAL)
            FontPreset.BLACK -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        val style = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }
}

/** Full-frame fade used for clip-to-clip transitions. */
class TransitionOverlay(
    private val project: Project,
    canvasWidth: Int,
    canvasHeight: Int,
) : BitmapOverlay() {

    private val fullWidth = canvasWidth.toFloat()
    private val fullHeight = canvasHeight.toFloat()
    private val black: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
    private val white: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private data class Boundary(val timeMs: Long, val halfMs: Long, val white: Boolean)

    private val boundaries: List<Boundary> = buildList {
        val clips = project.videoTrack?.clips?.sortedBy { it.startMs } ?: return@buildList
        for (i in 0 until clips.size - 1) {
            val current = clips[i]
            val next = clips[i + 1]
            if (current.transition == TransitionType.NONE) continue
            val half = min(320L, min(current.durationMs, next.durationMs) / 2)
            if (half < 60) continue
            add(Boundary(current.endMs, half, current.transition == TransitionType.FADE_WHITE))
        }
    }

    private var lastWhite = false

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000
        lastWhite = boundaries.firstOrNull { abs(timeMs - it.timeMs) < it.halfMs }?.white ?: false
        return if (lastWhite) white else black
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        var alpha = 0f
        var white = false
        for (b in boundaries) {
            val distance = abs(timeMs - b.timeMs)
            if (distance < b.halfMs) {
                val a = 1f - distance.toFloat() / b.halfMs
                if (a > alpha) {
                    alpha = a
                    white = b.white
                }
            }
        }
        return OverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(fullWidth, fullHeight)
            .build()
    }

    override fun release() {
        super.release()
        black.recycle()
        white.recycle()
    }
}

internal fun TextSpec.safeText(): String = text.ifBlank { " " }

@Suppress("unused")
private fun smooth(t: Float): Float = sin(t * Math.PI.toFloat() / 2f)
