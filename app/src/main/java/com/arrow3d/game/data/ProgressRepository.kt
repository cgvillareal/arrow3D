package com.arrow3d.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arrow3d.domain.generator.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Player progress and settings. */
interface IProgressRepository {
    /** Level the player is on, per difficulty (starts at 1). */
    fun observeLevels(): Flow<Map<Difficulty, Int>>
    suspend fun currentLevel(difficulty: Difficulty): Int
    suspend fun setCurrentLevel(difficulty: Difficulty, level: Int)

    fun observeVibrationEnabled(): Flow<Boolean>
    suspend fun setVibrationEnabled(enabled: Boolean)
}

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "arrow3d_progress")

class ProgressRepositoryImpl(context: Context) : IProgressRepository {

    private val store = context.applicationContext.progressStore

    private fun levelKey(d: Difficulty) = intPreferencesKey("level_${d.name.lowercase()}")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")

    override fun observeLevels(): Flow<Map<Difficulty, Int>> = store.data.map { prefs ->
        Difficulty.entries.associateWith { prefs[levelKey(it)] ?: 1 }
    }

    override suspend fun currentLevel(difficulty: Difficulty): Int =
        store.data.first()[levelKey(difficulty)] ?: 1

    override suspend fun setCurrentLevel(difficulty: Difficulty, level: Int) {
        store.edit { it[levelKey(difficulty)] = level.coerceAtLeast(1) }
    }

    override fun observeVibrationEnabled(): Flow<Boolean> = store.data.map { it[vibrationKey] ?: true }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        store.edit { it[vibrationKey] = enabled }
    }
}
