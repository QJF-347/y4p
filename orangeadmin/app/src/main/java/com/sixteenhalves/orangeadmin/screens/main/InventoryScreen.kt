package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun InventoryScreen(
    onNewBranch: () -> Unit,
    onNavigateAddNewItem: () -> Unit,
    onNavigateBranch: () -> Unit,
    mainViewModel: MainViewModel,
) {
    val branchesExist =
        mainViewModel
            .branchesExist
            .collectAsStateWithLifecycle()
            .value
    EmptyInventory(onNewBranch)
    if (branchesExist) {
        PopulatedInventory(onNavigateAddNewItem = onNavigateAddNewItem, onNavigateBranches = onNavigateBranch)
    } else {
        PopulatedInventory(onNavigateAddNewItem = onNavigateAddNewItem, onNavigateBranches = onNavigateBranch)
//        EmptyInventory(onNewBranch)
    }
}
