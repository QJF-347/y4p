package com.sixteenhalves.orangeadmin.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sixteenhalves.orangeadmin.domain.MainScreenNavigationItems
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun MainAppShell(
    backStack: MutableList<AppRoute>,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentRoute = backStack.last()
    val snackbarHostState = mainViewModel.snackbarHostState

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            MainScreenNavigationItems.entries.forEach {
                val isSelected =
                    when (currentRoute) {
                        is BranchRoutes -> (it.route == MainRoutes.Branches) || (it.route == BranchRoutes.Home)
                        is SettingsRoutes -> (it.route == MainRoutes.Settings) || (it.route == SettingsRoutes.Home)
                        is InventoryRoutes -> (it.route == InventoryRoutes.Home)
                        else -> it.route == currentRoute
                    }
                item(
                    onClick = { backStack.add(it.route) },
                    label = { Text(it.title) },
                    icon = {
                        Icon(
                            painter = painterResource(it.icon),
                            contentDescription = null,
                        )
                    },
                    selected = isSelected,
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                content()
            }
        }
    }
}
