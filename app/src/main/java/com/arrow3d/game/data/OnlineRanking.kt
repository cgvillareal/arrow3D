package com.arrow3d.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arrow3d.online.Api
import com.arrow3d.online.ApiResult
import com.arrow3d.online.LeaderboardResponse
import com.arrow3d.online.RankingClient
import com.arrow3d.online.RunResult
import com.arrow3d.online.RunSubmission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/** The player's identity on the ranking server. [token] is their secret; it never leaves the device otherwise. */
data class OnlineAccount(val id: String, val name: String, val token: String)

/** What happened to a won level on the online ranking. */
sealed interface SubmitOutcome {
    data class Ranked(val result: RunResult) : SubmitOutcome

    /** No connection: it is kept and sent later. */
    data object Queued : SubmitOutcome

    /** The server refused it (it does not replay to a win, or the daily challenge closed). */
    data object Rejected : SubmitOutcome

    /** Ranking not configured, or the player has not joined. */
    data object Skipped : SubmitOutcome
}

interface IOnlineRanking {
    /** False when the build has no server URL: the online ranking is hidden. */
    val isConfigured: Boolean

    /** Web page with the privacy policy, served by the ranking server; null when not configured. */
    val privacyUrl: String?

    fun observeAccount(): Flow<OnlineAccount?>
    suspend fun join(name: String): ApiResult<OnlineAccount>

    /** Deletes the account and its scores on the server, then forgets it on this device. */
    suspend fun leave(): ApiResult<Unit>
    suspend fun submit(run: RunSubmission): SubmitOutcome
    suspend fun leaderboard(modeKey: String, day: Long): ApiResult<LeaderboardResponse>

    /** Sends runs that were queued while offline. */
    suspend fun flushPending()
}

private val Context.onlineStore: DataStore<Preferences> by preferencesDataStore(name = "arrow3d_online")

class OnlineRankingImpl(context: Context, baseUrl: String) : IOnlineRanking {

    private val store = context.applicationContext.onlineStore
    private val client = baseUrl.takeIf { it.isNotBlank() }?.let { RankingClient(it) }
    private val pendingLock = Mutex()

    private val idKey = stringPreferencesKey("player_id")
    private val nameKey = stringPreferencesKey("player_name")
    private val tokenKey = stringPreferencesKey("player_token")
    private val pendingKey = stringPreferencesKey("pending_runs")
    private val runsSerializer = ListSerializer(RunSubmission.serializer())

    override val isConfigured: Boolean get() = client != null

    override val privacyUrl: String? = baseUrl.takeIf { it.isNotBlank() }?.trimEnd('/')?.plus("/privacy")

    override fun observeAccount(): Flow<OnlineAccount?> = store.data.map { prefs ->
        val id = prefs[idKey]
        val name = prefs[nameKey]
        val token = prefs[tokenKey]
        if (id != null && name != null && token != null) OnlineAccount(id, name, token) else null
    }

    override suspend fun join(name: String): ApiResult<OnlineAccount> {
        val api = client ?: return ApiResult.Failed(0, null, "ranking not configured")
        return when (val result = api.register(name)) {
            is ApiResult.Ok -> {
                val player = result.value
                val account = OnlineAccount(player.id, player.name, checkNotNull(player.token) { "server sent no token" })
                store.edit {
                    it[idKey] = account.id
                    it[nameKey] = account.name
                    it[tokenKey] = account.token
                }
                ApiResult.Ok(account)
            }
            is ApiResult.Failed -> result
            is ApiResult.Unreachable -> result
        }
    }

    override suspend fun leave(): ApiResult<Unit> {
        val api = client ?: return ApiResult.Failed(0, null, "ranking not configured")
        val account = observeAccount().first() ?: return ApiResult.Ok(Unit)
        val result = api.deleteAccount(account.token)
        // 401: the server no longer knows this token, so there is nothing left to delete there.
        val gone = result is ApiResult.Ok || (result is ApiResult.Failed && result.status == HTTP_UNAUTHORIZED)
        if (!gone) return result
        store.edit {
            it.remove(idKey)
            it.remove(nameKey)
            it.remove(tokenKey)
            it.remove(pendingKey)
        }
        return ApiResult.Ok(Unit)
    }

    override suspend fun submit(run: RunSubmission): SubmitOutcome {
        val api = client ?: return SubmitOutcome.Skipped
        val account = observeAccount().first() ?: return SubmitOutcome.Skipped
        flushPending()
        return when (val result = api.submit(account.token, run)) {
            is ApiResult.Ok -> SubmitOutcome.Ranked(result.value)
            is ApiResult.Unreachable -> {
                enqueue(run)
                SubmitOutcome.Queued
            }
            // 429: too many requests, worth retrying later; anything else will not get better.
            is ApiResult.Failed -> if (result.status == HTTP_TOO_MANY_REQUESTS) {
                enqueue(run)
                SubmitOutcome.Queued
            } else {
                SubmitOutcome.Rejected
            }
        }
    }

    override suspend fun leaderboard(modeKey: String, day: Long): ApiResult<LeaderboardResponse> {
        val api = client ?: return ApiResult.Failed(0, null, "ranking not configured")
        return api.leaderboard(modeKey, day = day, token = observeAccount().first()?.token)
    }

    override suspend fun flushPending() {
        val api = client ?: return
        val account = observeAccount().first() ?: return
        pendingLock.withLock {
            val pending = readPending()
            if (pending.isEmpty()) return
            // Keep only what could not be delivered; refused runs are dropped.
            val left = pending.filter { api.submit(account.token, it).let { r -> r is ApiResult.Unreachable || (r is ApiResult.Failed && r.status == HTTP_TOO_MANY_REQUESTS) } }
            writePending(left)
        }
    }

    private suspend fun enqueue(run: RunSubmission) = pendingLock.withLock {
        writePending((readPending() + run).takeLast(MAX_PENDING))
    }

    private suspend fun readPending(): List<RunSubmission> {
        val text = store.data.first()[pendingKey] ?: return emptyList()
        return runCatching { Api.json.decodeFromString(runsSerializer, text) }.getOrDefault(emptyList())
    }

    private suspend fun writePending(runs: List<RunSubmission>) {
        store.edit { it[pendingKey] = Api.json.encodeToString(runsSerializer, runs) }
    }

    private companion object {
        const val MAX_PENDING = 20
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
    }
}
