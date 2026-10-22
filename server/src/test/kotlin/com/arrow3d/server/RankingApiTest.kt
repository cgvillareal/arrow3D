package com.arrow3d.server

import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.generator.LevelGenerator
import com.arrow3d.domain.modes.DailyChallenge
import com.arrow3d.domain.modes.LevelSpec
import com.arrow3d.domain.solver.Solver
import com.arrow3d.online.ActionType
import com.arrow3d.online.Api
import com.arrow3d.online.ApiErrors
import com.arrow3d.online.ApiResult
import com.arrow3d.online.ErrorResponse
import com.arrow3d.online.LeaderboardResponse
import com.arrow3d.online.PlayerResponse
import com.arrow3d.online.RankingClient
import com.arrow3d.online.RunAction
import com.arrow3d.online.RunResult
import com.arrow3d.online.RunSubmission
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.time.Duration.Companion.hours

class RankingApiTest {

    private val day = 20_004L
    private val dbFile = File.createTempFile("ranking", ".db")
    private val store = SqliteRankingStore(dbFile.path)

    @After
    fun tearDown() {
        store.close()
        dbFile.delete()
    }

    private fun solvedRun(mode: String, difficulty: Difficulty, level: Int, gapMs: Long = 300): RunSubmission {
        val spec = if (mode == "daily") DailyChallenge.spec(day) else LevelSpec.classic(difficulty, level)
        val order = Solver.solve(LevelGenerator(spec.config).generate(spec.seed).level).order
        return RunSubmission(mode, level, day, actions = order.mapIndexed { i, id -> RunAction(ActionType.TAP, 500 + i * gapMs, id) })
    }

