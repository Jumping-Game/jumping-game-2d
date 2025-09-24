package com.bene.jump.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class Bgm(private val context: Context) {
    private var player: MediaPlayer? = null

    fun prepare(uri: Uri) {
        release()
        player =
            MediaPlayer.create(context, uri)?.apply {
                isLooping = true
            }
    }

    fun play() {
        player?.start()
    }

    fun pause() {
        player?.pause()
    }

    fun release() {
        player?.release()
        player = null
    }
}
