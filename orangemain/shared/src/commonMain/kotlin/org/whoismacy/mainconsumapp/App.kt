package org.whoismacy.mainconsumapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import orangemain.shared.generated.resources.Res
import orangemain.shared.generated.resources.history
import orangemain.shared.generated.resources.inventory
import orangemain.shared.generated.resources.shopping_cart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.whoismacy.mainconsumapp.data.sync.SyncService
import org.whoismacy.mainconsumapp.ui.navigation.Route
import org.whoismacy.mainconsumapp.ui.screens.checkout.CheckoutScreen
import org.whoismacy.mainconsumapp.ui.screens.history.HistoryScreen
import org.whoismacy.mainconsumapp.ui.screens.pos.PosScreen
import org.whoismacy.mainconsumapp.ui.screens.products.ProductsScreen
import org.whoismacy.mainconsumapp.ui.screens.setup.SetupScreen
import org.whoismacy.mainconsumapp.ui.theme.OrangeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    OrangeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val isTopLevelRoute = currentDestination?.hierarchy?.any {
                it.hasRoute<Route.Pos>() || it.hasRoute<Route.Products>() || it.hasRoute<Route.History>()
            } == true

            val showBottomBar = isTopLevelRoute || currentDestination == null

            // Keep the till in step with the server: push local edits/sales, then
            // pull catalog and server-owned M-Pesa outcomes. Runs on launch and
            // every minute while the app is open. No-ops if not provisioned.
            val syncService: SyncService = koinInject()
            val syncScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                while (true) {
                    syncService.syncCycle()
                    delay(60_000)
                }
            }

            Scaffold(
                topBar = {
                    if (isTopLevelRoute) {
                        TopAppBar(
                            title = { Text("Orange POS") },
                            actions = {
                                TextButton(
                                    onClick = {
                                        syncScope.launch {
                                            syncService.syncCycle()
                                        }
                                    }
                                ) {
                                    Text("Sync")
                                }
                                TextButton(onClick = { navController.navigate(Route.Setup) { launchSingleTop = true } }) {
                                    Text("Device setup")
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(painterResource(Res.drawable.shopping_cart), contentDescription = "POS") },
                                label = { Text("POS") },
                                selected = currentDestination?.hierarchy?.any { it.hasRoute<Route.Pos>() } == true,
                                onClick = {
                                    navController.navigate(Route.Pos) {
                                        popUpTo(navController.graph.findStartDestination().route ?: "") {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(painterResource(Res.drawable.inventory), contentDescription = "Products") },
                                label = { Text("Products") },
                                selected = currentDestination?.hierarchy?.any { it.hasRoute<Route.Products>() } == true,
                                onClick = {
                                    navController.navigate(Route.Products) {
                                        popUpTo(navController.graph.findStartDestination().route ?: "") {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(painterResource(Res.drawable.history), contentDescription = "History") },
                                label = { Text("History") },
                                selected = currentDestination?.hierarchy?.any { it.hasRoute<Route.History>() } == true,
                                onClick = {
                                    navController.navigate(Route.History) {
                                        popUpTo(navController.graph.findStartDestination().route ?: "") {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Route.Pos,
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    composable<Route.Pos> {
                        PosScreen(
                            onNavigateToCheckout = { totalAmount -> navController.navigate(Route.Checkout(totalAmount)) }
                        )
                    }
                    composable<Route.Checkout> { backStackEntry ->
                        val checkout = backStackEntry.toRoute<Route.Checkout>()
                        CheckoutScreen(
                            totalAmount = checkout.totalAmount,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<Route.Products> {
                        ProductsScreen()
                    }
                    composable<Route.History> {
                        HistoryScreen()
                    }
                    composable<Route.Setup> {
                        SetupScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