    private fun api(limits: RateLimits = RateLimits(), block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { rankingModule(store, today = { day }, limits = limits) }
        block()
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null): HttpResponse =
        client.post(path) {
            contentType(ContentType.Application.Json)
            if (token != null) header("Authorization", "Bearer $token")
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.register(name: String): PlayerResponse {
        val response = postJson(Api.PLAYERS, """{"name":"$name"}""")
        assertEquals(HttpStatusCode.Created, response.status)
        return Api.json.decodeFromString(PlayerResponse.serializer(), response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.submit(token: String, run: RunSubmission): HttpResponse =
        postJson(Api.RUNS, Api.json.encodeToString(RunSubmission.serializer(), run), token)

    private suspend fun ApplicationTestBuilder.board(mode: String, token: String? = null, query: String = ""): LeaderboardResponse {
        val response = client.get("${Api.LEADERBOARDS}/$mode$query") { if (token != null) header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status)
        return Api.json.decodeFromString(LeaderboardResponse.serializer(), response.bodyAsText())
    }

    @Test
    fun `players register with a valid name and get a token`() = api {
        val player = register("  Gabi 78 ")
        assertEquals("Gabi 78", player.name)
        assertNotNull(player.token)
        val bad = postJson(Api.PLAYERS, """{"name":"x"}""")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(ApiErrors.INVALID_NAME, Api.json.decodeFromString(ErrorResponse.serializer(), bad.bodyAsText()).error)
    }

    @Test
    fun `a verified run enters the board with the server's score`() = api {
        val ana = register("Ana")
        val run = solvedRun("easy", Difficulty.EASY, 2)
        val response = submit(ana.token!!, run)
        assertEquals(HttpStatusCode.OK, response.status)
        val result = Api.json.decodeFromString(RunResult.serializer(), response.bodyAsText())
        assertEquals(1, result.rank)
        assertTrue(result.improved)

        val board = board("easy", ana.token)
        assertEquals(listOf("Ana"), board.entries.map { it.name })
        assertEquals(result.score, board.entries.single().score)
        assertEquals(1, board.me?.rank)
        assertNull(board.day)
    }

    @Test
    fun `only each player's best counts and ranks follow the score`() = api {
        val ana = register("Ana")
        val beto = register("Beto")
        val fast = solvedRun("easy", Difficulty.EASY, 2) // full combo
        val slow = solvedRun("easy", Difficulty.EASY, 2, gapMs = 3_000) // no combo
        submit(beto.token!!, slow)
        submit(ana.token!!, fast)
        val worse = Api.json.decodeFromString(RunResult.serializer(), submit(ana.token!!, slow).bodyAsText())
        assertFalse(worse.improved)
        assertEquals(1, worse.rank)

        val board = board("easy", beto.token)
        assertEquals(listOf("Ana", "Beto"), board.entries.map { it.name })
        assertEquals(listOf(1, 2), board.entries.map { it.rank })
        assertEquals(2, board.me?.rank)
    }

    @Test
    fun `cheated or unauthenticated runs are refused`() = api {
        val ana = register("Ana")
        val run = solvedRun("easy", Difficulty.EASY, 2)
        assertEquals(HttpStatusCode.Unauthorized, submit("not-a-token", run).status)
        val unsolved = run.copy(actions = run.actions.dropLast(1))
        assertEquals(HttpStatusCode.UnprocessableEntity, submit(ana.token!!, unsolved).status)
        val otherLevel = run.copy(level = 3)
        assertEquals(HttpStatusCode.UnprocessableEntity, submit(ana.token!!, otherLevel).status)
        assertEquals(HttpStatusCode.NotFound, submit(ana.token!!, run.copy(mode = "chess")).status)
        assertEquals(HttpStatusCode.BadRequest, postJson(Api.RUNS, "{not json", ana.token).status)
        assertTrue(board("easy").entries.isEmpty())
    }

    @Test
    fun `the daily board is per day`() = api {
        val ana = register("Ana")
        assertEquals(HttpStatusCode.OK, submit(ana.token!!, solvedRun("daily", Difficulty.MEDIUM, 1)).status)
        assertEquals(1, board("daily", query = "?day=$day").entries.size)
        assertEquals(day, board("daily", query = "?day=$day").day)
        assertTrue(board("daily", query = "?day=${day - 1}").entries.isEmpty())
        // defaults to the server's day
        assertEquals(1, board("daily").entries.size)
    }

    @Test
    fun `players can rename themselves`() = api {
        val ana = register("Ana")
        val response = client.post(Api.ME) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${ana.token}")
            header("X-HTTP-Method-Override", "PATCH")
            setBody("""{"name":"Ana Laura"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ana Laura", Api.json.decodeFromString(PlayerResponse.serializer(), response.bodyAsText()).name)
    }

    @Test
    fun `registrations are rate limited`() = api(RateLimits(registrations = 2, registrationPeriod = 1.hours)) {
        register("Uno")
        register("Dos")
        val third = postJson(Api.PLAYERS, """{"name":"Tres"}""")
        assertEquals(HttpStatusCode.TooManyRequests, third.status)
        assertEquals(ApiErrors.RATE_LIMITED, Api.json.decodeFromString(ErrorResponse.serializer(), third.bodyAsText()).error)
    }

    @Test
    fun `the app's client talks to a real server end to end`() {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(Netty, port = port, host = "127.0.0.1") { rankingModule(store, today = { day }) }.start(wait = false)
        try {
            runBlocking {
                val client = RankingClient("http://127.0.0.1:$port/")
                val player = (client.register("Ana") as ApiResult.Ok).value
                val renamed = client.rename(player.token!!, "Ana Laura")
                assertEquals("Ana Laura", (renamed as ApiResult.Ok).value.name)

                val result = client.submit(player.token!!, solvedRun("hard", Difficulty.HARD, 5))
                assertEquals(1, (result as ApiResult.Ok).value.rank)
                val board = (client.leaderboard("hard", token = player.token) as ApiResult.Ok).value
                assertEquals("Ana Laura", board.me?.name)

                val refused = client.submit(player.token!!, solvedRun("hard", Difficulty.HARD, 5).let { it.copy(actions = it.actions.take(3)) })
                assertEquals(ApiErrors.INVALID_RUN, (refused as ApiResult.Failed).error)
                assertEquals(422, refused.status)

                val offline = RankingClient("http://127.0.0.1:1").leaderboard("easy")
                assertTrue(offline is ApiResult.Unreachable)
            }
        } finally {
            server.stop(100, 500)
        }
    }
}
