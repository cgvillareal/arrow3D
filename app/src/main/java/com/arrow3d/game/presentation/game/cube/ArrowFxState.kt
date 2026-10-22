package com.arrow3d.game.presentation.game.cube

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.model.Level
import com.arrow3d.game.presentation.game.ArrowFx

/**
 * Running animations, keyed by arrow id, timed with the frame clock.
 * Owned by the cube view: it is purely visual and is rebuilt on every new level.
 */
@Stable
class ArrowFxState {

    /** Current frame time (ms). Reading it inside a draw lambda redraws every frame. */
    val frameMillis = mutableLongStateOf(0L)

    class Exit(val start: Long, val durationMs: Long)
    class Block(val start: Long, val blockerId: Int, val bump: Float)

    val exiting = HashMap<Int, Exit>()
    val blocked = HashMap<Int, Block>()
    val chainGlow = HashMap<Int, Long>()
    val restored = HashMap<Int, Long>()

    fun clear() {
        exiting.clear()
        blocked.clear()
        chainGlow.clear()
        restored.clear()
    }

    fun onFx(fx: ArrowFx, level: Level) {
        val now = frameMillis.longValue
        when (fx) {
            is ArrowFx.Released -> {
                val arrow = level.arrow(fx.arrowId)
                var duration = (EXIT_BASE_MS + EXIT_PER_CELL_MS * arrow.length).coerceAtMost(EXIT_MAX_MS)
                if (arrow.kind == ArrowKind.TRAVELER) duration += TRAVELER_EXTRA_MS
                blocked.remove(fx.arrowId)
                exiting[fx.arrowId] = Exit(now, duration)
                fx.newlyFree.forEach { chainGlow[it] = now + duration / 2 }
            }
            is ArrowFx.Blocked -> {
                // Move forward until it nearly touches the blocker, then bounce back.
                val path = level.pathOf(fx.arrowId)
                val gapCells = path.indexOfFirst { level.owner[it] == fx.blockerId }.coerceAtLeast(0)
                val bump = (0.35f + gapCells * 2f - 0.2f).coerceIn(0.35f, 1.4f)
                blocked[fx.arrowId] = Block(now, fx.blockerId, bump)
            }
            is ArrowFx.Restored -> {
                exiting.remove(fx.arrowId)
                restored[fx.arrowId] = now
            }
        }
    }

    companion object {
        const val EXIT_BASE_MS = 230L
        const val EXIT_PER_CELL_MS = 35L
        const val EXIT_MAX_MS = 400L
        const val TRAVELER_EXTRA_MS = 160L
        const val BLOCK_MS = 420L
        const val BLOCK_COLOR_MS = 650L
        const val HALO_MS = 650L
        const val CHAIN_MS = 550L
        const val RESTORE_MS = 260L
    }
}
