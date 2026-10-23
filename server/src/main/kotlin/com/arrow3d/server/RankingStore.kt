package com.arrow3d.server

import com.arrow3d.online.LeaderboardEntry
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID

data class Player(val id: String, val name: String)

/** One leaderboard: a mode and a period (the day for the daily challenge, [ALL_TIME] otherwise). */
data class BoardKey(val mode: String, val period: Long) {
    companion object {
        const val ALL_TIME = 0L
    }
}

/** A player's best run on a board. */
data class BestRun(val score: Int, val bestCombo: Int, val level: Int, val day: Long)

interface RankingStore {
    /** Returns the new player and their secret token; only the token's hash is stored. */
    fun createPlayer(name: String): Pair<Player, String>
    fun playerByToken(token: String): Player?
    fun rename(playerId: String, name: String): Player?

    /** Deletes the player and every score they had. Returns false if there was no such player. */
    fun deletePlayer(playerId: String): Boolean

    /** Keeps [run] if it beats the player's best on [board]. Returns true when it did. */
    fun submit(playerId: String, board: BoardKey, run: BestRun): Boolean
    fun entryOf(playerId: String, board: BoardKey): LeaderboardEntry?
    fun top(board: BoardKey, limit: Int): List<LeaderboardEntry>
}

/**
 * SQLite store: one file, no server to run. Each player keeps only their best run per board,
 * so the tables stay small. Ties go to whoever got the score first.
 */
class SqliteRankingStore(path: String, private val clock: () -> Long = System::currentTimeMillis) : RankingStore {

    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path").apply {
        createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute(
                """CREATE TABLE IF NOT EXISTS players (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    token_hash TEXT NOT NULL UNIQUE,
                    created_at INTEGER NOT NULL)""",
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS bests (
                    player_id TEXT NOT NULL REFERENCES players(id),
                    mode TEXT NOT NULL,
                    period INTEGER NOT NULL,
                    score INTEGER NOT NULL,
                    best_combo INTEGER NOT NULL,
                    level INTEGER NOT NULL,
                    day INTEGER NOT NULL,
                    achieved_at INTEGER NOT NULL,
                    PRIMARY KEY (player_id, mode, period))""",
            )
            st.execute("CREATE INDEX IF NOT EXISTS bests_board ON bests (mode, period, score DESC, achieved_at, player_id)")
        }
    }

    private val random = SecureRandom()

    @Synchronized
    override fun createPlayer(name: String): Pair<Player, String> {
        val player = Player(UUID.randomUUID().toString(), name)
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        connection.prepareStatement("INSERT INTO players (id, name, token_hash, created_at) VALUES (?, ?, ?, ?)").use {
            it.setString(1, player.id)
            it.setString(2, name)
            it.setString(3, hash(token))
            it.setLong(4, clock())
            it.executeUpdate()
        }
        return player to token
    }

    @Synchronized
    override fun playerByToken(token: String): Player? =
        connection.prepareStatement("SELECT id, name FROM players WHERE token_hash = ?").use {
            it.setString(1, hash(token))
            it.executeQuery().use { rs -> if (rs.next()) Player(rs.getString(1), rs.getString(2)) else null }
        }

    @Synchronized
    override fun rename(playerId: String, name: String): Player? {
        val changed = connection.prepareStatement("UPDATE players SET name = ? WHERE id = ?").use {
            it.setString(1, name)
            it.setString(2, playerId)
            it.executeUpdate()
        }
        return if (changed == 1) Player(playerId, name) else null
    }

    @Synchronized
    override fun deletePlayer(playerId: String): Boolean {
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM bests WHERE player_id = ?").use {
                it.setString(1, playerId)
                it.executeUpdate()
            }
            val deleted = connection.prepareStatement("DELETE FROM players WHERE id = ?").use {
                it.setString(1, playerId)
                it.executeUpdate()
            }
            connection.commit()
            return deleted == 1
        } catch (e: java.sql.SQLException) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    @Synchronized
    override fun submit(playerId: String, board: BoardKey, run: BestRun): Boolean =
        connection.prepareStatement(
            """INSERT INTO bests (player_id, mode, period, score, best_combo, level, day, achieved_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (player_id, mode, period) DO UPDATE SET
                 score = excluded.score, best_combo = excluded.best_combo, level = excluded.level,
                 day = excluded.day, achieved_at = excluded.achieved_at
               WHERE excluded.score > bests.score""",
        ).use {
            it.setString(1, playerId)
            it.setString(2, board.mode)
            it.setLong(3, board.period)
            it.setInt(4, run.score)
            it.setInt(5, run.bestCombo)
            it.setInt(6, run.level)
            it.setLong(7, run.day)
            it.setLong(8, clock())
            it.executeUpdate() == 1
        }

    @Synchronized
    override fun entryOf(playerId: String, board: BoardKey): LeaderboardEntry? =
        connection.prepareStatement(
            """SELECT b.player_id, p.name, b.score, b.best_combo, b.level, b.day,
                 (SELECT COUNT(*) + 1 FROM bests o
                   WHERE o.mode = b.mode AND o.period = b.period
                     AND (o.score > b.score
                       OR (o.score = b.score AND (o.achieved_at < b.achieved_at
                         OR (o.achieved_at = b.achieved_at AND o.player_id < b.player_id)))))
               FROM bests b JOIN players p ON p.id = b.player_id
               WHERE b.player_id = ? AND b.mode = ? AND b.period = ?""",
        ).use {
            it.setString(1, playerId)
            it.setString(2, board.mode)
            it.setLong(3, board.period)
            it.executeQuery().use { rs ->
                if (!rs.next()) {
                    null
                } else {
                    LeaderboardEntry(
                        rank = rs.getInt(7),
                        playerId = rs.getString(1),
                        name = rs.getString(2),
                        score = rs.getInt(3),
                        bestCombo = rs.getInt(4),
                        level = rs.getInt(5),
                        day = rs.getLong(6),
                    )
                }
            }
        }

    @Synchronized
    override fun top(board: BoardKey, limit: Int): List<LeaderboardEntry> =
        connection.prepareStatement(
            """SELECT b.player_id, p.name, b.score, b.best_combo, b.level, b.day
               FROM bests b JOIN players p ON p.id = b.player_id
               WHERE b.mode = ? AND b.period = ?
               ORDER BY b.score DESC, b.achieved_at ASC, b.player_id ASC
               LIMIT ?""",
        ).use {
            it.setString(1, board.mode)
            it.setLong(2, board.period)
            it.setInt(3, limit)
            it.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            LeaderboardEntry(
                                rank = size + 1,
                                playerId = rs.getString(1),
                                name = rs.getString(2),
                                score = rs.getInt(3),
                                bestCombo = rs.getInt(4),
                                level = rs.getInt(5),
                                day = rs.getLong(6),
                            ),
                        )
                    }
                }
            }
        }

    fun close() = connection.close()

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
