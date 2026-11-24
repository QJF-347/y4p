package org.whoismacy.mainconsumapp.util

/**
 * The provisioning details the admin app issues for a POS till. [baseUrl] is
 * optional: an existing (or the built-in) backend URL is kept when the shared
 * text does not name one.
 */
data class DeviceCredentials(
    val baseUrl: String? = null,
    val deviceToken: String? = null,
    val deviceId: String? = null,
    val branchId: String? = null,
) {
    val isComplete: Boolean
        get() = !deviceToken.isNullOrBlank() && !deviceId.isNullOrBlank() && !branchId.isNullOrBlank()

    /** The labelled block the admin app copies, shares and encodes as a QR code. */
    fun toShareText(): String =
        buildString {
            if (!baseUrl.isNullOrBlank()) appendLine("Backend URL: $baseUrl")
            appendLine("Device token: $deviceToken")
            appendLine("Device id: $deviceId")
            appendLine("Branch id: $branchId")
        }.trimEnd()
}

/**
 * Reads device credentials out of text produced by the admin app — pasted from
 * the clipboard, received through a share sheet, or decoded from a QR code.
 *
 * Accepted shapes:
 *  - labelled lines: `Backend URL: https://…`, `Device token=…`, `device_id=…`
 *  - a bare query string: `base_url=…&token=…&device_id=…&branch_id=…`
 *  - a setup link: `orange://device?base=…&token=…&device_id=…&branch_id=…`
 *
 * Keys are matched case-insensitively and ignore spaces, `_` and `-`, so
 * "Device id", "device_id" and "DeviceID" all mean the same field. Returns
 * null unless a device token, device id and branch id are all present.
 */
fun parseDeviceCredentials(raw: String): DeviceCredentials? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    var baseUrl: String? = null
    var deviceToken: String? = null
    var deviceId: String? = null
    var branchId: String? = null

    fun accept(key: String, value: String) {
        val cleaned = value.trim().trim('"', '\'').trim()
        if (cleaned.isEmpty()) return
        when (normalizeCredentialKey(key)) {
            "baseurl", "backendurl", "backend", "base", "server", "serverurl", "url", "apiurl", "api" ->
                if (baseUrl == null) baseUrl = cleaned
            "devicetoken", "token", "accesstoken", "deviceaccesstoken" ->
                if (deviceToken == null) deviceToken = cleaned
            "deviceid", "device" ->
                if (deviceId == null) deviceId = cleaned
            "branchid", "branch" ->
                if (branchId == null) branchId = cleaned
        }
    }

    val queryAt = text.indexOf('?')
    val prefix = if (queryAt >= 0) text.substring(0, queryAt) else text
    if (queryAt >= 0 && prefix.contains("://")) {
        text.substring(queryAt + 1)
            .split('&', ';', '#')
            .forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) accept(pair.substring(0, eq), percentDecode(pair.substring(eq + 1)))
            }
    } else {
        text.split('\n', '\r', ';', ',', '&').forEach { segment ->
            val line = segment.trim()
            if (line.isEmpty()) return@forEach
            val separator = listOf(line.indexOf(':'), line.indexOf('=')).filter { it > 0 }.minOrNull()
            if (separator == null) {
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    if (baseUrl == null) baseUrl = line
                }
                return@forEach
            }
            accept(line.substring(0, separator), line.substring(separator + 1))
        }
    }

    if (deviceToken.isNullOrBlank() || deviceId.isNullOrBlank() || branchId.isNullOrBlank()) return null
    return DeviceCredentials(
        baseUrl = baseUrl?.trimEnd('/'),
        deviceToken = deviceToken,
        deviceId = deviceId,
        branchId = branchId,
    )
}

private fun normalizeCredentialKey(key: String): String = key.lowercase().filter { it.isLetterOrDigit() }

private fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '+' -> out.append(' ')
            c == '%' && i + 2 < value.length -> {
                val code = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (code == null) {
                    out.append(c)
                } else {
                    out.append(code.toChar())
                    i += 2
                }
            }
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}
