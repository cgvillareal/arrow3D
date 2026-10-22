package com.arrow3d.online

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** The server answered with an error; [error] is one of [ApiErrors] when it sent one. */
    data class Failed(val status: Int, val error: String?, val message: String?) : ApiResult<Nothing>

    /** No answer: offline, timeout, DNS, unreadable response. */
    data class Unreachable(val cause: Throwable) : ApiResult<Nothing>
}

/**
 * Minimal HTTP client for the ranking API over [HttpURLConnection], so the app needs no extra
 * network library. Every call runs on [dispatcher].
 */
class RankingClient(
    baseUrl: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Int = 10_000,
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun register(name: String): ApiResult<PlayerResponse> =
        call("POST", Api.PLAYERS, token = null, RegisterRequest(name), RegisterRequest.serializer(), PlayerResponse.serializer())

    suspend fun rename(token: String, name: String): ApiResult<PlayerResponse> =
        call("PATCH", Api.ME, token, RenameRequest(name), RenameRequest.serializer(), PlayerResponse.serializer())

    suspend fun submit(token: String, run: RunSubmission): ApiResult<RunResult> =
        call("POST", Api.RUNS, token, run, RunSubmission.serializer(), RunResult.serializer())

    /** [day] is required for the daily board and ignored by the others. [token] adds the caller's own entry. */
    suspend fun leaderboard(mode: String, day: Long? = null, limit: Int = 50, token: String? = null): ApiResult<LeaderboardResponse> {
        val query = buildList {
            if (day != null) add("day=$day")
            add("limit=$limit")
        }.joinToString("&")
        val path = "${Api.LEADERBOARDS}/${URLEncoder.encode(mode, "UTF-8")}?$query"
        return call<Unit, LeaderboardResponse>("GET", path, token, null, null, LeaderboardResponse.serializer())
    }

    private suspend fun <B, R> call(
        method: String,
        path: String,
        token: String?,
        body: B?,
        bodySerializer: KSerializer<B>?,
        responseSerializer: KSerializer<R>,
    ): ApiResult<R> = withContext(dispatcher) {
        try {
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            // HttpURLConnection has no PATCH; the server also accepts it as an override header.
            if (method == "PATCH") {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                connection.requestMethod = method
            }
            try {
                if (body != null && bodySerializer != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(Api.json.encodeToString(bodySerializer, body).toByteArray()) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status in 200..299) {
                    ApiResult.Ok(Api.json.decodeFromString(responseSerializer, text))
                } else {
                    val error = runCatching { Api.json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                    ApiResult.Failed(status, error?.error, error?.message)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            ApiResult.Unreachable(e)
        } catch (e: SerializationException) {
            ApiResult.Unreachable(e)
        } catch (e: IllegalArgumentException) {
            // Malformed URL or JSON that does not match: treat it like no answer.
            ApiResult.Unreachable(e)
        }
    }
}
