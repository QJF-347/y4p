package org.whoismacy.mainconsumapp.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".orangepos")
        dbDir.mkdirs()
        val dbFile = File(dbDir, "orange_pos.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // JdbcSqliteDriver does not track schema versions, so migrate by hand.
        val target = OrangeDatabase.Schema.version
        if (!tableExists(driver, "SaleEntity")) {
            OrangeDatabase.Schema.create(driver)
            setUserVersion(driver, target)
        } else {
            val current = maxOf(userVersion(driver), 1L)
            if (current < target) {
                OrangeDatabase.Schema.migrate(driver, current, target)
                setUserVersion(driver, target)
            }
        }
        return driver
    }

    private fun userVersion(driver: SqlDriver): Long =
        driver.executeQuery(
            null,
            "PRAGMA user_version",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0,
        ).value

    private fun setUserVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

    private fun tableExists(driver: SqlDriver, name: String): Boolean =
        driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            { cursor -> QueryResult.Value(cursor.next().value) },
            1,
        ) {
            bindString(0, name)
        }.value
}
