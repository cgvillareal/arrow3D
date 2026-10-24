package com.arrow3d.game.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class MusicGeneratorTest {

    private fun rms(samples: ShortArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)

    private fun seconds(gen: MusicGenerator, s: Double) = ShortArray((ToneSynth.SAMPLE_RATE * s).toInt()).also(gen::render)

    @Test
    fun `music is audible, never clips and is deterministic`() {
        val a = seconds(MusicGenerator(), 4.0)
        val b = seconds(MusicGenerator(), 4.0)
        assertArrayEquals(a, b)
        val peak = a.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 2_000 until Short.MAX_VALUE)
        // a full loop at full intensity stays below full scale too
        val loud = MusicGenerator().apply { intensity = 1f }
        val loop = ShortArray(loud.loopSamples.toInt()).also(loud::render)
        assertTrue(loop.maxOf { abs(it.toInt()) } < Short.MAX_VALUE)
    }

    @Test
    fun `intensity swells the music gradually`() {
        // Same stretch of music, calm versus excited.
        val calm = MusicGenerator()
        val excited = MusicGenerator()
        seconds(calm, 3.0)
        seconds(excited, 3.0)
        excited.intensity = 1f
        val calmStart = rms(seconds(calm, 0.1))
        val excitedStart = rms(seconds(excited, 0.1))
        seconds(calm, 2.0)
        seconds(excited, 2.0)
        val calmRms = rms(seconds(calm, 3.0))
        val fullRms = rms(seconds(excited, 3.0))
        assertTrue("full $fullRms vs calm $calmRms", fullRms > calmRms * 1.15)
        // no jump: right after the change it still sounds almost like the calm music
        assertTrue("start $excitedStart vs $calmStart", excitedStart < calmStart * 1.1)
    }

    @Test
    fun `intensity is clamped`() {
        val gen = MusicGenerator()
        gen.intensity = 7f
        assertTrue(gen.intensity == 1f)
        gen.intensity = -1f
        assertTrue(gen.intensity == 0f)
    }
}
