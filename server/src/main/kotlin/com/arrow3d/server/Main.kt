package com.arrow3d.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

/**
 * Configuration comes from the environment:
 * - PORT (default 8080)
 * - DB_PATH: SQLite file (default data/ranking.db)
 * - TRUST_PROXY: "true" when running behind a proxy that sets X-Forwarded-For
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val dbPath = System.getenv("DB_PATH") ?: "data/ranking.db"
    val trustProxy = System.getenv("TRUST_PROXY")?.toBoolean() ?: false
    File(dbPath).absoluteFile.parentFile?.mkdirs()
    val store = SqliteRankingStore(dbPath)
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        rankingModule(store, trustProxy = trustProxy)
    }.start(wait = true)
}
