package org.whoismacy.mainconsumapp.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.whoismacy.mainconsumapp.data.remote.ApiException
import org.whoismacy.mainconsumapp.data.repository.SettingsRepository
import org.whoismacy.mainconsumapp.data.sync.SyncService

data class SetupState(
    val baseUrl: String = "",
    val deviceToken: String = "",
    val deviceId: String = "",
    val branchId: String = "",
    val cashierName: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !isSaving && baseUrl.isNotBlank() && deviceToken.isNotBlank() &&
            deviceId.isNotBlank() && branchId.isNotBlank() && cashierName.isNotBlank()
}

/**
 * Provisions this till for the backend: server URL, the branch device token,
 * the device id the backend issued when the admin created this till, the branch
 * id, and the cashier name stamped on sales. Saving also pulls the catalog so
 * the POS has something to sell before the first connection drops.
 */
class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val syncService: SyncService,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settingsRepository.current()
            _state.update {
                it.copy(
                    baseUrl = current?.baseUrl ?: DEFAULT_BASE_URL,
                    deviceToken = current?.deviceToken.orEmpty(),
                    deviceId = current?.deviceId.orEmpty(),
                    branchId = current?.branchId.orEmpty(),
                    cashierName = current?.cashierName.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    fun onBaseUrlChanged(value: String) = _state.update { it.copy(baseUrl = value) }

    fun onDeviceTokenChanged(value: String) = _state.update { it.copy(deviceToken = value) }

    fun onDeviceIdChanged(value: String) = _state.update { it.copy(deviceId = value) }

    fun onBranchIdChanged(value: String) = _state.update { it.copy(branchId = value) }

    fun onCashierNameChanged(value: String) = _state.update { it.copy(cashierName = value) }

    fun save() {
        val snapshot = _state.value
        if (!snapshot.canSave) {
            _state.update { it.copy(error = "Fill in every field.", message = null) }
            return
        }

        _state.update { it.copy(isSaving = true, error = null, message = null) }

        viewModelScope.launch {
            try {
                settingsRepository.save(
                    baseUrl = snapshot.baseUrl,
                    deviceToken = snapshot.deviceToken,
                    deviceId = snapshot.deviceId,
                    branchId = snapshot.branchId,
                    cashierName = snapshot.cashierName,
                )
                val pulled = syncService.pull()
                val pushed = syncService.pushPending()
                _state.update {
                    it.copy(
                        baseUrl = settingsRepository.current()?.baseUrl ?: snapshot.baseUrl,
                        isSaving = false,
                        message = buildString {
                            append("Connected. Server version ${pulled.serverVersion}, ")
                            append("${pulled.products} product(s) pulled")
                            if (pulled.salesUpdated > 0) append(", ${pulled.salesUpdated} sale(s) updated")
                            append(".")
                            if (pushed.failures.isNotEmpty()) {
                                append(" ${pushed.failures.size} row(s) rejected on push.")
                            }
                        },
                    )
                }
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "${e.message}${if (e.code.isBlank()) "" else " (${e.code})"}. " +
                            "Check the device id, device token and branch id.",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "Could not reach the backend: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://y4p.onrender.com"
    }
}
