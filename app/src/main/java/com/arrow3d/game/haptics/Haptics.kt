package com.arrow3d.game.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** One haptic pattern per game event (design doc, section 4). */
sealed interface HapticEvent {
    data class Released(val longArrow: Boolean) : HapticEvent
    data object Blocked : HapticEvent
    data object TravelerFold : HapticEvent
    data class Chain(val count: Int) : HapticEvent
    data object LevelComplete : HapticEvent
    data object OutOfLives : HapticEvent
}

interface IHaptics {
    fun play(event: HapticEvent)
}

class AndroidHaptics(context: Context) : IHaptics {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    override fun play(event: HapticEvent) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = composed(v, event) ?: waveform(event, v.hasAmplitudeControl())
        v.vibrate(effect)
    }

    /** Richer primitives where supported (API 30+), otherwise null to fall back to waveforms. */
    private fun composed(v: Vibrator, event: HapticEvent): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (event) {
            is HapticEvent.Chain -> {
                if (!v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) return null
                val composition = VibrationEffect.startComposition()
                val steps = event.count.coerceIn(2, 5)
                for (i in 0 until steps) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f + 0.7f * i / (steps - 1), 40)
                }
                composition.compose()
            }
            HapticEvent.LevelComplete -> {
                if (!v.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                    )
                ) {
                    return null
                }
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 30)
                    .compose()
            }
            else -> null
        }
    }

    private fun waveform(event: HapticEvent, amplitude: Boolean): VibrationEffect {
        fun amp(value: Int) = if (amplitude) value else VibrationEffect.DEFAULT_AMPLITUDE
        return when (event) {
            is HapticEvent.Released ->
                if (event.longArrow) VibrationEffect.createOneShot(35, amp(170)) else VibrationEffect.createOneShot(22, amp(120))
            HapticEvent.Blocked ->
                VibrationEffect.createWaveform(longArrayOf(0, 14, 40, 14), intArrayOf(0, amp(255), 0, amp(255)), -1)
            HapticEvent.TravelerFold -> VibrationEffect.createOneShot(10, amp(90))
            is HapticEvent.Chain ->
                VibrationEffect.createWaveform(longArrayOf(0, 15, 30, 15, 30, 15), intArrayOf(0, amp(80), 0, amp(150), 0, amp(220)), -1)
            HapticEvent.LevelComplete ->
                VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60, 40, 110), intArrayOf(0, amp(100), 0, amp(170), 0, amp(255)), -1)
            HapticEvent.OutOfLives -> VibrationEffect.createOneShot(380, amp(200))
        }
    }
}
