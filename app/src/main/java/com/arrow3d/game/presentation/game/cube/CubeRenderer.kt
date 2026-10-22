package com.arrow3d.game.presentation.game.cube

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.surface.Vec3i
import com.arrow3d.game.render.ArrowGeometry
import com.arrow3d.game.render.CubeProjector
import com.arrow3d.game.render.Point2
import com.arrow3d.game.render.ScreenPolygon
import com.arrow3d.game.render.Vec3f
import com.arrow3d.game.ui.theme.GameColors
import kotlin.math.PI
import kotlin.math.sin

/** Draws faces, grid and arrows, applying the running animations. */
class CubeRenderer {

    private val path = Path()

    fun DrawScope.draw(
        level: Level,
        remaining: Set<Int>,
        projector: CubeProjector,
        fx: ArrowFxState,
        now: Long,
        hintArrowId: Int?,
        pressedArrowId: Int?,
    ) {
        drawFaces(level, projector)

        val halos = ArrayList<Pair<Point2, Float>>()
        // Arrows at rest first, then animated ones on top.
        val resting = remaining.filter { it !in fx.exiting }
        for (id in resting) {
            drawArrow(level, id, projector, fx, now, hintArrowId, pressedArrowId, halos)
        }
        val finished = ArrayList<Int>()
        for ((id, exit) in fx.exiting) {
            val t = ((now - exit.start).toFloat() / exit.durationMs).coerceIn(0f, 1f)
            if (t >= 1f) {
                finished += id
                continue
            }
            val route = level.routeOf(id)
            val offset = easeIn(t) * ArrowGeometry.exitTravel(route)
            val color = lerp(GameColors.ArrowExitStart, GameColors.ArrowExitEnd, t)
            val alpha = 1f - smoothStep(0.6f, 1f, t)
            val shape = ArrowGeometry.shape(route, offset, projector)
            shape.polygons.forEach { fillPolygon(it, color.copy(alpha = alpha)) }
        }
        finished.forEach { fx.exiting.remove(it) }

        halos.forEach { (center, progress) ->
            val radius = projector.cellPixels * (0.35f + 0.55f * progress)
            drawCircle(
                color = GameColors.ArrowBlocked.copy(alpha = 1f - progress),
                radius = radius,
                center = Offset(center.x, center.y),
                style = Stroke(width = projector.cellPixels * 0.08f),
            )
        }
    }

    private fun DrawScope.drawFaces(level: Level, projector: CubeProjector) {
        val surface = level.surface
        val n2 = surface.size * 2
        for (face in surface.faces) {
            val centre = Vec3f.of(face.origin + face.uAxis * surface.size + face.vAxis * surface.size)
            if (!projector.isFacing(face.normal, centre)) continue
            val corners = listOf(
                face.origin,
                face.origin + face.uAxis * n2,
                face.origin + face.uAxis * n2 + face.vAxis * n2,
                face.origin + face.vAxis * n2,
            ).map { projector.projectSurface(it) }
            val shade = 0.55f + 0.45f * projector.light(face.normal)
            fillPolygon(ScreenPolygon(corners), shaded(GameColors.Face, shade))

            val gridWidth = (projector.cellPixels * 0.025f).coerceAtLeast(1f)
            val gridColor = GameColors.Grid.copy(alpha = 0.55f)
            for (i in 1 until surface.size) {
                line(face.origin + face.uAxis * (2 * i), face.origin + face.uAxis * (2 * i) + face.vAxis * n2, projector, gridColor, gridWidth)
                line(face.origin + face.vAxis * (2 * i), face.origin + face.vAxis * (2 * i) + face.uAxis * n2, projector, gridColor, gridWidth)
            }
        }
    }

    private fun DrawScope.drawArrow(
        level: Level,
        id: Int,
        projector: CubeProjector,
        fx: ArrowFxState,
        now: Long,
        hintArrowId: Int?,
        pressedArrowId: Int?,
        halos: MutableList<Pair<Point2, Float>>,
    ) {
        val arrow = level.arrow(id)
        val route = level.routeOf(id)
        var color = if (arrow.kind == ArrowKind.TRAVELER) GameColors.ArrowTraveler else GameColors.ArrowRest
        var offset = 0f
        var alpha = 1f
        var widthScale = 1f

        fx.blocked[id]?.let { block ->
            val elapsed = now - block.start
            if (elapsed > ArrowFxState.BLOCK_COLOR_MS) {
                fx.blocked.remove(id)
            } else {
                val t = (elapsed.toFloat() / ArrowFxState.BLOCK_MS).coerceIn(0f, 1f)
                offset = block.bump * sin(PI.toFloat() * t)
                val fade = smoothStep(0.7f, 1f, elapsed.toFloat() / ArrowFxState.BLOCK_COLOR_MS)
                color = lerp(GameColors.ArrowBlocked, color, fade)
                if (elapsed < ArrowFxState.HALO_MS) {
                    level.routeOf(block.blockerId).let { r ->
                        ArrowGeometry.shape(r, 0f, projector).tip?.let { tip ->
                            halos += tip to elapsed.toFloat() / ArrowFxState.HALO_MS
                        }
                    }
                }
            }
        }
        fx.chainGlow[id]?.let { start ->
            val t = (now - start).toFloat() / ArrowFxState.CHAIN_MS
            when {
                t > 1f -> fx.chainGlow.remove(id)
                t > 0f -> color = lerp(color, Color.White, 0.5f * sin(PI.toFloat() * t))
            }
        }
        fx.restored[id]?.let { start ->
            val t = (now - start).toFloat() / ArrowFxState.RESTORE_MS
            if (t >= 1f) fx.restored.remove(id) else alpha = t.coerceIn(0f, 1f)
        }
        if (id == hintArrowId) {
            val pulse = 0.5f + 0.5f * sin(now / 140.0).toFloat()
            color = lerp(color, Color.White, 0.2f + 0.3f * pulse)
            widthScale = 1.1f
        }
        if (id == pressedArrowId) {
            color = lerp(color, Color.White, 0.25f)
            widthScale = 1.15f
        }

        val shape = ArrowGeometry.shape(route, offset, projector, widthScale)
        shape.polygons.forEach { fillPolygon(it, color.copy(alpha = alpha)) }
    }

    private fun DrawScope.fillPolygon(polygon: ScreenPolygon, color: Color) {
        val pts = polygon.points
        if (pts.size < 3) return
        path.rewind()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        drawPath(path, color)
    }

    private fun DrawScope.line(a: Vec3i, b: Vec3i, projector: CubeProjector, color: Color, width: Float) {
        val p = projector.projectSurface(a)
        val q = projector.projectSurface(b)
        drawLine(color, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = width)
    }

    private fun shaded(base: Color, factor: Float): Color {
        val k = factor * 1.25f
        return Color(
            red = (base.red * k).coerceIn(0f, 1f),
            green = (base.green * k).coerceIn(0f, 1f),
            blue = (base.blue * k).coerceIn(0f, 1f),
        )
    }

    private fun easeIn(t: Float) = t * t

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
