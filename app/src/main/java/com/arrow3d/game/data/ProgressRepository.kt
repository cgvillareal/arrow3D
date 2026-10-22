package com.arrow3d.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arrow3d.domain.generator.Difficulty
import com.arrow3d.domain.modes.AdaptiveDifficulty
import com.arrow3d.domain.modes.AdaptiveProgress
import com.arrow3d.domain.modes.DailyRecord
import com.arrow3d.domain.modes.Leaderboard
import com.arrow3d.domain.modes.PlayMode
import com.arrow3d.domain.modes.ScoreEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val vibration: Boolean = true,
    val sound: Boolean = true,
    val colorBlind: Boolean = false,
)

/** Player progress, records and settings. */
interface IProgressRepository {
    /** Level the player is on, per difficulty (starts at 1). */
    fun observeLevels(): Flow<Map<Difficulty, Int>>
    suspend fun currentLevel(difficulty: Difficulty): Int
    suspend fun setCurrentLevel(difficulty: Difficulty, level: Int)

    fun observeSettings(): Flow<Settings>
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setColorBlindEnabled(enabled: Boolean)

    fun observeAdaptive(): Flow<AdaptiveProgress>
    suspend fun setAdaptive(progress: AdaptiveProgress)

    fun observeDaily(): Flow<DailyRecord>
    suspend fun setDaily(record: DailyRecord)

    /** Local ranking, keyed by [PlayMode.key]. */
    fun observeLeaderboards(): Flow<Map<String, Leaderboard>>

    /** Adds a finished level to its mode's ranking; returns its rank (1-based) or null if it did not make the top. */
    suspend fun addScore(modeKey: String, entry: ScoreEntry): Int?
}

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "arrow3d_progress")

class ProgressRepositoryImpl(context: Context) : IProgressRepository {

    private val store = context.applicationContext.progressStore

    private fun levelKey(d: Difficulty) = intPreferencesKey("level_${d.name.lowercase()}")
    private fun recordsKey(modeKey: String) = stringPreferencesKey("records_$modeKey")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")
    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val colorBlindKey = booleanPreferencesKey("color_blind_enabled")
    private val adaptiveLevelKey = intPreferencesKey("adaptive_level")
    private val adaptiveRatingKey = doublePreferencesKey("adaptive_rating")
    private val dailyLastKey = longPreferencesKey("daily_last_completed_day")
    private val dailyStreakKey = intPreferencesKey("daily_streak")
    private val dailyBestStreakKey = intPreferencesKey("daily_best_streak")
    private val dailyBestScoreKey = intPreferencesKey("daily_best_score")

    override fun observeLevels(): Flow<Map<Difficulty, Int>> = store.data.map { prefs ->
        Difficulty.entries.associateWith { prefs[levelKey(it)] ?: 1 }
    }

    override suspend fun currentLevel(difficulty: Difficulty): Int =
        store.data.first()[levelKey(difficulty)] ?: 1

    override suspend fun setCurrentLevel(difficulty: Difficulty, level: Int) {
        store.edit { it[levelKey(difficulty)] = level.coerceAtLeast(1) }
    }

    override fun observeSettings(): Flow<Settings> = store.data.map {
        Settings(
            vibration = it[vibrationKey] ?: true,
            sound = it[soundKey] ?: true,
            colorBlind = it[colorBlindKey] ?: false,
        )
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        store.edit { it[vibrationKey] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { it[soundKey] = enabled }
    }

    override suspend fun setColorBlindEnabled(enabled: Boolean) {
        store.edit { it[colorBlindKey] = enabled }
    }

    override fun observeAdaptive(): Flow<AdaptiveProgress> = store.data.map {
        AdaptiveProgress(
            level = it[adaptiveLevelKey] ?: 1,
            rating = it[adaptiveRatingKey] ?: AdaptiveDifficulty.START_RATING,
        )
    }

    override suspend fun setAdaptive(progress: AdaptiveProgress) {
        store.edit {
            it[adaptiveLevelKey] = progress.level
            it[adaptiveRatingKey] = progress.rating
        }
    }

    override fun observeDaily(): Flow<DailyRecord> = store.data.map {
        DailyRecord(
            lastCompletedDay = it[dailyLastKey],
            streak = it[dailyStreakKey] ?: 0,
            bestStreak = it[dailyBestStreakKey] ?: 0,
            bestScore = it[dailyBestScoreKey] ?: 0,
        )
    }

    override suspend fun setDaily(record: DailyRecord) {
        store.edit {
            val last = record.lastCompletedDay
            if (last == null) it.remove(dailyLastKey) else it[dailyLastKey] = last
            it[dailyStreakKey] = record.streak
            it[dailyBestStreakKey] = record.bestStreak
            it[dailyBestScoreKey] = record.bestScore
        }
    }

    override fun observeLeaderboards(): Flow<Map<String, Leaderboard>> = store.data.map { prefs ->
        PlayMode.all.associate { it.key to Leaderboard.decode(prefs[recordsKey(it.key)]) }
    }

    override suspend fun addScore(modeKey: String, entry: ScoreEntry): Int? {
        var rank: Int? = null
        store.edit { prefs ->
            val (board, r) = Leaderboard.decode(prefs[recordsKey(modeKey)]).add(entry)
            prefs[recordsKey(modeKey)] = board.encode()
            rank = r
        }
        return rank
    }
}
