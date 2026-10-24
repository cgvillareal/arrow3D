package com.arrow3d.domain.modes

/** One finished level in the local ranking. [day] is the epoch day it was played. */
data class ScoreEntry(val score: Int, val bestCombo: Int, val level: Int, val day: Long)

/** Best scores of one mode, highest first. Stored as text so any key-value store can keep it. */
data class Leaderboard(val entries: List<ScoreEntry> = emptyList()) {

    val best: ScoreEntry? get() = entries.firstOrNull()

    /** Adds [entry] and keeps the top [MAX_ENTRIES]. Returns the board and the entry's rank (1-based), or null if it did not make it. */
    fun add(entry: ScoreEntry): Pair<Leaderboard, Int?> {
        val sorted = (entries + entry)
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.bestCombo }.thenBy { it.day })
            .take(MAX_ENTRIES)
        val rank = sorted.indexOfFirst { it === entry }.takeIf { it >= 0 }?.plus(1)
        return Leaderboard(sorted) to rank
    }

    fun encode(): String = entries.joinToString(";") { "${it.score},${it.bestCombo},${it.level},${it.day}" }

    companion object {
        const val MAX_ENTRIES = 10

        /** Ignores anything it cannot read, so a corrupt value only loses the bad entries. */
        fun decode(text: String?): Leaderboard {
            if (text.isNullOrBlank()) return Leaderboard()
            val entries = text.split(";").mapNotNull { row ->
                val parts = row.split(",")
                if (parts.size != 4) return@mapNotNull null
                val score = parts[0].toIntOrNull() ?: return@mapNotNull null
                val combo = parts[1].toIntOrNull() ?: return@mapNotNull null
                val level = parts[2].toIntOrNull() ?: return@mapNotNull null
                val day = parts[3].toLongOrNull() ?: return@mapNotNull null
                ScoreEntry(score, combo, level, day)
            }
            return Leaderboard(entries.sortedByDescending { it.score }.take(MAX_ENTRIES))
        }
    }
}
