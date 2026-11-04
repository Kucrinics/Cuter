package com.cuter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

object MediaProbe {

    data class Info(
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val rotation: Int = 0,
    )

    fun probe(file: File, type: MediaType): Info {
        if (type == MediaType.IMAGE) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            return Info(width = opts.outWidth, height = opts.outHeight)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width; width = height; height = tmp
            }
            Info(durationMs = duration, width = width, height = height, rotation = rotation)
        } catch (_: Exception) {
            Info()
        } finally {
            retriever.release()
        }
    }

    /** Renders a small JPEG preview for the timeline / project list. */
    fun writeThumbnail(file: File, type: MediaType, out: File, maxWidth: Int = 320): Boolean {
        return try {
            val bitmap = when (type) {
                MediaType.IMAGE -> decodeScaled(file, maxWidth)
                MediaType.VIDEO -> videoFrame(file, maxWidth)
                MediaType.AUDIO -> null
            } ?: return false
            out.parentFile?.mkdirs()
            out.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeScaled(file: File, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun videoFrame(file: File, maxWidth: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            if (frame.width <= maxWidth) {
                frame
            } else {
                val ratio = maxWidth.toFloat() / frame.width
                val scaled = Bitmap.createScaledBitmap(
                    frame, maxWidth, (frame.height * ratio).toInt().coerceAtLeast(1), true
                )
                if (scaled !== frame) frame.recycle()
                scaled
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
