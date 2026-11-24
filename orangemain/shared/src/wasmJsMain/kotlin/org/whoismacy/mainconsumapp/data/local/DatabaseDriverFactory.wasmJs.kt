package org.whoismacy.mainconsumapp.data.local

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        throw UnsupportedOperationException("WasmJS database driver is not supported yet.")
    }
}