package com.sixteenhalves.orangeadmin.nav

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import com.sixteenhalves.orangeadmin.screens.main.AddInventoryItemScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchAddInventoryScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchDetailScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchDeviceScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchLocationScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchNameScreen
import com.sixteenhalves.orangeadmin.screens.main.BranchesScreen
import com.sixteenhalves.orangeadmin.screens.main.HomeScreen
import com.sixteenhalves.orangeadmin.screens.main.InventoryScreen
import com.sixteenhalves.orangeadmin.screens.main.ReportsScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsAccountScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsAppDetailsScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsBiometricsScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsNotificationsScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsPaymentScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsScreen
import com.sixteenhalves.orangeadmin.screens.main.SettingsThemeScreen
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoutes : AppRoute {
    @Serializable data object HomeRoute : MainRoutes

    @Serializable data object Branches : MainRoutes

    @Serializable data object Reports : MainRoutes

    @Serializable data object Settings : MainRoutes
}

@Serializable
sealed interface InventoryRoutes : AppRoute {
    @Serializable data object Home : InventoryRoutes

    @Serializable data class AddNewItem(
        val branchId: String? = null,
    ) : InventoryRoutes
}

@Serializable
sealed interface BranchRoutes : AppRoute {
    @Serializable data object Home : BranchRoutes

    @Serializable data object Name : BranchRoutes

    @Serializable data class Location(
        val name: String,
    ) : BranchRoutes

    @Serializable data class Device(
        val branchId: String,
    ) : BranchRoutes

    @Serializable data class AddInventory(
        val branchId: String,
    ) : BranchRoutes

    @Serializable data class Detail(
        val branchId: String,
    ) : BranchRoutes
}

@Serializable
sealed interface SettingsRoutes : AppRoute {
    @Serializable data object Home : SettingsRoutes

    @Serializable data object Theme : SettingsRoutes

    @Serializable data object Biometrics : SettingsRoutes

    @Serializable data object Notifications : SettingsRoutes

    @Serializable data object Payment : SettingsRoutes

    @Serializable data object AppDetails : SettingsRoutes

    @Serializable data object Account: SettingsRoutes
}

fun EntryProviderScope<AppRoute>.mainGraph(
    backStack: MutableList<AppRoute>,
    mainViewModel: MainViewModel,
) {
    entry<MainRoutes.HomeRoute> {
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            HomeScreen(
                mainViewModel = mainViewModel,
                onNewBranch =
                    { backStack.add(BranchRoutes.Name) },
                canManage = canManage,
            )
        }
    }

    entry<MainRoutes.Reports> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            ReportsScreen(mainViewModel = mainViewModel)
        }
    }

    entry<InventoryRoutes.Home> {
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            InventoryScreen(
                mainViewModel = mainViewModel,
                onNewBranch = { backStack.add(BranchRoutes.Name) },
                onNavigateAddNewItem = { backStack.add(InventoryRoutes.AddNewItem()) },
                onNavigateBranch = { backStack.add(BranchRoutes.Home)},
                canManage = canManage,
            )
        }
    }

    entry<InventoryRoutes.AddNewItem> { route ->
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            AddInventoryItemScreen(
                mainViewModel = mainViewModel,
                initialBranchId = route.branchId,
                onBack = { backStack.removeLastOrNull() },
                canManage = canManage,
            )
        }
    }

    entry<BranchRoutes.Home> {
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            BranchesScreen(
                mainViewModel = mainViewModel,
                onNewBranch = { backStack.add(BranchRoutes.Name) },
                onBranchClick = { branchId -> backStack.add(BranchRoutes.Detail(branchId)) },
                canManage = canManage,
            )
        }
    }
    entry<BranchRoutes.Name> {
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            if (canManage) {
                BranchNameScreen(
                    onNext = { name ->
                        backStack.add(BranchRoutes.Location(name))
                    },
                )
            } else {
                androidx.compose.material3.Text("Only owners can create branches.")
            }
        }
    }
    entry<BranchRoutes.Location> { route ->
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            if (canManage) {
                BranchLocationScreen(
                    onComplete = { location ->
                        mainViewModel.createBranch(name = route.name, location = location) { branchId ->
                            backStack.removeLastOrNull()
                            backStack.removeLastOrNull()
                            backStack.add(BranchRoutes.Detail(branchId))
                        }
                    },
                )
            } else {
                androidx.compose.material3.Text("Only owners can create branches.")
            }
        }
    }
    entry<BranchRoutes.Device> { route ->
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            BranchDeviceScreen(
                mainViewModel = mainViewModel,
                branchId = route.branchId,
                onSave = { backStack.removeLastOrNull() },
                canManage = canManage,
            )
        }
    }
    entry<BranchRoutes.Detail> { route ->
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            BranchDetailScreen(
                mainViewModel = mainViewModel,
                branchId = route.branchId,
                onAddDevice = { backStack.add(BranchRoutes.Device(route.branchId)) },
                onAddInventory = { backStack.add(BranchRoutes.AddInventory(route.branchId)) },
                onBack = { backStack.removeLastOrNull() },
                canManage = canManage,
            )
        }
    }
    entry<BranchRoutes.AddInventory> { route ->
        val canManage by mainViewModel.isOwner.collectAsStateWithLifecycle()
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            BranchAddInventoryScreen(
                mainViewModel = mainViewModel,
                branchId = route.branchId,
                onBack = { backStack.removeLastOrNull() },
                onAddNewItem = { backStack.add(InventoryRoutes.AddNewItem(route.branchId)) },
                canManage = canManage,
            )
        }
    }
    entry<SettingsRoutes.Home> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            SettingsScreen(
                onNavigateTheme = { backStack.add(SettingsRoutes.Theme) },
                onNavigateBiometrics = { backStack.add(SettingsRoutes.Biometrics) },
                onNavigateNotifications = { backStack.add(SettingsRoutes.Notifications) },
                onNavigatePaymentSettings = { backStack.add(SettingsRoutes.Payment) },
                onNavigateAppDetails = { backStack.add(SettingsRoutes.AppDetails) },
                onNavigateAccountDetails = { backStack.add(SettingsRoutes.Account)},
                mainViewModel = mainViewModel,
            )
        }
    }
    entry<SettingsRoutes.Theme> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) { SettingsThemeScreen(mainViewModel = mainViewModel) }
    }
    entry<SettingsRoutes.Biometrics> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                SettingsBiometricsScreen(mainViewModel = mainViewModel)
            } else {
                androidx.compose.material3.Text("Biometrics requires Android 11+")
            }
        }
    }
    entry<SettingsRoutes.Notifications> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) { SettingsNotificationsScreen(mainViewModel = mainViewModel) }
    }
    entry<SettingsRoutes.Payment> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) { SettingsPaymentScreen(mainViewModel = mainViewModel) }
    }
    entry<SettingsRoutes.AppDetails> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) { SettingsAppDetailsScreen() }
    }

    entry<SettingsRoutes.Account> {
        MainAppShell(backStack = backStack, mainViewModel = mainViewModel) {
            SettingsAccountScreen()
        }
    }
}
