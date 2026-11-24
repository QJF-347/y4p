package com.sixteenhalves.orangeadmin.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sixteenhalves.orangeadmin.viewmodels.AuthViewModel
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun AuthShell(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = mainViewModel.snackbarHostState

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
