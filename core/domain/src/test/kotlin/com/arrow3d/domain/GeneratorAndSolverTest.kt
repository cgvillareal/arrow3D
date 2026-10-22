package com.arrow3d.domain

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.GeneratorConfig
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.solver.Solver
import com.arrow3d.domain.surface.Cell
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import com.arrow3d.domain.surface.FaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorAndSolverTest {

    @Test
    fun `every difficulty generates full, solvable cubes across many seeds`() {
        Difficulty.entries.forEach { difficulty ->
            val generator = LevelGenerator(GeneratorConfig.from(difficulty))
            repeat(25) { level ->
                val generated = generator.generate(LevelGenerator.seedFor(difficulty, level + 1))
                val lvl = generated.level
                assertTrue("$difficulty #$level must cover the surface", lvl.coversWholeSurface)
                assertTrue("$difficulty #$level must be solvable", Solver.isSolvable(lvl))
                assertTrue(lvl.arrows.all { it.length <= difficulty.maxArrowLength })
            }
        }
    }

    @Test
    fun `generator is deterministic for a seed`() {
        val generator = LevelGenerator(GeneratorConfig.from(Difficulty.MEDIUM))
        val a = generator.generate(42).level.arrows
        val b = generator.generate(42).level.arrows
        assertEquals(a, b)
    }

    @Test
    fun `harder modes produce long arrows and travelers`() {
        val generator = LevelGenerator(GeneratorConfig.from(Difficulty.EXPERT))
        val levels = (1..15).map { generator.generate(LevelGenerator.seedFor(Difficulty.EXPERT, it)) }
        assertTrue(levels.any { g -> g.level.arrows.any { it.length >= 3 } })
        assertTrue(levels.any { g -> g.level.arrows.any { it.kind == ArrowKind.TRAVELER } })
        assertTrue(levels.none { it.usedFallback })
    }

    @Test
    fun `average chain depth grows with difficulty`() {
        val depths = Difficulty.entries.map { d ->
            val generator = LevelGenerator(GeneratorConfig.from(d))
            (1..20).map { generator.generate(LevelGenerator.seedFor(d, it)).depth }.average()
        }
        depths.zipWithNext().forEach { (easier, harder) -> assertTrue("$depths", harder > easier) }
    }

    @Test
    fun `solver detects a deadlock`() {
        // Two arrows on the same row pointing at each other can never leave.
        val surface = CubeSurface(2)
        val level = Level(
            surface,
            listOf(
                Arrow.fromTail(0, FaceId.TOP, Cell(0, 0), Dir2D.U_PLUS, 1),
                Arrow.fromTail(1, FaceId.TOP, Cell(1, 0), Dir2D.U_MINUS, 1),
            ),
        )
        val result = Solver.solve(level)
        assertFalse(result.solvable)
        assertEquals(setOf(0, 1), result.stuck)
    }

    @Test
    fun `solver depth counts waves of the dependency chain`() {
        val surface = CubeSurface(3)
        // 0 blocks 1 which blocks 2: three waves.
        val level = Level(
            surface,
            listOf(
                Arrow.fromTail(0, FaceId.TOP, Cell(2, 0), Dir2D.V_PLUS, 1),
                Arrow.fromTail(1, FaceId.TOP, Cell(1, 0), Dir2D.U_PLUS, 1),
                Arrow.fromTail(2, FaceId.TOP, Cell(0, 0), Dir2D.U_PLUS, 1),
            ),
        )
        val result = Solver.solve(level)
        assertTrue(result.solvable)
        assertEquals(listOf(0, 1, 2), result.order)
        assertEquals(3, result.depth)
    }
}
