package com.arrow3d.game.render

import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.rules.route
import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderMathTest {

    private val surface = CubeSurface(3)
    private val projector = CubeProjector(1080f, 1920f, 3, CameraAngles())

    @Test
    fun `default camera shows top, front and right faces only`() {
        val visible = surface.faces.filter { f ->
            projector.isFacing(f.normal, Vec3f.of(surface.center(CellRef(f.id, Cell(1, 1)))))
        }.map { it.id }.toSet()
        assertEquals(setOf(FaceId.TOP, FaceId.FRONT, FaceId.RIGHT), visible)
    }

    @Test
    fun `cube projects inside the screen`() {
        surface.allCells().forEach { ref ->
            val p = projector.projectSurface(surface.center(ref))
            assertTrue(p.x in 0f..1080f && p.y in 0f..1920f)
        }
    }

    @Test
    fun `arrow at rest has shaft and head on a visible face`() {
        val arrow = Arrow.fromTail(0, FaceId.TOP, Cell(0, 1), Dir2D.U_PLUS, 2)
        val shape = ArrowGeometry.shape(surface.route(arrow), 0f, projector)
        assertEquals(2, shape.polygons.size) // one shaft quad + head triangle
        assertNotNull(shape.tip)
    }

    @Test
    fun `arrow on a hidden face draws nothing`() {
        val arrow = Arrow.fromTail(0, FaceId.BOTTOM, Cell(0, 1), Dir2D.U_PLUS, 2)
        val shape = ArrowGeometry.shape(surface.route(arrow), 0f, projector)
        assertTrue(shape.polygons.isEmpty())
        assertNull(shape.tip)
    }

    @Test
    fun `traveler folding over the edge is drawn on both faces`() {
        val arrow = Arrow.fromTail(0, FaceId.TOP, Cell(1, 1), Dir2D.U_PLUS, 2, ArrowKind.TRAVELER)
        val route = surface.route(arrow)
        // move the body so it straddles the TOP/RIGHT edge
        val shape = ArrowGeometry.shape(route, 2.5f, projector)
        assertTrue("expected a quad on each face plus head, got ${shape.polygons.size}", shape.polygons.size >= 3)
        assertTrue(ArrowGeometry.exitTravel(route) > route.surfaceLength - route.bodyLength)
    }

    @Test
    fun `hit test picks the touched arrow and ignores far touches`() {
        val a = Arrow.fromTail(0, FaceId.TOP, Cell(0, 0), Dir2D.U_PLUS, 3)
        val b = Arrow.fromTail(1, FaceId.TOP, Cell(0, 2), Dir2D.U_PLUS, 3)
        val level = Level(surface, listOf(a, b))
        val onA = projector.projectSurface(surface.center(CellRef(FaceId.TOP, Cell(1, 0))))
        val hit = HitTest.find(level, setOf(0, 1), projector, onA)
        assertEquals(0, hit?.arrowId)
        assertFalse(hit!!.ambiguous)
        assertNull(HitTest.find(level, setOf(0, 1), projector, Point2(5f, 5f)))
        // removed arrows cannot be hit
        assertTrue(HitTest.find(level, setOf(1), projector, onA)?.arrowId != 0)
    }
}
