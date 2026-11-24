package com.sixteenhalves.orangeadmin.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefsFileName = "secure_session_prefs"
    private val tag = "SessionManager"

    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences(context)
    }

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsFileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Log.e(tag, "EncryptedSharedPreferences decryption failed, resetting secure preferences", e)
            try {
                context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE).edit().clear().apply()
                val prefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$prefsFileName.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }
            } catch (ex: Exception) {
                Log.e(tag, "Failed to delete corrupted secure shared preferences file", ex)
            }

            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsFileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val _isSessionValid = MutableStateFlow(hasValidTokens())
    val isSessionValid: StateFlow<Boolean> = _isSessionValid.asStateFlow()

    fun saveTokens(accessToken: String, refreshToken: String) {
        try {
            sharedPreferences.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .apply()
            _isSessionValid.value = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to save tokens", e)
        }
    }

    fun getAccessToken(): String? = try {
        sharedPreferences.getString("access_token", null)
    } catch (e: Exception) {
        Log.e(tag, "Failed to get access token", e)
        null
    }

    fun getRefreshToken(): String? = try {
        sharedPreferences.getString("refresh_token", null)
    } catch (e: Exception) {
        Log.e(tag, "Failed to get refresh token", e)
        null
    }

    fun clearSession() {
        try {
            sharedPreferences.edit()
                .remove("access_token")
                .remove("refresh_token")
                .apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear session", e)
        }
        _isSessionValid.value = false
    }

    private fun hasValidTokens(): Boolean {
        return !getAccessToken().isNullOrEmpty() && !getRefreshToken().isNullOrEmpty()
    }
}
