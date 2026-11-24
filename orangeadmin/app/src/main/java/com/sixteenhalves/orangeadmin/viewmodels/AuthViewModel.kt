package com.sixteenhalves.orangeadmin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenhalves.orangeadmin.domain.ApplicationState
import com.sixteenhalves.orangeadmin.domain.EventManager
import com.sixteenhalves.orangeadmin.domain.SessionManager
import com.sixteenhalves.orangeadmin.ntwk.CreateUserRequest
import com.sixteenhalves.orangeadmin.ntwk.DeleteAccountRequest
import com.sixteenhalves.orangeadmin.ntwk.NetworkService
import com.sixteenhalves.orangeadmin.ntwk.RefreshRequest
import com.sixteenhalves.orangeadmin.ntwk.parseApiError
import com.sixteenhalves.orangeadmin.ntwk.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val networkService: NetworkService,
        private val sessionManager: SessionManager
    ) : ViewModel() {
        private val _authApplicationState = MutableStateFlow<ApplicationState>(ApplicationState.Idle)
        val authApplicationState = _authApplicationState.asStateFlow()

        private val _userState = MutableStateFlow(AuthRegisterFlowData())
        val userState = _userState.asStateFlow()

        private fun triggerEvent(message: String) {
            EventManager.triggerEvent(EventManager.AppEvent.ShowEvent(message))
        }

        fun updateEmail(email: String) {
            _userState.update { it.copy(email = email) }
        }

        fun updateName(name: String) {
            _userState.update { it.copy(name = name) }
        }

        fun updatePassword(password: String) {
            _userState.update { it.copy(password = password) }
        }

        fun updateCompanyDesc(desc: String) {
            _userState.update { it.copy(companyDescription = desc) }
        }

        fun updateCompanyName(name: String) {
            _userState.update { it.copy(companyName = name) }
        }

        fun updateImage(file: File) {
            _userState.update { it.copy(image = file.absolutePath) }
        }
        
        fun resetState() {
            _authApplicationState.value = ApplicationState.Idle
        }

        fun registerToServer() {
            if (sessionManager.getAccessToken().isNullOrBlank()) {
                // Account creation is invite-only: POST /auth/users requires an
                // authenticated OWNER, so never fire this before sign-in.
                triggerEvent("Admin accounts are created by your business owner. Sign in with the invite they shared with you.")
                return
            }
            val currentState = userState.value
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response =
                        networkService.createUser(
                            CreateUserRequest(
                                name = currentState.name,
                                email = currentState.email,
                                password = currentState.password,
                                role = "OWNER",
                            ),
                        )

                    if (response.isSuccessful) {
                        triggerEvent("Account Successfully Created🎊")
                        _authApplicationState.value = ApplicationState.Authenticated()
                    } else {
                        _authApplicationState.value =
                            ApplicationState.Error(parseApiError(response).message)
                    }
                } catch (e: Exception) {
                    _authApplicationState.value =
                        ApplicationState.Error(e.toUserMessage("An error occurred while trying to create your account."))
                } finally {
                    updatePassword("0")
                }
            }
        }

        fun loginToServer(
            email: String,
            password: String,
        ) {
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response =
                        networkService
                            .loginUser(AuthLoginFlowData(email = email, password = password))
                    if (response.isSuccessful) {
                        response.body()?.let { authResponse ->
                            authResponse.accessToken?.let { token ->
                                val refreshToken = authResponse.refreshToken ?: ""
                                sessionManager.saveTokens(token, refreshToken)
                            }
                        }
                        triggerEvent("Login Successful")
                        _authApplicationState.value = ApplicationState.Authenticated()
                    } else {
                        _authApplicationState.value =
                            ApplicationState.Error(parseApiError(response).message)
                    }
                } catch (e: Exception) {
                    _authApplicationState.value =
                        ApplicationState.Error(e.toUserMessage("An error occurred while trying to log in."))
                }
            }
        }

        fun logout() {
            val refreshToken = sessionManager.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                // No server-side session to revoke; just drop local state.
                sessionManager.clearSession()
                triggerEvent("Logged out successfully")
                _authApplicationState.value = ApplicationState.Idle
                return
            }
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response = networkService.logoutUser(RefreshRequest(refreshToken))
                    if (response.isSuccessful) {
                        sessionManager.clearSession()
                        triggerEvent("Logged out successfully")
                        _authApplicationState.value = ApplicationState.Idle
                    } else {
                        _authApplicationState.value =
                            ApplicationState.Error(parseApiError(response).message)
                    }
                } catch (e: Exception) {
                    _authApplicationState.value =
                        ApplicationState.Error(e.toUserMessage("An error occurred while trying to log out."))
                }
            }
        }

        fun deleteAccount(password: String) {
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response = networkService.deleteUser(DeleteAccountRequest(password))
                    if (response.isSuccessful) {
                        sessionManager.clearSession()
                        triggerEvent("Account deleted successfully")
                        _authApplicationState.value = ApplicationState.Idle
                    } else {
                        _authApplicationState.value =
                            ApplicationState.Error(parseApiError(response).message)
                    }
                } catch (e: Exception) {
                    _authApplicationState.value =
                        ApplicationState.Error(e.toUserMessage("An error occurred while trying to delete your account."))
                }
            }
        }
    }

data class AuthRegisterFlowData(
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val companyName: String = "",
    val companyDescription: String = "",
    val image: String? = null,
)

data class AuthLoginFlowData(
    val email: String = "",
    val password: String = "",
)
