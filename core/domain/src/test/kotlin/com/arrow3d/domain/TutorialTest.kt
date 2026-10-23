package com.arrow3d.domain

import com.arrow3d.domain.engine.PuzzleEngine
import com.arrow3d.domain.engine.TapOutcome
import com.arrow3d.domain.model.ArrowKind
import com.arrow3d.domain.modes.LessonTopic
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.Tutorial
import com.arrow3d.domain.solver.Solver
import com.arrow3d.domain.surface.FaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    @Test
    fun `every lesson is solvable and only uses the faces the camera shows first`() {
        val visible = setOf(FaceId.TOP, FaceId.FRONT, FaceId.RIGHT)
        Tutorial.lessons.forEach { lesson ->
            assertTrue("${lesson.topic} must be solvable", Solver.isSolvable(lesson.level))
            val faces = lesson.level.arrows.map { it.face } + lesson.level.arrows.flatMap { a -> lesson.level.pathOf(a.id).map { it.face } }
            assertTrue("${lesson.topic} uses a hidden face", faces.all { it in visible })
        }
        assertEquals(LessonTopic.entries.toList(), Tutorial.lessons.map { it.topic })
    }

    @Test
    fun `each lesson shows its idea`() {
        fun lesson(topic: LessonTopic) = Tutorial.lessons.first { it.topic == topic }.level
        lesson(LessonTopic.TAP).let { assertEquals(it.ids, it.freeArrows(it.ids)) }
        lesson(LessonTopic.BLOCKING).let { assertEquals(1, it.blockerOf(0, it.ids)) }
        lesson(LessonTopic.CHAIN).let { assertEquals(3, Solver.solve(it).depth) }
        lesson(LessonTopic.LONG).let { l -> assertTrue(l.arrows.any { it.length == 3 }) }
        lesson(LessonTopic.BENT).let { l ->
            assertTrue(l.arrow(0).isBent)
            assertEquals(0, l.blockerOf(1, l.ids)) // blocked by the snake's tail...
            assertTrue(l.isFree(1, l.ids - 0)) // ...until the snake leaves
        }
        lesson(LessonTopic.TRAVELER).let { l ->
            assertEquals(ArrowKind.TRAVELER, l.arrow(0).kind)
            assertEquals(1, l.blockerOf(0, l.ids))
        }
        lesson(LessonTopic.COMBO).let { l ->
            var state = PuzzleEngine.start(l, null, null, null)
            Solver.solve(l).order.forEachIndexed { i, id ->
                val r = PuzzleEngine.tap(state, id, nowMillis = i * 500L)
                assertTrue(r.outcome is TapOutcome.Released)
                state = r.state
            }
            assertEquals(l.arrows.size, state.bestCombo)
        }
    }

    @Test
    fun `tutorial restarts once finished and is not a ranked mode`() {
        assertEquals(0, Tutorial.startIndex(0))
        assertEquals(3, Tutorial.startIndex(3))
        assertEquals(0, Tutorial.startIndex(Tutorial.size))
        assertEquals(PlayMode.Tutorial, PlayMode.fromKey("tutorial"))
        assertFalse(PlayMode.Tutorial in PlayMode.all)
    }
}
