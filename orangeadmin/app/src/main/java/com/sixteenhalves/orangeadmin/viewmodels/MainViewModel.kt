package com.sixteenhalves.orangeadmin.viewmodels

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.material3.SnackbarHostState
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenhalves.orangeadmin.domain.ApplicationState
import com.sixteenhalves.orangeadmin.domain.BiometricPreference
import com.sixteenhalves.orangeadmin.domain.BranchDetail
import com.sixteenhalves.orangeadmin.domain.BranchOverview
import com.sixteenhalves.orangeadmin.domain.DashboardSummary
import com.sixteenhalves.orangeadmin.domain.EventManager
import com.sixteenhalves.orangeadmin.domain.InventoryProduct
import com.sixteenhalves.orangeadmin.domain.NotificationPreferences
import com.sixteenhalves.orangeadmin.domain.SessionManager
import com.sixteenhalves.orangeadmin.domain.ThemePreference
import com.sixteenhalves.orangeadmin.domain.TopSeller
import com.sixteenhalves.orangeadmin.domain.UiState
import com.sixteenhalves.orangeadmin.domain.User
import com.sixteenhalves.orangeadmin.domain.isOwner
import com.sixteenhalves.orangeadmin.repository.AdminRepository
import com.sixteenhalves.orangeadmin.repository.BiometricRepository
import com.sixteenhalves.orangeadmin.repository.DeviceRegistration
import com.sixteenhalves.orangeadmin.repository.SettingsPreferencesRepository
import com.sixteenhalves.orangeadmin.repository.UserRepository
import com.sixteenhalves.orangeadmin.repository.userFacingMessage
import com.sixteenhalves.orangeadmin.utils.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val biometricRepository: BiometricRepository,
        private val settingsPreferencesRepository: SettingsPreferencesRepository,
        private val sessionManager: SessionManager,
        private val adminRepository: AdminRepository,
    ) : ViewModel() {
        private val _user = MutableStateFlow<User?>(null)
        val user: StateFlow<User?> = _user.asStateFlow()

        /** Only OWNER accounts may create branches/products, restock, delete or invite. */
        val isOwner: StateFlow<Boolean> =
            _user.map { it.isOwner }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

        private val _dashboard = MutableStateFlow<UiState<DashboardSummary>>(UiState.Loading)
        val dashboard = _dashboard.asStateFlow()

        private val _branches = MutableStateFlow<UiState<List<BranchOverview>>>(UiState.Loading)
        val branches = _branches.asStateFlow()

        private val _inventory = MutableStateFlow<UiState<List<InventoryProduct>>>(UiState.Loading)
        val inventory = _inventory.asStateFlow()

        private val _branchDetail = MutableStateFlow<UiState<BranchDetail>>(UiState.Loading)
        val branchDetail = _branchDetail.asStateFlow()

        private var dashboardJob: Job? = null
        private var branchesJob: Job? = null
        private var inventoryJob: Job? = null
        private var branchDetailJob: Job? = null

        private val _mainApplicationState = MutableStateFlow<ApplicationState>(ApplicationState.Idle)
        val mainApplicationState = _mainApplicationState.asStateFlow()

        val themePreference: StateFlow<ThemePreference> =
            settingsPreferencesRepository.themePreference.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ThemePreference.SYSTEM_DEFAULT,
            )

        val notificationPreferences: StateFlow<NotificationPreferences> =
            settingsPreferencesRepository.notificationPreferences.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = NotificationPreferences(),
            )

        val biometricPreference: StateFlow<BiometricPreference> =
            settingsPreferencesRepository
                .biometricsPreference
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = BiometricPreference(),
                )

        val snackbarHostState = SnackbarHostState()

        init {
            viewModelScope.launch {
                sessionManager.isSessionValid.collect { isValid ->
                    if (!isValid) {
                        _mainApplicationState.value = ApplicationState.Unauthenticated
                        _user.value = null
                    }
                }
            }

            _mainApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val user = userRepository.getUser().getOrNull()
                    _user.value = user

                    if (user != null) {
                        _mainApplicationState.value = ApplicationState.Authenticated()
                        refreshAll()
                    } else {
                        _mainApplicationState.value = ApplicationState.Unauthenticated
                    }
                } catch (e: Exception) {
                    Log.e("MAIN VIEW MODEL", "Error: ${e.message}")
                    _mainApplicationState.value = ApplicationState.Unauthenticated // Fallback to auth screen
                }
            }
        }

        fun triggerEvent(message: String) {
            EventManager.triggerEvent(EventManager.AppEvent.ShowEvent(message))
        }

        fun refreshAll() {
            refreshDashboard()
            refreshBranches()
            refreshInventory()
        }

        fun refreshDashboard() {
            dashboardJob?.cancel()
            dashboardJob =
                viewModelScope.launch {
                    _dashboard.value = UiState.Loading
                    val summaryResult = adminRepository.getDashboardSummary()
                    val overviewsResult = adminRepository.getBranchOverviews()

                    val summary = summaryResult.getOrNull() ?: adminRepository.cachedDashboardSummary()
                    val overviews = overviewsResult.getOrNull() ?: adminRepository.cachedBranchOverviews()

                    if (summaryResult.isFailure || overviewsResult.isFailure) {
                        if (summary == null && overviews == null) {
                            val failure = summaryResult.exceptionOrNull() ?: overviewsResult.exceptionOrNull()
                            val message =
                                if (failure != null) {
                                    failure.userFacingMessage("Couldn't load your dashboard")
                                } else {
                                    "Couldn't load your dashboard"
                                }
                            _dashboard.value = UiState.Failed(message)
                            return@launch
                        }
                        notifyUsingSavedData()
                    }

                    val data =
                        (summary ?: DashboardSummary(branchesCount = overviews?.size ?: 0)).copy(
                            topSellers =
                                overviews
                                    .orEmpty()
                                    .flatMap { it.stats.topSellers }
                                    .groupBy { it.name }
                                    .map { (name, sellers) -> TopSeller(name, sellers.sumOf { it.quantitySold }) }
                                    .sortedByDescending { it.quantitySold }
                                    .take(5),
                        )
                    _dashboard.value =
                        if (data.branchesCount == 0 && overviews.isNullOrEmpty()) {
                            UiState.Empty
                        } else {
                            UiState.Ready(data)
                        }
                }
        }

        fun refreshBranches() {
            branchesJob?.cancel()
            branchesJob =
                viewModelScope.launch {
                    _branches.value = UiState.Loading
                    adminRepository
                        .getBranchOverviews()
                        .onSuccess { overviews ->
                            _branches.value =
                                if (overviews.isEmpty()) UiState.Empty else UiState.Ready(overviews)
                        }.onFailure { error ->
                            val cached = adminRepository.cachedBranchOverviews()
                            if (cached != null) {
                                _branches.value = UiState.Ready(cached)
                                notifyUsingSavedData()
                            } else {
                                _branches.value =
                                    UiState.Failed(error.userFacingMessage("Couldn't load your branches"))
                            }
                        }
                }
        }

        fun refreshInventory() {
            inventoryJob?.cancel()
            inventoryJob =
                viewModelScope.launch {
                    _inventory.value = UiState.Loading
                    adminRepository
                        .getInventory()
                        .onSuccess { products ->
                            _inventory.value =
                                if (products.isEmpty()) UiState.Empty else UiState.Ready(products)
                        }.onFailure { error ->
                            val cached = adminRepository.cachedInventory()
                            if (cached != null) {
                                _inventory.value = UiState.Ready(cached)
                                notifyUsingSavedData()
                            } else {
                                _inventory.value =
                                    UiState.Failed(error.userFacingMessage("Couldn't load your inventory"))
                            }
                        }
                }
        }

        fun openBranch(branchId: String) {
            branchDetailJob?.cancel()
            branchDetailJob =
                viewModelScope.launch {
                    _branchDetail.value = UiState.Loading
                    adminRepository
                        .getBranchDetail(branchId)
                        .onSuccess { _branchDetail.value = UiState.Ready(it) }
                        .onFailure { error ->
                            val cached = adminRepository.cachedBranchDetail(branchId)
                            if (cached != null) {
                                _branchDetail.value = UiState.Ready(cached)
                                notifyUsingSavedData()
                            } else {
                                _branchDetail.value =
                                    UiState.Failed(error.userFacingMessage("Couldn't load this branch"))
                            }
                        }
                }
        }

        fun createBranch(
            name: String,
            location: String,
            onCreated: (String) -> Unit,
        ) {
            viewModelScope.launch {
                adminRepository
                    .createBranch(name, location)
                    .onSuccess { branch ->
                        triggerEvent("${branch.name} created")
                        refreshAll()
                        onCreated(branch.id)
                    }.onFailure {
                        triggerEvent(it.userFacingMessage("Couldn't create branch"))
                    }
            }
        }

        fun createProduct(
            branchId: String,
            name: String,
            categoryName: String?,
            priceCents: Long,
            stock: Int,
            onCreated: () -> Unit,
        ) {
            viewModelScope.launch {
                adminRepository
                    .createProduct(branchId, name, categoryName, priceCents, stock)
                    .onSuccess {
                        triggerEvent("$name added to inventory")
                        refreshInventory()
                        refreshBranches()
                        refreshDashboard()
                        onCreated()
                    }.onFailure {
                        triggerEvent(it.userFacingMessage("Couldn't add product"))
                    }
            }
        }

        fun restock(
            productId: String,
            stockQuantity: Int,
            onSaved: () -> Unit,
        ) {
            viewModelScope.launch {
                adminRepository
                    .restock(productId, stockQuantity)
                    .onSuccess { onSaved() }
                    .onFailure { triggerEvent(it.userFacingMessage("Couldn't update stock")) }
            }
        }

        fun deleteProduct(
            productId: String,
            onDeleted: () -> Unit,
        ) {
            viewModelScope.launch {
                adminRepository
                    .deleteProduct(productId)
                    .onSuccess {
                        triggerEvent("Product removed")
                        refreshInventory()
                        onDeleted()
                    }.onFailure {
                        triggerEvent(it.userFacingMessage("Couldn't remove product"))
                    }
            }
        }

        /**
         * Applies staged inventory edits one request per item. The restock API
         * writes absolute quantities, so it first checks that nobody changed
         * these products since the screen loaded and aborts if they did.
         */
        fun saveInventoryChanges(
            branchId: String,
            baseline: Map<String, Int>,
            restocks: Map<String, Int>,
            deletions: Set<String>,
            onComplete: (Boolean) -> Unit,
        ) {
            viewModelScope.launch {
                val latest = adminRepository.getBranchDetail(branchId).getOrNull()
                if (latest != null) {
                    val latestStock = latest.products.associate { it.id to it.stock }
                    val diverged =
                        restocks.keys.any { id ->
                            latestStock.containsKey(id) && latestStock[id] != baseline[id]
                        }
                    if (diverged) {
                        triggerEvent("Inventory changed on another device. Review the latest quantities and try again.")
                        _branchDetail.value = UiState.Ready(latest)
                        onComplete(false)
                        return@launch
                    }
                }

                val errors = mutableListOf<String>()
                deletions.forEach { id ->
                    adminRepository
                        .deleteProduct(id)
                        .onFailure { errors += it.userFacingMessage("Couldn't remove product") }
                }
                restocks.forEach { (id, quantity) ->
                    adminRepository
                        .restock(id, quantity)
                        .onFailure { errors += it.userFacingMessage("Couldn't update stock") }
                }

                if (errors.isEmpty()) {
                    triggerEvent("Inventory updated")
                    refreshAll()
                    onComplete(true)
                } else {
                    triggerEvent(errors.first())
                    refreshInventory()
                    openBranch(branchId)
                    onComplete(false)
                }
            }
        }

        fun registerDevice(
            branchId: String,
            name: String,
            onRegistered: (DeviceRegistration) -> Unit,
        ) {
            viewModelScope.launch {
                adminRepository
                    .registerDevice(branchId, name)
                    .onSuccess { onRegistered(it) }
                    .onFailure { triggerEvent(it.userFacingMessage("Couldn't register device")) }
            }
        }

        fun isBiometricsAvailable(context: Context): Boolean = biometricRepository.isBiometricAvailable(context)

        fun showBiometricPrompt(
            activity: FragmentActivity,
            onSuccess: () -> Unit = {},
            onFailure: () -> Unit = {},
        ) {
            biometricRepository.showBiometricPrompt(
                activity = activity,
                onFailure = onFailure,
                onSuccess = onSuccess,
            )
        }

        fun updateTheme(themePreference: ThemePreference) {
            viewModelScope.launch {
                settingsPreferencesRepository.updateThemePreference(themePreference)
            }
        }

        fun updateNotificationPreferences(lowStockAlerts: Boolean? = null) {
            viewModelScope.launch {
                settingsPreferencesRepository.updateNotificationPreference(
                    lowStockAlerts = lowStockAlerts,
                )
            }
        }

        fun updateBiometricPreference(enabled: Boolean) {
            viewModelScope.launch {
                settingsPreferencesRepository.updateBiometricPreference(
                    enabled = enabled,
                )
            }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun promptEnrollBiometric(context: Context) {
            biometricRepository.promptEnrollBiometric(context)
        }

        fun logout() {
            sessionManager.clearSession()
            // The flow collection in init will automatically handle updating the state to Unauthenticated
        }

        fun testNotification(context: Context) {
            val currentPrefs = notificationPreferences.value
            val sample = (inventory.value as? UiState.Ready)?.value?.minByOrNull { it.stock }
            NotificationHelper.showLowStockNotification(
                context = context,
                itemName = sample?.name ?: "Sample Product",
                remainingStock = sample?.stock ?: 0,
                preferences = currentPrefs,
            )
        }

        private var lastSavedDataNoticeAt = 0L

        private fun notifyUsingSavedData() {
            val now = System.currentTimeMillis()
            if (now - lastSavedDataNoticeAt > SAVED_DATA_NOTICE_INTERVAL_MS) {
                lastSavedDataNoticeAt = now
                triggerEvent("Showing saved data — can't reach the server")
            }
        }

        private companion object {
            const val SAVED_DATA_NOTICE_INTERVAL_MS = 15_000L
        }
    }
