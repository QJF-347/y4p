package com.sixteenhalves.orangeadmin.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sixteenhalves.orangeadmin.domain.BiometricPreference
import com.sixteenhalves.orangeadmin.domain.NotificationPreferences
import com.sixteenhalves.orangeadmin.domain.PaymentSettings
import com.sixteenhalves.orangeadmin.domain.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings_preferences")

@Singleton
class SettingsPreferencesRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val THEME_KEY = stringPreferencesKey("theme_preference")
        private val LOW_STOCK_ALERTS_KEY = booleanPreferencesKey("low_stock_alerts")
        private val ENABLE_BIOMETRICS = booleanPreferencesKey("enable_biometrics")

        // Local-only M-Pesa credentials (see PaymentSettings). Not synced anywhere.
        private val PAYBILL_NUMBER_KEY = stringPreferencesKey("mpesa_paybill_number")
        private val BUY_GOODS_TILL_KEY = stringPreferencesKey("mpesa_buy_goods_till")
        private val CONSUMER_KEY_KEY = stringPreferencesKey("mpesa_consumer_key")
        private val CONSUMER_SECRET_KEY = stringPreferencesKey("mpesa_consumer_secret")
        private val PASSKEY_KEY = stringPreferencesKey("mpesa_passkey")
        private val SHORTCODE_KEY = stringPreferencesKey("mpesa_shortcode")

        val themePreference: Flow<ThemePreference> =
            context.dataStore.data.map { preferences ->
                val themeString = preferences[THEME_KEY] ?: ThemePreference.SYSTEM_DEFAULT.name
                ThemePreference.fromString(themeString)
            }

        val notificationPreferences: Flow<NotificationPreferences> =
            context.dataStore.data.map { preferences ->
                NotificationPreferences(
                    lowStockAlerts = preferences[LOW_STOCK_ALERTS_KEY] ?: false,
                )
            }

        val biometricsPreference: Flow<BiometricPreference> =
            context.dataStore.data.map { preferences ->
                BiometricPreference(enabled = preferences[ENABLE_BIOMETRICS] ?: false)
            }

        val paymentSettings: Flow<PaymentSettings> =
            context.dataStore.data.map { preferences ->
                PaymentSettings(
                    paybillNumber = preferences[PAYBILL_NUMBER_KEY].orEmpty(),
                    buyGoodsTill = preferences[BUY_GOODS_TILL_KEY].orEmpty(),
                    consumerKey = preferences[CONSUMER_KEY_KEY].orEmpty(),
                    consumerSecret = preferences[CONSUMER_SECRET_KEY].orEmpty(),
                    passkey = preferences[PASSKEY_KEY].orEmpty(),
                    shortcode = preferences[SHORTCODE_KEY].orEmpty(),
                )
            }

        suspend fun updatePaymentSettings(settings: PaymentSettings) {
            context.dataStore.edit { preferences ->
                preferences[PAYBILL_NUMBER_KEY] = settings.paybillNumber.trim()
                preferences[BUY_GOODS_TILL_KEY] = settings.buyGoodsTill.trim()
                preferences[CONSUMER_KEY_KEY] = settings.consumerKey.trim()
                preferences[CONSUMER_SECRET_KEY] = settings.consumerSecret.trim()
                preferences[PASSKEY_KEY] = settings.passkey.trim()
                preferences[SHORTCODE_KEY] = settings.shortcode.trim()
            }
        }

        suspend fun updateThemePreference(themePreference: ThemePreference) {
            context.dataStore.edit { preferences ->
                preferences[THEME_KEY] = themePreference.name
            }
        }

        suspend fun updateNotificationPreference(lowStockAlerts: Boolean? = null) {
            context.dataStore.edit { preferences ->
                lowStockAlerts?.let { preferences[LOW_STOCK_ALERTS_KEY] = it }
            }
        }

        suspend fun updateBiometricPreference(enabled: Boolean? = false) {
            context.dataStore.edit { preferences ->
                enabled?.let { preferences[ENABLE_BIOMETRICS] = enabled }
            }
        }
    }
