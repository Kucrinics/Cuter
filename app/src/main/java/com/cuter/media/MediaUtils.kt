package com.cuter.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.cuter.data.MediaType
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import kotlin.math.min

/** Constant gain + fade in/out applied to decoded PCM. */
class GainAudioProcessor(
    private val gain: Float,
    private val fadeInMs: Long = 0L,
    private val fadeOutMs: Long = 0L,
    private val totalDurationMs: Long = 0L,
) : BaseAudioProcessor() {

    private var framePosition = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        framePosition = 0L
    }

    override fun onReset() {
        framePosition = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val frameSize = (if (isFloat) 4 else 2) * format.channelCount

        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        if (isFloat) {
            while (inputBuffer.remaining() >= frameSize) {
                val g = gainAt(framePosition, format.sampleRate)
                repeat(format.channelCount) {
                    output.putFloat(inputBuffer.float * g)
                }
                framePosition++
            }
        } else {
            while (inputBuffer.remaining() >= frameSize) {
                val g = gainAt(framePosition, format.sampleRate)
                repeat(format.channelCount) {
                    val sample = inputBuffer.short.toInt()
                    val value = (sample * g).toInt().coerceIn(-32768, 32767)
                    output.putShort(value.toShort())
                }
                framePosition++
            }
        }
        output.flip()
    }

    private fun gainAt(frame: Long, sampleRate: Int): Float {
        var result = gain
        if (sampleRate <= 0) return result
        val timeMs = frame * 1000.0 / sampleRate
        if (fadeInMs > 0 && timeMs < fadeInMs) {
            result *= (timeMs / fadeInMs).toFloat().coerceIn(0f, 1f)
        }
        if (fadeOutMs > 0 && totalDurationMs > 0) {
            val fadeStart = totalDurationMs - fadeOutMs
            if (timeMs > fadeStart) {
                result *= ((totalDurationMs - timeMs) / fadeOutMs).toFloat().coerceIn(0f, 1f)
            }
        }
        return result
    }
}

class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/** Silent PCM wav used to offset audio clips on the timeline. */
object SilenceFactory {
    private const val SAMPLE_RATE = 44100

    fun get(context: Context, durationMs: Long): File {
        val rounded = (durationMs.coerceAtLeast(50) / 50) * 50
        val dir = File(context.cacheDir, "silence").apply { mkdirs() }
        val file = File(dir, "silence_$rounded.wav")
        if (file.exists() && file.length() > 44) return file
        val frames = (rounded * SAMPLE_RATE / 1000).toInt().coerceAtLeast(1)
        val dataSize = frames * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write("RIFF".toByteArray())
            raf.write(intLe(36 + dataSize))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intLe(16))
            raf.write(shortLe(1))
            raf.write(shortLe(1))
            raf.write(intLe(SAMPLE_RATE))
            raf.write(intLe(SAMPLE_RATE * 2))
            raf.write(shortLe(2))
            raf.write(shortLe(16))
            raf.write("data".toByteArray())
            raf.write(intLe(dataSize))
            val chunk = ByteArray(64 * 1024)
            var left = dataSize
            while (left > 0) {
                val n = min(left, chunk.size)
                raf.write(chunk, 0, n)
                left -= n
            }
        }
        return file
    }

    private fun intLe(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun shortLe(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}

/** A solid black frame reused as filler for gaps / empty timelines. */
object BlackFrameFactory {
    fun get(context: Context): File {
        val file = File(context.cacheDir, "black_frame.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}

object FormatUtil {
    fun formatMs(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms.coerceAtLeast(0) % 1000) / 100
        return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
    }

    fun extensionForMime(mime: String?): String {
        val subtype = mime?.substringAfter('/', "")?.lowercase(Locale.US).orEmpty()
        return when (subtype) {
            "jpeg" -> "jpg"
            "quicktime" -> "mov"
            "3gpp" -> "3gp"
            "x-matroska" -> "mkv"
            "mpeg" -> "mp3"
            "mp4a-latm" -> "m4a"
            "x-wav", "wav" -> "wav"
            "" -> "mp4"
            else -> subtype.take(5)
        }
    }

    fun mediaLabel(type: MediaType): String = when (type) {
        MediaType.VIDEO -> "Видео"
        MediaType.IMAGE -> "Фото"
        MediaType.AUDIO -> "Аудио"
    }
}
