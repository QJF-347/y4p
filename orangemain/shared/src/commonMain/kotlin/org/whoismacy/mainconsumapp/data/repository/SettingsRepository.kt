package org.whoismacy.mainconsumapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.whoismacy.mainconsumapp.data.local.AppSettingsEntity
import org.whoismacy.mainconsumapp.data.local.OrangeDatabase
import org.whoismacy.mainconsumapp.util.UuidV7

data class DeviceSettings(
    val baseUrl: String,
    val deviceToken: String,
    val deviceId: String,
    val branchId: String,
    val cashierId: String,
    val cashierName: String,
    val lastServerVersion: Long,
)

/**
 * Device provisioning for the backend API: server base URL, the branch device
 * token issued by the admin app, and the branch the device belongs to.
 */
class SettingsRepository(private val database: OrangeDatabase) {

    private val queries get() = database.appSettingsQueries

    fun observe(): Flow<DeviceSettings?> =
        queries.select().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toSettings() }

    suspend fun current(): DeviceSettings? = withContext(Dispatchers.Default) {
        queries.select().executeAsOneOrNull()?.toSettings()
    }

    /**
     * [deviceId] must be the id the backend returned when the admin created this
     * device: `POST /sync/push` requires it to match the presented token.
     */
    suspend fun save(
        baseUrl: String,
        deviceToken: String,
        deviceId: String,
        branchId: String,
        cashierName: String,
    ) {
        withContext(Dispatchers.Default) {
            val existing = queries.select().executeAsOneOrNull()
            val normalizedUrl = baseUrl.trim().trimEnd('/')
            val normalizedToken = deviceToken.trim()
            val normalizedDevice = deviceId.trim()
            val normalizedBranch = branchId.trim()
            // A different server or branch keeps its own version stream, so restart from zero.
            val identityChanged = existing != null && (
                existing.base_url != normalizedUrl ||
                    existing.device_token != normalizedToken ||
                    existing.branch_id != normalizedBranch
                )
            queries.upsert(
                base_url = normalizedUrl,
                device_token = normalizedToken,
                device_id = normalizedDevice,
                branch_id = normalizedBranch,
                cashier_id = existing?.cashier_id ?: UuidV7.new(),
                cashier_name = cashierName.trim(),
                last_server_version = if (identityChanged) 0L else existing?.last_server_version ?: 0L,
            )
        }
    }

    suspend fun setLastServerVersion(version: Long) {
        withContext(Dispatchers.Default) { queries.updateLastVersion(version) }
    }
}

private fun AppSettingsEntity.toSettings() = DeviceSettings(
    baseUrl = base_url,
    deviceToken = device_token,
    deviceId = device_id,
    branchId = branch_id,
    cashierId = cashier_id,
    cashierName = cashier_name,
    lastServerVersion = last_server_version,
)
