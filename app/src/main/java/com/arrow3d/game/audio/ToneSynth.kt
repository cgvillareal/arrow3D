package com.arrow3d.game.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// Pure Kotlin, no Android imports: the game's sounds are synthesised, so there are no audio
// assets to ship, and the waveforms are unit-testable on the JVM.

enum class Wave { SINE, TRIANGLE, SQUARE }

/** One note: starts at [startMs], lasts [durationMs], decays exponentially. */
data class Note(val freqHz: Double, val startMs: Int, val durationMs: Int, val gain: Double = 1.0)

object ToneSynth {
    const val SAMPLE_RATE = 22_050
    private const val ATTACK_MS = 4.0
    private const val MASTER_GAIN = 0.55

    /** Equal temperament: [semitones] above A4 (440 Hz). */
    fun pitch(semitones: Int): Double = 440.0 * 2.0.pow(semitones / 12.0)

    /** Mixes [notes] into 16-bit mono PCM. */
    fun render(notes: List<Note>, wave: Wave, decay: Double = 6.0): ShortArray {
        val totalMs = notes.maxOf { it.startMs + it.durationMs }
        val out = DoubleArray(totalMs * SAMPLE_RATE / 1000)
        for (note in notes) {
            val start = note.startMs * SAMPLE_RATE / 1000
            val count = note.durationMs * SAMPLE_RATE / 1000
            for (i in 0 until count) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / SAMPLE_RATE
                val ms = t * 1000
                val attack = (ms / ATTACK_MS).coerceAtMost(1.0)
                // Short fade at the very end so a note never clicks when it stops.
                val release = ((count - i).toDouble() / (SAMPLE_RATE * 0.004)).coerceAtMost(1.0)
                val envelope = attack * release * exp(-decay * t / (note.durationMs / 1000.0))
                out[idx] += oscillator(wave, note.freqHz * t) * envelope * note.gain
            }
        }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1.0)
        return ShortArray(out.size) { i -> (out[i] / peak * MASTER_GAIN * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun oscillator(wave: Wave, phase: Double): Double {
        val p = phase - kotlin.math.floor(phase)
        return when (wave) {
            Wave.SINE -> sin(2 * PI * p)
            Wave.TRIANGLE -> 1 - 4 * abs(p - 0.5)
            Wave.SQUARE -> if (p < 0.5) 0.6 else -0.6
        }
    }

    /** 16-bit mono PCM wrapped in a WAV (RIFF) container, ready for SoundPool. */
    fun wav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        fun putInt(at: Int, v: Int) {
            for (k in 0 until 4) out[at + k] = (v shr (8 * k)).toByte()
        }
        fun putShort(at: Int, v: Int) {
            out[at] = v.toByte()
            out[at + 1] = (v shr 8).toByte()
        }
        fun putAscii(at: Int, s: String) = s.forEachIndexed { k, c -> out[at + k] = c.code.toByte() }
        putAscii(0, "RIFF")
        putInt(4, 36 + dataSize)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt(16, 16) // PCM chunk size
        putShort(20, 1) // PCM format
        putShort(22, 1) // mono
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2) // byte rate
        putShort(32, 2) // block align
        putShort(34, 16) // bits per sample
        putAscii(36, "data")
        putInt(40, dataSize)
        samples.forEachIndexed { i, s -> putShort(44 + i * 2, s.toInt()) }
        return out
    }
}

/** The game's sound effects (design doc, section 4), one per event. */
sealed interface SoundEvent {
    /** Pitch climbs a major scale as the combo grows. */
    data class Released(val combo: Int) : SoundEvent
    data object Blocked : SoundEvent
    data object TravelerFold : SoundEvent
    data object Chain : SoundEvent
    data object LevelComplete : SoundEvent
    data object OutOfLives : SoundEvent
}

/** Every sound the game can play, keyed by a stable name. */
object SoundBank {
    const val MAX_COMBO_STEP = 8
    private val majorScale = listOf(0, 2, 4, 5, 7, 9, 11, 12)

    fun nameOf(event: SoundEvent): String = when (event) {
        is SoundEvent.Released -> "release_${event.combo.coerceIn(1, MAX_COMBO_STEP)}"
        SoundEvent.Blocked -> "blocked"
        SoundEvent.TravelerFold -> "fold"
        SoundEvent.Chain -> "chain"
        SoundEvent.LevelComplete -> "complete"
        SoundEvent.OutOfLives -> "out_of_lives"
    }

    fun renderAll(): Map<String, ShortArray> = buildMap {
        // C5 is 3 semitones above A4.
        for (step in 1..MAX_COMBO_STEP) {
            val f = ToneSynth.pitch(3 + majorScale[step - 1])
            put("release_$step", ToneSynth.render(listOf(Note(f, 0, 110), Note(f * 2, 0, 70, 0.25)), Wave.TRIANGLE))
        }
        put("blocked", ToneSynth.render(listOf(Note(110.0, 0, 70), Note(98.0, 85, 90)), Wave.SQUARE, decay = 3.0))
        put("fold", ToneSynth.render(listOf(Note(ToneSynth.pitch(27), 0, 45)), Wave.SINE))
        put("chain", ToneSynth.render((0..2).map { Note(ToneSynth.pitch(15 + 4 * it), 45 * it, 80, 0.7) }, Wave.SINE))
        put(
            "complete",
            ToneSynth.render(listOf(3, 7, 10, 15).mapIndexed { i, s -> Note(ToneSynth.pitch(s), 110 * i, if (i == 3) 420 else 160) }, Wave.TRIANGLE, decay = 4.0),
        )
        put("out_of_lives", ToneSynth.render(listOf(-2, -5, -9).mapIndexed { i, s -> Note(ToneSynth.pitch(s), 170 * i, 220) }, Wave.TRIANGLE, decay = 3.0))
    }
}
