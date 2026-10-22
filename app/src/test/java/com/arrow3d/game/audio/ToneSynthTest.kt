package com.arrow3d.game.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {

    @Test
    fun `pitch follows equal temperament`() {
        assertEquals(440.0, ToneSynth.pitch(0), 1e-9)
        assertEquals(880.0, ToneSynth.pitch(12), 1e-9)
    }

    @Test
    fun `render has the right length, is audible and never clips`() {
        val pcm = ToneSynth.render(listOf(Note(440.0, 0, 100), Note(660.0, 50, 100)), Wave.TRIANGLE)
        assertEquals(150 * ToneSynth.SAMPLE_RATE / 1000, pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak > 10_000)
        assertTrue(peak < Short.MAX_VALUE)
        // starts and ends silent enough not to click
        assertTrue(abs(pcm.first().toInt()) < 500)
        assertTrue(abs(pcm.last().toInt()) < 500)
    }

    @Test
    fun `wav header describes 16-bit mono pcm`() {
        val pcm = ShortArray(100) { (it * 10).toShort() }
        val wav = ToneSynth.wav(pcm)
        assertEquals(44 + 200, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        fun int(at: Int) = (0 until 4).sumOf { (wav[at + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals(ToneSynth.SAMPLE_RATE, int(24))
        assertEquals(200, int(40))
        assertEquals(10, (wav[46].toInt() and 0xFF) or (wav[47].toInt() shl 8))
    }

    @Test
    fun `every sound event has a rendered sound`() {
        val bank = SoundBank.renderAll()
        val events = (1..10).map { SoundEvent.Released(it) } + listOf(
            SoundEvent.Blocked, SoundEvent.TravelerFold, SoundEvent.Chain, SoundEvent.LevelComplete, SoundEvent.OutOfLives,
        )
        events.forEach { assertTrue(SoundBank.nameOf(it), bank.getValue(SoundBank.nameOf(it)).isNotEmpty()) }
        // combos past the top of the scale keep the highest note
        assertEquals("release_8", SoundBank.nameOf(SoundEvent.Released(20)))
    }
}
