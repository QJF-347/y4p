package com.sixteenhalves.orangeadmin.domain

sealed interface ApplicationState {
    data object Idle : ApplicationState
    
    data object Loading : ApplicationState
    
    data class Authenticated(val userId: String? = null) : ApplicationState
    
    data object Unauthenticated : ApplicationState

    data class Error(val message: String) : ApplicationState
}