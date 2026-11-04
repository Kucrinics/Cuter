package com.cuter.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cuter.media.FormatUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MediaImporter(context: Context, private val repository: ProjectRepository) {
    private val appContext = context.applicationContext

    suspend fun import(projectId: String, uri: Uri): Result<MediaAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val mime = resolver.getType(uri)
            val display = queryName(uri) ?: "asset"
            val type = when {
                mime?.startsWith("video/") == true -> MediaType.VIDEO
                mime?.startsWith("image/") == true -> MediaType.IMAGE
                mime?.startsWith("audio/") == true -> MediaType.AUDIO
                display.endsWith(".mp4", true) || display.endsWith(".mov", true) ||
                    display.endsWith(".mkv", true) || display.endsWith(".webm", true) -> MediaType.VIDEO
                display.endsWith(".jpg", true) || display.endsWith(".png", true) ||
                    display.endsWith(".webp", true) || display.endsWith(".heic", true) -> MediaType.IMAGE
                display.endsWith(".mp3", true) || display.endsWith(".wav", true) ||
                    display.endsWith(".m4a", true) || display.endsWith(".aac", true) -> MediaType.AUDIO
                else -> error("Формат не поддерживается")
            }

            val id = newId()
            val ext = FormatUtil.extensionForMime(mime)
            val relPath = "media/$id.$ext"
            val target = File(repository.projectDir(projectId), relPath)
            target.parentFile?.mkdirs()
            val stream = resolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            if (target.length() == 0L) {
                target.delete()
                error("Пустой файл")
            }

            val info = MediaProbe.probe(target, type)
            if (type != MediaType.IMAGE && info.durationMs < 100) {
                target.delete()
                error("Файл повреждён или не содержит медиаданных")
            }

            val thumbName = if (type != MediaType.AUDIO) "thumb_$id.jpg" else null
            if (thumbName != null) {
                MediaProbe.writeThumbnail(target, type, File(repository.thumbsDir(projectId), thumbName))
            }

            MediaAsset(
                id = id,
                relPath = relPath,
                name = display.substringBeforeLast('.').take(40).ifBlank { "Медиа" },
                type = type,
                durationMs = info.durationMs,
                width = info.width,
                height = info.height,
                rotation = info.rotation,
                thumbName = thumbName,
            )
        }
    }

    private fun queryName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()
    }
}
