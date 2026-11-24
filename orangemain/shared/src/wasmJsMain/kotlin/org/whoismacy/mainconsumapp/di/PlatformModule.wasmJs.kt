package org.whoismacy.mainconsumapp.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.whoismacy.mainconsumapp.data.local.DatabaseDriverFactory

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
}
