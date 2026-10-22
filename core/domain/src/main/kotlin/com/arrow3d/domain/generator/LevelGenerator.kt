package com.arrow3d.domain.generator

import com.arrow3d.domain.model.Arrow
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.domain.rules.exitPath
import com.arrow3d.domain.solver.Solver
import com.arrow3d.domain.surface.CellRef
import com.arrow3d.domain.surface.CubeSurface
import com.arrow3d.domain.surface.Dir2D
import kotlin.math.pow
import kotlin.random.Random

data class GeneratorConfig(
    val gridSize: Int,
    val maxArrowLength: Int,
    val lengthBias: Double = 1.0,
    val pathBias: Double = 0.0,
    val travelerChance: Double = 0.0,
    val maxAttempts: Int = 40,
) {
    init {
        require(gridSize >= 1)
        require(maxArrowLength in 1..gridSize) { "maxArrowLength must be between 1 and gridSize" }
    }

    companion object {
        fun from(difficulty: Difficulty) = GeneratorConfig(
            gridSize = difficulty.gridSize,
            maxArrowLength = difficulty.maxArrowLength,
            lengthBias = difficulty.lengthBias,
            pathBias = difficulty.pathBias,
            travelerChance = difficulty.travelerChance,
        )
    }
}

data class GeneratedLevel(
    val level: Level,
    val seed: Long,
    val depth: Int,
    /** True when the generator had to fall back to single-cell arrows. */
    val usedFallback: Boolean,
)

/**
 * Builds a cube whose faces are completely covered by arrows and that always has a solution.
 *
 * It works backwards: arrows are placed in reverse removal order. When an arrow is placed, every
 * cell on its exit path must still be empty, because anything placed before it will leave after
 * it. Cells that are empty now get filled later by arrows that leave earlier, so the resulting
 * removal order is always valid.
 *
 * Cells are visited from the centre of each face outwards: the straight line from a cell to its
 * nearest edge then only crosses cells that are visited later, so a one-cell arrow always fits.
 * If a random layout gets stuck, the attempt is retried; after [GeneratorConfig.maxAttempts] the
 * generator uses single-cell arrows, which never fail.
 */
class LevelGenerator(private val config: GeneratorConfig) {

    fun generate(seed: Long): GeneratedLevel {
        val surface = CubeSurface(config.gridSize)
        val random = Random(seed)
        repeat(config.maxAttempts) {
            val arrows = tryBuild(surface, random, config.maxArrowLength, config.travelerChance)
            if (arrows != null) {
                val level = Level(surface, arrows)
                val result = Solver.solve(level)
                if (result.solvable) return GeneratedLevel(level, seed, result.depth, usedFallback = false)
            }
        }
        val arrows = checkNotNull(tryBuild(surface, random, maxLength = 1, travelerChance = 0.0)) {
            "Single-cell fallback cannot fail"
        }
        val level = Level(surface, arrows)
        return GeneratedLevel(level, seed, Solver.solve(level).depth, usedFallback = true)
    }

    private data class Candidate(val arrow: Arrow, val weight: Double)

    private fun tryBuild(
        surface: CubeSurface,
        random: Random,
        maxLength: Int,
        travelerChance: Double,
    ): List<Arrow>? {
        val filled = HashSet<CellRef>()
        val arrows = ArrayList<Arrow>()
        // Centre-out order, random among cells at the same distance from the edge.
        val order = surface.allCells()
            .shuffled(random)
            .sortedByDescending { surface.distanceToEdge(it.cell) }

        for (ref in order) {
            if (ref in filled) continue
            val wantTraveler = travelerChance > 0 && random.nextDouble() < travelerChance
            val candidates = candidatesFor(surface, ref, filled, maxLength, arrows.size)
            val travelers = if (wantTraveler) travelerCandidates(surface, candidates, filled) else emptyList()
            val pool = travelers.ifEmpty { candidates }
            if (pool.isEmpty()) return null
            val chosen = pickWeighted(pool, random)
            arrows += chosen.arrow
            filled += chosen.arrow.refs
        }
        // Ids follow placement order; renumber so ids do not leak the solution order.
        val shuffledIds = arrows.indices.shuffled(random)
        return arrows.mapIndexed { i, a -> a.copy(id = shuffledIds[i]) }
    }

    private fun candidatesFor(
        surface: CubeSurface,
        ref: CellRef,
        filled: Set<CellRef>,
        maxLength: Int,
        nextId: Int,
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (dir in Dir2D.entries) {
            for (length in 1..maxLength) {
                // `offset` = position of `ref` inside the arrow, counted from the tail.
                for (offset in 0 until length) {
                    val tail = ref.cell.moved(dir, -offset)
                    val cells = (0 until length).map { tail.moved(dir, it) }
                    if (!cells.all { surface.contains(it) }) continue
                    if (cells.any { CellRef(ref.face, it) in filled }) continue
                    val arrow = Arrow(nextId, ref.face, cells, dir, ArrowKind.COMMON)
                    val path = surface.exitPath(arrow)
                    if (path.any { it in filled }) continue
                    out += Candidate(arrow, weightOf(length, path.size))
                }
            }
        }
        return out
    }

    private fun travelerCandidates(
        surface: CubeSurface,
        candidates: List<Candidate>,
        filled: Set<CellRef>,
    ): List<Candidate> = candidates.mapNotNull { c ->
        val traveler = c.arrow.copy(kind = ArrowKind.TRAVELER)
        val path = surface.exitPath(traveler)
        if (path.any { it in filled }) null else Candidate(traveler, weightOf(traveler.length, path.size))
    }

    private fun weightOf(length: Int, pathLength: Int): Double =
        length.toDouble().pow(config.lengthBias) * (1.0 + pathLength).pow(config.pathBias)

    private fun pickWeighted(pool: List<Candidate>, random: Random): Candidate {
        val total = pool.sumOf { it.weight }
        var r = random.nextDouble() * total
        for (c in pool) {
            r -= c.weight
            if (r <= 0) return c
        }
        return pool.last()
    }

    companion object {
        /** Deterministic seed so "level N of a mode" is always the same puzzle. */
        fun seedFor(difficulty: Difficulty, levelNumber: Int): Long =
            (difficulty.ordinal + 1) * 1_000_003L + levelNumber * 7_919L + 17L
    }
}
