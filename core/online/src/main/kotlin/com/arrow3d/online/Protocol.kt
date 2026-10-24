package com.arrow3d.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire format of the ranking API (version 1). Field names are the JSON keys. */
object Api {
    const val PREFIX = "/v1"
    const val PLAYERS = "$PREFIX/players"
    const val ME = "$PREFIX/players/me"
    const val RUNS = "$PREFIX/runs"
    const val LEADERBOARDS = "$PREFIX/leaderboards"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
data class RegisterRequest(val name: String)

@Serializable
data class RenameRequest(val name: String)

/** [token] is only sent once, when the player registers; the server keeps just its hash. */
@Serializable
data class PlayerResponse(val id: String, val name: String, val token: String? = null)

@Serializable
enum class ActionType { TAP, UNDO, HINT, RESTART }

/** One thing the player did, [t] ms after the level was loaded. */
@Serializable
data class RunAction(
    val type: ActionType,
    val t: Long,
    val arrowId: Int? = null,
    val ambiguous: Boolean = false,
)

/**
 * A won level, as the list of actions since it was loaded. The server rebuilds the level from
 * ([mode], [level], [day], [rating]) and replays the actions; the score is whatever the replay gives.
 */
@Serializable
data class RunSubmission(
    val mode: String,
    val level: Int,
    /** Epoch day: the daily challenge's day, or the day it was played for other modes. */
    val day: Long,
    /** Adaptive mode only: rating the level was generated with. */
    val rating: Double? = null,
    val actions: List<RunAction>,
)

@Serializable
data class RunResult(
    val score: Int,
    val bestCombo: Int,
    /** Player's place on the board this run counts for, with their best score. */
    val rank: Int,
    /** True when this run beat the player's previous best on that board. */
    val improved: Boolean,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val playerId: String,
    val name: String,
    val score: Int,
    val bestCombo: Int,
    val level: Int,
    val day: Long,
)

@Serializable
data class LeaderboardResponse(
    val mode: String,
    /** Set for the daily board, which is per day. */
    val day: Long? = null,
    val entries: List<LeaderboardEntry>,
    /** The caller's own entry when they sent their token and are on the board. */
    val me: LeaderboardEntry? = null,
)

@Serializable
data class ErrorResponse(val error: String, val message: String = "")

/** Machine-readable error codes. */
object ApiErrors {
    const val INVALID_NAME = "invalid_name"
    const val UNAUTHORIZED = "unauthorized"
    const val INVALID_RUN = "invalid_run"
    const val UNKNOWN_MODE = "unknown_mode"
    const val RATE_LIMITED = "rate_limited"
    const val BAD_REQUEST = "bad_request"
}

/** Nicknames: 3 to 16 letters, digits, spaces, '_' or '-', trimmed, and not offensive. */
object NameRules {
    const val MIN = 3
    const val MAX = 16
    private val allowed = Regex("^[\\p{L}\\p{N} _-]+$")

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun isValid(raw: String): Boolean {
        val name = normalize(raw)
        return name.length in MIN..MAX && allowed.matches(name) && !isOffensive(name)
    }

    /**
     * A small blocklist of insults and slurs in Spanish and English. Each word of the name (split
     * on spaces, '_' and '-'), and the whole name, is compared after folding case, accents, common
     * digit-for-letter swaps and repeated letters, so "P-U_T0 amo" and "puuuto" are caught.
     * Whole words only: matching inside words would refuse "Computadora", "Pijama" or "Vergara".
     * It is a first line of defence, not a guarantee.
     */
    fun isOffensive(raw: String): Boolean {
        val words = raw.split(Regex("[\\s_-]+")).filter { it.isNotEmpty() }.map(::fold)
        return (words + fold(raw)).any { it in BLOCKED }
    }

    private fun fold(text: String): String {
        val noAccents = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val letters = buildString {
            for (c in noAccents) {
                when (c) {
                    '0' -> append('o')
                    '1', '!' -> append('i')
                    '3' -> append('e')
                    '4', '@' -> append('a')
                    '5', '$' -> append('s')
                    '7' -> append('t')
                    else -> if (c.isLetter()) append(c)
                }
            }
        }
        // "puuuto" -> "puto"
        return letters.replace(Regex("(.)\\1+"), "$1")
    }

    /** Folded form (no accents, no repeated letters). */
    private val BLOCKED = listOf(
        // Spanish
        "puto", "puta", "pelotudo", "pelotuda", "boludo", "boluda", "forro", "concha", "pija",
        "verga", "chinga", "cabron", "maricon", "marica", "trolo", "mogolico", "pendejo", "pendeja",
        "gilipolas", "hijodeputa", "hdp", "culiao", "culero", "mierda", "coño", "zorra",
        "negrodemierda", "sudaca", "nazi", "hitler", "violar", "violador",
        // English
        "fuck", "fuk", "shit", "bitch", "cunt", "dick", "pusy", "whore", "slut", "nigger",
        "nigga", "faget", "fag", "retard", "rape", "rapist", "asshole", "bastard", "wanker", "twat",
    ).map { fold(it) }.toSet()
}
