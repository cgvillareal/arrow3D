package com.arrow3d.domain

import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.rules.exitPath
import com.arrow3d.domain.rules.route
import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId
import com.arrow3d.domain.surface.Vec3i
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceTest {

    private val surface = CubeSurface(3)

    @Test
    fun `cube has six faces with size squared cells each`() {
        assertEquals(6 * 9, surface.allCells().size)
        assertEquals(54, surface.allCells().toSet().size)
    }

    @Test
    fun `cell centres lie on their face plane`() {
        surface.allCells().forEach { ref ->
            val frame = surface.frame(ref.face)
            val c = surface.center(ref)
            // distance from the face plane along the normal must be zero
            assertEquals(0, (c - frame.origin) dot frame.normal)
        }
    }

    @Test
    fun `step stays on the face and returns null at the edge`() {
        val ref = CellRef(FaceId.TOP, Cell(2, 1))
        assertEquals(CellRef(FaceId.TOP, Cell(1, 1)), surface.step(ref, Dir2D.U_MINUS))
        assertNull(surface.step(ref, Dir2D.U_PLUS))
    }

    @Test
    fun `fold from top moving plus X lands on the right face moving down`() {
        val (entry, dir) = surface.fold(CellRef(FaceId.TOP, Cell(2, 1)), Dir2D.U_PLUS)
        assertEquals(FaceId.RIGHT, entry.face)
        assertEquals(-Vec3i.Y, surface.dir3(entry.face, dir))
        // entry cell is in the top row of the right face, at the same depth (z) as the start cell
        assertEquals(surface.center(CellRef(FaceId.TOP, Cell(2, 1))).z, surface.center(entry).z)
        assertEquals(2, entry.cell.v)
    }

    @Test
    fun `every fold is adjacent and folding back returns to the start`() {
        surface.allCells().forEach { ref ->
            Dir2D.entries.forEach { dir ->
                if (surface.step(ref, dir) == null) {
                    val (entry, newDir) = surface.fold(ref, dir)
                    // the two cells touch the same edge point
                    val edge = surface.center(ref) + surface.dir3(ref.face, dir)
                    assertEquals(edge, surface.center(entry) - surface.dir3(entry.face, newDir))
                    val (back, _) = surface.fold(entry, newDir.opposite)
                    assertEquals(ref, back)
                }
            }
        }
    }

    @Test
    fun `common arrow path covers cells up to its own edge only`() {
        val arrow = Arrow.fromTail(0, FaceId.FRONT, Cell(0, 1), Dir2D.U_PLUS, length = 1)
        assertEquals(
            listOf(CellRef(FaceId.FRONT, Cell(1, 1)), CellRef(FaceId.FRONT, Cell(2, 1))),
            surface.exitPath(arrow),
        )
    }

    @Test
    fun `traveler path continues across the whole neighbour face`() {
        val arrow = Arrow.fromTail(0, FaceId.TOP, Cell(1, 1), Dir2D.U_PLUS, length = 1, kind = ArrowKind.TRAVELER)
        val path = surface.exitPath(arrow)
        assertEquals(1 + 3, path.size)
        assertEquals(FaceId.TOP, path.first().face)
        assertTrue(path.drop(1).all { it.face == FaceId.RIGHT })
    }

    @Test
    fun `bent arrows must touch, not cross themselves and point along the last step`() {
        val ok = Arrow.fromPath(0, FaceId.TOP, listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)))
        assertEquals(Dir2D.U_PLUS, ok.dir)
        assertEquals(Dir2D.U_PLUS, ok.tailDir)
        assertEquals(2, ok.bends)
        assertThrows(IllegalArgumentException::class.java) {
            Arrow(1, FaceId.TOP, listOf(Cell(0, 0), Cell(2, 0)), Dir2D.U_PLUS) // gap
        }
        assertThrows(IllegalArgumentException::class.java) {
            Arrow(1, FaceId.TOP, listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)), Dir2D.U_PLUS) // head points sideways
        }
        assertThrows(IllegalArgumentException::class.java) {
            Arrow.fromPath(1, FaceId.TOP, listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(0, 1), Cell(0, 0)))
        }
    }

    @Test
    fun `bent route turns at cell centres and keeps the body length`() {
        // tail (0,0) -> (1,0) -> (1,1) -> head (1,2) going V_PLUS, then out through the far edge.
        val arrow = Arrow.fromPath(0, FaceId.TOP, listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(1, 2)))
        val route = surface.route(arrow)
        // back of tail, the turn, the edge, off the surface
        assertEquals(4, route.points.size)
        assertEquals(surface.center(CellRef(FaceId.TOP, Cell(1, 0))), route.points[1])
        assertEquals(route.points.size - 1, route.segmentNormals.size)
        assertEquals(8, route.bodyLength)
        // 2 * 4 - 1 along the body, + 1 to the edge
        assertEquals(8, route.surfaceLength)
        assertTrue(surface.exitPath(arrow).isEmpty())
    }

    @Test
    fun `route length matches cells crossed`() {
        val arrow = Arrow.fromTail(0, FaceId.TOP, Cell(0, 0), Dir2D.U_PLUS, length = 2)
        val route = surface.route(arrow)
        // from back of tail to the face edge: 3 cells = 6 doubled units
        assertEquals(6, route.surfaceLength)
        assertEquals(4, route.bodyLength)
        assertEquals(3, route.points.size)

        val traveler = arrow.copy(kind = ArrowKind.TRAVELER)
        val tr = surface.route(traveler)
        assertEquals(6 + 6, tr.surfaceLength)
        assertEquals(4, tr.points.size)
        assertEquals(Vec3i.Y, tr.segmentNormals[0])
        assertEquals(Vec3i.X, tr.segmentNormals[1])
    }
}
