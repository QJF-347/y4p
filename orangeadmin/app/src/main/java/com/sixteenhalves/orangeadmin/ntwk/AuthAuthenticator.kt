package com.sixteenhalves.orangeadmin.ntwk

import com.sixteenhalves.orangeadmin.domain.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

class AuthAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    // Use Provider to avoid circular dependency since NetworkService might depend on OkHttpClient
    private val networkServiceProvider: Provider<NetworkService>
) : Authenticator {

    /** Tag applied to a request we already retried with a refreshed token, so we never loop forever. */
    private object RefreshedRequest

    /**
     * Guards token refresh so only one refresh runs at a time.
     *
     * The backend rotates refresh tokens and treats reuse of a rotated token as theft, revoking
     * every session in the token family. Two parallel requests that both got 401 must therefore
     * not both call /auth/refresh with the same refresh token.
     */
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // This request was already retried once with a fresh token and was rejected again.
        if (response.request.tag(RefreshedRequest::class.java) != null) return null

        // The refresh endpoint itself was rejected. Only a definitive 401/403 means the refresh
        // token is actually invalid; a 5xx or timeout must not sign the user out.
        if (response.request.url.pathSegments.lastOrNull() == "refresh") {
            if (response.code == 401 || response.code == 403) sessionManager.clearSession()
            return null
        }

        // Only requests that actually carried a bearer token can be retried with a new one.
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        // Without a refresh token there is nothing to retry with.
        if (sessionManager.getRefreshToken() == null) return null

        // Another request may already have renewed the token while this one was in flight.
        val currentToken = sessionManager.getAccessToken()
        if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
            return retryWith(response.request, currentToken)
        }

        synchronized(refreshLock) {
            // Re-check under the lock: whoever held it may have refreshed just now.
            val latestToken = sessionManager.getAccessToken()
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return retryWith(response.request, latestToken)
            }

            val refreshToken = sessionManager.getRefreshToken() ?: return null
            var newAccessToken: String? = null

            // Attempt to refresh the token synchronously
            runBlocking {
                try {
                    val refreshResponse =
                        networkServiceProvider.get().refreshToken(RefreshRequest(refreshToken))
                    if (refreshResponse.isSuccessful) {
                        val body = refreshResponse.body()
                        val accessToken = body?.accessToken
                        if (!accessToken.isNullOrBlank()) {
                            sessionManager.saveTokens(accessToken, body.refreshToken ?: refreshToken)
                            newAccessToken = accessToken
                        }
                    } else if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                        // Refresh was definitively rejected — the session is over.
                        sessionManager.clearSession()
                    }
                    // Otherwise (5xx, 429, ...): keep the session and let the caller see the failure.
                } catch (e: IOException) {
                    // Network hiccup, timeout or DNS failure: keep the session so a later retry can succeed.
                } catch (e: Exception) {
                    // Unexpected failure: keep the session and don't retry.
                }
            }

            return newAccessToken?.let { retryWith(response.request, it) }
        }
    }

    private fun retryWith(request: Request, token: String): Request =
        request.newBuilder()
            .header("Authorization", "Bearer $token")
            .tag(RefreshedRequest::class.java, RefreshedRequest)
            .build()
}
