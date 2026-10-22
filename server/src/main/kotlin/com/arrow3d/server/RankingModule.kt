package com.arrow3d.server

import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.online.Api
import com.arrow3d.online.ApiErrors
import com.arrow3d.online.ErrorResponse
import com.arrow3d.online.LeaderboardResponse
import com.arrow3d.online.NameRules
import com.arrow3d.online.PlayerResponse
import com.arrow3d.online.RegisterRequest
import com.arrow3d.online.RenameRequest
import com.arrow3d.online.RunResult
import com.arrow3d.online.RunSubmission
import com.arrow3d.online.RunVerifier
import com.arrow3d.online.Verdict
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.methodoverride.XHttpMethodOverride
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Request limits per client address. */
data class RateLimits(
    val registrations: Int = 10,
    val registrationPeriod: Duration = 1.hours,
    val runs: Int = 60,
    val runPeriod: Duration = 1.minutes,
)

/**
 * The ranking API. [today] is the server's epoch day (UTC) used to check the daily challenge.
 * [trustProxy] reads the client address from X-Forwarded-For; turn it on only behind a proxy
 * that sets it, otherwise anyone could pick their own rate-limit bucket.
 */
fun Application.rankingModule(
    store: RankingStore,
    today: () -> Long = { LocalDate.now(ZoneOffset.UTC).toEpochDay() },
    limits: RateLimits = RateLimits(),
    trustProxy: Boolean = false,
) {
    install(ContentNegotiation) { json(Api.json) }
    install(XHttpMethodOverride)
    install(CallLogging)
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ApiErrors.BAD_REQUEST, cause.message ?: "bad request"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorResponse(ApiErrors.RATE_LIMITED, "too many requests, try again later"))
        }
    }

    fun ApplicationCall.clientKey(): String =
        (if (trustProxy) request.header("X-Forwarded-For")?.substringBefore(',')?.trim() else null)
            ?: request.origin.remoteHost

    install(RateLimit) {
        register(REGISTER_LIMIT) {
            rateLimiter(limit = limits.registrations, refillPeriod = limits.registrationPeriod)
            requestKey { it.clientKey() }
        }
        register(RUNS_LIMIT) {
            rateLimiter(limit = limits.runs, refillPeriod = limits.runPeriod)
            requestKey { it.clientKey() }
        }
    }

    fun ApplicationCall.player(): Player? =
        request.header("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.let(store::playerByToken)

    suspend fun ApplicationCall.fail(status: HttpStatusCode, error: String, message: String) =
        respond(status, ErrorResponse(error, message))

    routing {
        get("/health") { call.respondText("ok") }

        rateLimit(REGISTER_LIMIT) {
            post(Api.PLAYERS) {
                val name = NameRules.normalize(call.receive<RegisterRequest>().name)
                if (!NameRules.isValid(name)) return@post call.fail(HttpStatusCode.BadRequest, ApiErrors.INVALID_NAME, NAME_HELP)
                val (player, token) = store.createPlayer(name)
                call.respond(HttpStatusCode.Created, PlayerResponse(player.id, player.name, token))
            }
        }

        patch(Api.ME) {
            val player = call.player() ?: return@patch call.fail(HttpStatusCode.Unauthorized, ApiErrors.UNAUTHORIZED, "unknown token")
            val name = NameRules.normalize(call.receive<RenameRequest>().name)
            if (!NameRules.isValid(name)) return@patch call.fail(HttpStatusCode.BadRequest, ApiErrors.INVALID_NAME, NAME_HELP)
            val renamed = store.rename(player.id, name) ?: player
            call.respond(PlayerResponse(renamed.id, renamed.name))
        }

        rateLimit(RUNS_LIMIT) {
            post(Api.RUNS) {
                val player = call.player() ?: return@post call.fail(HttpStatusCode.Unauthorized, ApiErrors.UNAUTHORIZED, "unknown token")
                val run = call.receive<RunSubmission>()
                if (PlayMode.all.none { it.key == run.mode }) {
                    return@post call.fail(HttpStatusCode.NotFound, ApiErrors.UNKNOWN_MODE, "unknown mode ${run.mode}")
                }
                // Rebuilding and replaying a big level takes a few milliseconds of CPU.
                val verdict = withContext(Dispatchers.Default) { RunVerifier.verify(run, today()) }
                when (verdict) {
                    is Verdict.Rejected -> call.fail(HttpStatusCode.UnprocessableEntity, ApiErrors.INVALID_RUN, verdict.reason)
                    is Verdict.Accepted -> {
                        val board = boardFor(run.mode, run.day)
                        val improved = store.submit(player.id, board, BestRun(verdict.score, verdict.bestCombo, run.level, run.day))
                        val entry = checkNotNull(store.entryOf(player.id, board)) { "best run was not stored" }
                        call.respond(RunResult(score = verdict.score, bestCombo = verdict.bestCombo, rank = entry.rank, improved = improved))
                    }
                }
            }
        }

        get("${Api.LEADERBOARDS}/{mode}") {
            val mode = call.parameters["mode"].orEmpty()
            if (PlayMode.all.none { it.key == mode }) {
                return@get call.fail(HttpStatusCode.NotFound, ApiErrors.UNKNOWN_MODE, "unknown mode $mode")
            }
            val day = call.request.queryParameters["day"]?.toLongOrNull() ?: today()
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
            val board = boardFor(mode, day)
            val me = call.player()?.let { store.entryOf(it.id, board) }
            call.respond(
                LeaderboardResponse(
                    mode = mode,
                    day = if (mode == PlayMode.Daily.key) day else null,
                    entries = store.top(board, limit),
                    me = me,
                ),
            )
        }
    }
}

/** The daily challenge has a board per day; every other mode has one all-time board. */
fun boardFor(mode: String, day: Long): BoardKey =
    BoardKey(mode, if (mode == PlayMode.Daily.key) day else BoardKey.ALL_TIME)

private val REGISTER_LIMIT = RateLimitName("register")
private val RUNS_LIMIT = RateLimitName("runs")
private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 100
private val NAME_HELP = "names have ${NameRules.MIN} to ${NameRules.MAX} letters, digits, spaces, '_' or '-'"
