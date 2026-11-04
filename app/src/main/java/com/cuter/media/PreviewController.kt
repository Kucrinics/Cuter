package com.cuter.media

import android.content.Context
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer

@UnstableApi
class PreviewController(context: Context) {

    private val appContext = context.applicationContext
    private var player: CompositionPlayer? = null
    private var composition: Composition? = null
    private var textureView: TextureView? = null

    var onEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun attach(view: TextureView) {
        if (textureView === view) return
        textureView = view
        player?.setVideoTextureView(view)
    }

    fun bind(composition: Composition, startMs: Long, playWhenReady: Boolean) {
        if (this.composition === composition && player != null) {
            player?.seekTo(startMs)
            player?.playWhenReady = playWhenReady
            return
        }
        releasePlayer()
        this.composition = composition
        val p = CompositionPlayer.Builder(appContext).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError?.invoke(error.message ?: "Не удалось воспроизвести проект")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded?.invoke()
            }
        })
        textureView?.let { p.setVideoTextureView(it) }
        p.setComposition(composition)
        p.prepare()
        p.seekTo(startMs.coerceAtLeast(0))
        p.playWhenReady = playWhenReady
        player = p
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun position(): Long = player?.currentPosition ?: 0L

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun clear() {
        releasePlayer()
        composition = null
    }

    fun release() {
        releasePlayer()
        textureView = null
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
