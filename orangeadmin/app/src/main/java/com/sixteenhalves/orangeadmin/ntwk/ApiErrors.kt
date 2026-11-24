package com.sixteenhalves.orangeadmin.ntwk

import com.google.gson.JsonParser
import retrofit2.Response
import java.io.IOException

/** A non-2xx response from the API, carrying the contract error code (API_CONTRACT §0). */
class ApiException(
    val statusCode: Int,
    val code: String?,
    override val message: String,
) : Exception(message)

/** Parses the `{"error":{"code","message"}}` envelope, falling back to the HTTP reason phrase. */
fun parseApiError(response: Response<*>): ApiException {
    val fallback = response.message().ifBlank { "Request failed (${response.code()})" }
    val raw = runCatching { response.errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return ApiException(response.code(), null, fallback)
    return runCatching {
        val error = JsonParser.parseString(raw).asJsonObject.getAsJsonObject("error")
        val message = error?.get("message")?.takeUnless { it.isJsonNull }?.asString
        val code = error?.get("code")?.takeUnless { it.isJsonNull }?.asString
        ApiException(response.code(), code, message?.ifBlank { fallback } ?: fallback)
    }.getOrElse { ApiException(response.code(), null, fallback) }
}

/** Turns any failure into something safe to show to a user. */
fun Throwable.toUserMessage(fallback: String): String =
    when (this) {
        is ApiException -> message
        is IOException -> "Can't reach the server. Check your connection and try again."
        else -> fallback
    }
