package com.arrow3d.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface ISoundPlayer {
    fun play(event: SoundEvent)
}

/**
 * Plays the synthesised sounds with a [SoundPool]: they are rendered once to WAV files in the
 * cache directory, off the main thread, and a sound that is not loaded yet is simply skipped.
 */
class AndroidSoundPlayer(context: Context) : ISoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** Sound name -> SoundPool id. An id can only play once its load has completed. */
    private val ids = ConcurrentHashMap<String, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready += sampleId
        }
        val dir = File(context.applicationContext.cacheDir, "sounds")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            dir.mkdirs()
            SoundBank.renderAll().forEach { (name, pcm) ->
                val file = File(dir, "$name-v$BANK_VERSION.wav")
                if (!file.exists()) file.writeBytes(ToneSynth.wav(pcm))
                ids[name] = pool.load(file.absolutePath, 1)
            }
        }
    }

    override fun play(event: SoundEvent) {
        val id = ids[SoundBank.nameOf(event)] ?: return
        if (id !in ready) return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val VOLUME = 0.8f
        /** Bump when the sounds change, so stale cached files are not reused. */
        const val BANK_VERSION = 1
    }
}
