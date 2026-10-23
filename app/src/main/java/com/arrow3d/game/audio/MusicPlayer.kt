package com.arrow3d.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

interface IMusicPlayer {
    /** Starts (or keeps) playing. Safe to call repeatedly. */
    fun start()

    /** Stops and frees the audio output. Safe to call repeatedly. */
    fun stop()

    /** 0 = calm, 1 = full combo. */
    fun setIntensity(value: Float)
}

/**
 * Streams [MusicGenerator] through an [AudioTrack] from its own thread. The generator is pure
 * Kotlin; this class only moves its blocks to the speaker.
 */
class AndroidMusicPlayer : IMusicPlayer {

    private val generator = MusicGenerator()
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false

    @Synchronized
    override fun start() {
        if (thread != null) return
        running = true
        thread = Thread({ loop() }, "arrow3d-music").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    override fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
    }

    override fun setIntensity(value: Float) {
        generator.intensity = value
    }

    private fun loop() {
        val rate = ToneSynth.SAMPLE_RATE
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, BLOCK * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val block = ShortArray(BLOCK)
        try {
            track.setVolume(VOLUME)
            track.play()
            while (running) {
                generator.render(block)
                if (track.write(block, 0, block.size) < 0) break
            }
            track.pause()
            track.flush()
        } catch (e: IllegalStateException) {
            // The output could not start (for example, no audio device); play nothing.
        } finally {
            track.release()
        }
    }

    private companion object {
        const val BLOCK = 1_024
        const val VOLUME = 0.35f
        const val JOIN_TIMEOUT_MS = 500L
    }
}
