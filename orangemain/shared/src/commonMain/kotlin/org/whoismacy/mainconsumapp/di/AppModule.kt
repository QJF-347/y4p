package org.whoismacy.mainconsumapp.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.whoismacy.mainconsumapp.data.local.DatabaseDriverFactory
import org.whoismacy.mainconsumapp.data.local.createDatabase
import org.whoismacy.mainconsumapp.data.remote.BackendApi
import org.whoismacy.mainconsumapp.data.repository.CartRepository
import org.whoismacy.mainconsumapp.data.repository.CategoryRepository
import org.whoismacy.mainconsumapp.data.repository.ProductRepository
import org.whoismacy.mainconsumapp.data.repository.SettingsRepository
import org.whoismacy.mainconsumapp.data.repository.TransactionRepository
import org.whoismacy.mainconsumapp.data.sync.SyncService
import org.whoismacy.mainconsumapp.ui.screens.checkout.CheckoutViewModel
import org.whoismacy.mainconsumapp.ui.screens.history.HistoryViewModel
import org.whoismacy.mainconsumapp.ui.screens.pos.PosViewModel
import org.whoismacy.mainconsumapp.ui.screens.products.ProductsViewModel
import org.whoismacy.mainconsumapp.ui.screens.setup.SetupViewModel

val networkModule = module {
    single<Clock> { Clock.System }

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}

val dataModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
    single { ProductRepository(get(), get()) }
    single { TransactionRepository(get(), get()) }
    single { CategoryRepository(get()) }
    single { CartRepository() }
    single { SettingsRepository(get()) }
    single {
        val settings = get<SettingsRepository>()
        // Resolved per request so a setup change takes effect without an app restart.
        BackendApi(get(), { settings.current() })
    }
    single { SyncService(get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { PosViewModel(get(), get()) }
    viewModel { CheckoutViewModel(get(), get(), get(), get(), get()) }
    viewModel { ProductsViewModel(get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { SetupViewModel(get(), get()) }
}

val appModule = listOf(dataModule, networkModule, viewModelModule)

expect val platformModule: Module
