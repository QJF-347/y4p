package com.sixteenhalves.orangeadmin.ntwk

import com.sixteenhalves.orangeadmin.domain.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

class AuthAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    // Use Provider to avoid circular dependency since NetworkService might depend on OkHttpClient
    private val networkServiceProvider: Provider<NetworkService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // If the refresh token request itself failed with a 401, we shouldn't try again.
        if (response.request.url.pathSegments.last() == "refresh-token") {
            sessionManager.clearSession()
            return null
        }

        val refreshToken = sessionManager.getRefreshToken() ?: return null

        var newAccessToken: String? = null

        // Attempt to refresh the token synchronously
        runBlocking {
            try {
                val refreshResponse = networkServiceProvider.get().refreshToken(RefreshRequest(refreshToken))
                if (refreshResponse.isSuccessful) {
                    refreshResponse.body()?.let { responseBody ->
                        val accessToken = responseBody.token ?: return@runBlocking
                        val newRefresh = responseBody.refreshToken ?: refreshToken
                        
                        sessionManager.saveTokens(accessToken, newRefresh)
                        newAccessToken = accessToken
                    }
                } else {
                    sessionManager.clearSession()
                }
            } catch (e: Exception) {
                sessionManager.clearSession()
            }
        }

        return newAccessToken?.let {
            response.request.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        }
    }
}
