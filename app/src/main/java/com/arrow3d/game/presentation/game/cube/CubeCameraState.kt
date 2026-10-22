package com.arrow3d.game.presentation.game.cube

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.arrow3d.game.render.CameraAngles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Compose-owned camera state (like a LazyListState): yaw, pitch and zoom of the cube, with
 * inertia after a drag, a bounded zoom and an animated recentre.
 */
@Stable
class CubeCameraState(private val scope: CoroutineScope) {

    var yaw by mutableFloatStateOf(CameraAngles.DEFAULT_YAW)
        private set
    var pitch by mutableFloatStateOf(CameraAngles.DEFAULT_PITCH)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set

    val angles: CameraAngles get() = CameraAngles(yaw, pitch, zoom)

    private var motion: Job? = null

    /** Called when a finger touches the screen: stops inertia and any running animation. */
    fun stop() {
        motion?.cancel()
        motion = null
    }

    fun rotateBy(dxPx: Float, dyPx: Float) {
        yaw += dxPx * DEGREES_PER_PX
        pitch = (pitch + dyPx * DEGREES_PER_PX).coerceIn(CameraAngles.MIN_PITCH, CameraAngles.MAX_PITCH)
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(CameraAngles.MIN_ZOOM, CameraAngles.MAX_ZOOM)
    }

    /** Keeps rotating after the finger lifts, slowing down smoothly. Velocity in px/s. */
    fun fling(velocityX: Float, velocityY: Float) {
        stop()
        motion = scope.launch {
            var vx = velocityX.coerceIn(-MAX_FLING, MAX_FLING)
            var vy = velocityY.coerceIn(-MAX_FLING, MAX_FLING)
            var last = withFrameNanos { it }
            while (isActive && abs(vx) + abs(vy) > MIN_FLING) {
                withFrameNanos { now ->
                    val dt = (now - last) / 1_000_000_000f
                    last = now
                    rotateBy(vx * dt, vy * dt)
                    val decay = exp(-FRICTION * dt)
                    vx *= decay
                    vy *= decay
                }
            }
        }
    }

    /** Double tap on the background: back to the default three-face view. */
    fun recenter() {
        stop()
        // Pick the equivalent target yaw closest to the current one, so it never spins around.
        val turns = ((yaw - CameraAngles.DEFAULT_YAW) / 360f).roundToInt()
        val targetYaw = CameraAngles.DEFAULT_YAW + turns * 360f
        animateTo(targetYaw, CameraAngles.DEFAULT_PITCH, 1f, RECENTER_MS)
    }

    /** Closing animation when the last arrow leaves: a full turn around the vertical axis. */
    fun celebrate() {
        stop()
        animateTo(yaw + 360f, CameraAngles.DEFAULT_PITCH, zoom, CELEBRATE_MS)
    }

    /** New level or restart: snap back to the default view. */
    fun reset() {
        stop()
        yaw = CameraAngles.DEFAULT_YAW
        pitch = CameraAngles.DEFAULT_PITCH
        zoom = 1f
    }

    private fun animateTo(targetYaw: Float, targetPitch: Float, targetZoom: Float, durationMs: Int) {
        val startYaw = yaw
        val startPitch = pitch
        val startZoom = zoom
        motion = scope.launch {
            animate(0f, 1f, animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) { t, _ ->
                yaw = startYaw + (targetYaw - startYaw) * t
                pitch = startPitch + (targetPitch - startPitch) * t
                zoom = startZoom + (targetZoom - startZoom) * t
            }
        }
    }

    private companion object {
        const val DEGREES_PER_PX = 0.35f
        const val FRICTION = 4.5f
        const val MIN_FLING = 30f
        const val MAX_FLING = 4000f
        const val RECENTER_MS = 420
        const val CELEBRATE_MS = 1100
    }
}

@Composable
fun rememberCubeCameraState(): CubeCameraState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CubeCameraState(scope) }
}
