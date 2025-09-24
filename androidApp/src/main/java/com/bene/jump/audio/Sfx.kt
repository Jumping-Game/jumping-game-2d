package com.bene.jump.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class Sfx(context: Context) {
    private val soundPool =
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    private var jumpSoundId: Int? = null
    private val appContext = context.applicationContext

    fun primeJump(resId: Int) {
        jumpSoundId = soundPool.load(appContext, resId, 1)
    }

    fun playJump() {
        val id = jumpSoundId ?: return
        soundPool.play(id, 0.4f, 0.4f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
