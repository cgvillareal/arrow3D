package com.arrow3d.game.presentation.game.cube

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arrow3d.domain.model.Level
import com.arrow3d.game.R
import com.arrow3d.game.render.ArrowHit
import com.arrow3d.game.render.CubeProjector
import com.arrow3d.game.render.HitTest
import com.arrow3d.game.render.Point2
import com.arrow3d.game.ui.theme.ArrowPalette
import kotlinx.coroutines.isActive

/**
 * The 3D cube. Gestures (design doc, section 2):
 * - tap an arrow: try to remove it (the closest visible arrow to the finger wins);
 * - drag with one finger: rotate, with inertia;
 * - pinch: zoom within limits;
 * - double tap on the background: recentre the camera.
 * The arrow under the finger is highlighted on press and acted on when the finger lifts.
 */
@Composable
fun CubeView(
    level: Level,
    remaining: Set<Int>,
    hintArrowId: Int?,
    camera: CubeCameraState,
    fx: ArrowFxState,
    enabled: Boolean,
    onArrowTap: (ArrowHit) -> Unit,
    modifier: Modifier = Modifier,
    palette: ArrowPalette = ArrowPalette.Standard,
) {
    val renderer = remember { CubeRenderer() }
    var pressedArrowId by remember { mutableStateOf<Int?>(null) }
    val currentLevel by rememberUpdatedState(level)
    val currentRemaining by rememberUpdatedState(remaining)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onArrowTap)
    val description = stringResource(R.string.cd_cube)

    // Frame clock for animations: the draw phase reads it, so only drawing runs each frame.
    LaunchedEffect(fx) {
        while (isActive) {
            withFrameMillis { fx.frameMillis.longValue = it }
        }
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(camera) {
                var lastBackgroundTap = 0L
                var lastBackgroundPos = Offset.Zero
                val doubleTapSlop = 48.dp.toPx()

                fun projector() = CubeProjector(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    currentLevel.surface.size,
                    camera.angles,
                )

                fun hitAt(position: Offset): ArrowHit? =
                    if (!currentEnabled) {
                        null
                    } else {
                        HitTest.find(currentLevel, currentRemaining, projector(), Point2(position.x, position.y))
                    }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    camera.stop()
                    pressedArrowId = hitAt(down.position)?.arrowId

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dragging = false
                    var multiTouch = false
                    var travelled = 0f
                    var upPosition = down.position
                    var upTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) {
                            event.changes.firstOrNull()?.let {
                                upPosition = it.position
                                upTime = it.uptimeMillis
                            }
                            break
                        }
                        if (active.size >= 2) {
                            multiTouch = true
                            pressedArrowId = null
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) camera.zoomBy(zoom)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = active.first()
                        val delta = change.positionChange()
                        travelled += delta.getDistance()
                        if (!dragging && travelled > viewConfiguration.touchSlop) {
                            dragging = true
                            pressedArrowId = null
                        }
                        if (dragging && !multiTouch) {
                            camera.rotateBy(delta.x, delta.y)
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                        }
                    }
                    pressedArrowId = null

                    when {
                        multiTouch -> Unit
                        dragging -> {
                            val velocity = tracker.calculateVelocity()
                            camera.fling(velocity.x, velocity.y)
                        }
                        else -> {
                            val hit = hitAt(upPosition)
                            if (hit != null) {
                                currentOnTap(hit)
                                lastBackgroundTap = 0L
                            } else {
                                val isDoubleTap = upTime - lastBackgroundTap < viewConfiguration.doubleTapTimeoutMillis &&
                                    (upPosition - lastBackgroundPos).getDistance() < doubleTapSlop
                                if (isDoubleTap) {
                                    camera.recenter()
                                    lastBackgroundTap = 0L
                                } else {
                                    lastBackgroundTap = upTime
                                    lastBackgroundPos = upPosition
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val projector = CubeProjector(size.width, size.height, level.surface.size, camera.angles)
        with(renderer) {
            draw(
                level = level,
                remaining = remaining,
                projector = projector,
                fx = fx,
                now = fx.frameMillis.longValue,
                hintArrowId = hintArrowId,
                pressedArrowId = pressedArrowId,
                palette = palette,
            )
        }
    }
}
