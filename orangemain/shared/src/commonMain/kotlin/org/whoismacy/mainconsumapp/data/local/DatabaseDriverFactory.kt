package org.whoismacy.mainconsumapp.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): OrangeDatabase {
    val driver = driverFactory.createDriver()
    return OrangeDatabase(driver)
}
