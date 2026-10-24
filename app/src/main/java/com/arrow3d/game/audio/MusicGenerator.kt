package com.arrow3d.game.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generative background music, rendered block by block (pure Kotlin, no Android).
 *
 * A calm loop in A minor (Am - F - C - G, one chord per bar) with four layers: a soft pad and a
 * bass always play; an arpeggio and a high sparkle fade in with [intensity], which the game ties
 * to the combo. Intensity changes are smoothed so a combo swells the music instead of switching
 * it. The output never clips: the layers' gains add up to less than full scale.
 */
class MusicGenerator(private val sampleRate: Int = ToneSynth.SAMPLE_RATE, bpm: Double = 84.0) {

    /** Target energy, 0 (calm) to 1 (full combo). */
    var intensity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var level = 0.0
    private var sample = 0L

    private val samplesPerBeat = sampleRate * 60.0 / bpm
    private val samplesPerBar = samplesPerBeat * BEATS_PER_BAR
    private val slew = 1.0 / (sampleRate * SMOOTHING_SECONDS)

    private var padPhase = DoubleArray(3)
    private var bassPhase = 0.0
    private var arpPhase = 0.0
    private var sparklePhase = 0.0

    /** Length of one full turn of the progression, in samples. */
    val loopSamples: Long get() = (samplesPerBar * PROGRESSION.size).toLong()

    /** Fills [out] with the next [out].size samples of 16-bit mono PCM. */
    fun render(out: ShortArray) {
        for (i in out.indices) {
            level += (intensity - level).coerceIn(-slew, slew)
            out[i] = (next() * Short.MAX_VALUE).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            sample++
        }
    }

    private fun next(): Double {
        val barPos = sample % loopSamples.coerceAtLeast(1)
        val bar = (barPos / samplesPerBar).toInt()
        val inBar = barPos - bar * samplesPerBar
        val chord = PROGRESSION[bar]

        // Pad: the triad, faded around chord changes so the switch never clicks.
        val edge = minOf(inBar, samplesPerBar - inBar) / (sampleRate * CHORD_FADE_SECONDS)
        val padGain = PAD_GAIN * edge.coerceAtMost(1.0)
        var mix = 0.0
        for (k in 0..2) {
            padPhase[k] = advance(padPhase[k], ToneSynth.pitch(chord[k] - 12))
            mix += sin(2 * PI * padPhase[k]) * padGain / 3
        }

        // Bass: the root on beats 1 and 3, plucked.
        val halfBar = samplesPerBeat * 2
        val sinceBass = inBar % halfBar
        bassPhase = advance(bassPhase, ToneSynth.pitch(chord[0] - 24))
        mix += triangle(bassPhase) * BASS_GAIN * exp(-sinceBass / (sampleRate * 0.35))

        // Arpeggio: eighth notes up and down the chord, from a little intensity on.
        val arpGain = ARP_GAIN * ramp(level, 0.15, 0.6)
        if (arpGain > 0) {
            val eighth = samplesPerBeat / 2
            val step = (inBar / eighth).toInt()
            val note = chord[ARP_PATTERN[step % ARP_PATTERN.size]]
            arpPhase = advance(arpPhase, ToneSynth.pitch(note))
            mix += triangle(arpPhase) * arpGain * exp(-(inBar % eighth) / (sampleRate * 0.2))
        }

        // Sparkle: sixteenths two octaves up, only near a full combo.
        val sparkleGain = SPARKLE_GAIN * ramp(level, 0.6, 1.0)
        if (sparkleGain > 0) {
            val sixteenth = samplesPerBeat / 4
            val step = (inBar / sixteenth).toInt()
            val note = chord[(step * 2) % 3] + 12
            sparklePhase = advance(sparklePhase, ToneSynth.pitch(note))
            mix += sin(2 * PI * sparklePhase) * sparkleGain * exp(-(inBar % sixteenth) / (sampleRate * 0.05))
        }
        return mix
    }

    private fun advance(phase: Double, freq: Double): Double {
        val p = phase + freq / sampleRate
        return p - kotlin.math.floor(p)
    }

    private fun triangle(phase: Double) = 1 - 4 * abs(phase - 0.5)

    private fun ramp(x: Double, from: Double, to: Double) = ((x - from) / (to - from)).coerceIn(0.0, 1.0)

    private companion object {
        const val BEATS_PER_BAR = 4
        const val SMOOTHING_SECONDS = 1.5
        const val CHORD_FADE_SECONDS = 0.03
        const val PAD_GAIN = 0.28
        const val BASS_GAIN = 0.22
        const val ARP_GAIN = 0.25
        const val SPARKLE_GAIN = 0.14

        /** Semitones above A4 for each chord's notes: Am, F, C, G (root position, around middle C). */
        val PROGRESSION = listOf(
            intArrayOf(0, 3, 7), // A C E
            intArrayOf(-4, 0, 3), // F A C
            intArrayOf(3, 7, 10), // C E G
            intArrayOf(-2, 2, 5), // G B D
        )

        /** Indexes into the chord: up, up, down, up... */
        val ARP_PATTERN = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2)
    }
}
