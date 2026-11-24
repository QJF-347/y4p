package com.sixteenhalves.orangeadmin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixteenhalves.orangeadmin.domain.ApplicationState
import com.sixteenhalves.orangeadmin.domain.EventManager
import com.sixteenhalves.orangeadmin.domain.SessionManager
import com.sixteenhalves.orangeadmin.ntwk.NetworkService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val networkService: NetworkService,
        private val sessionManager: SessionManager
    ) : ViewModel() {
        private val _authApplicationState = MutableStateFlow<ApplicationState>(ApplicationState.Authenticated("f29hf2h92"))
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
            val currentState = userState.value
            var imagePart: MultipartBody.Part? = null
            currentState.image?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val requestFile = file.asRequestBody("image/webp".toMediaTypeOrNull())
                    imagePart = MultipartBody.Part.createFormData("logo", file.name, requestFile)
                }
            }
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response =
                        networkService.createUser(
                            email = currentState.email,
                            name = currentState.name,
                            password = currentState.password,
                            companyName = currentState.companyName,
                            companyDescription = currentState.companyDescription,
                            logo = imagePart,
                        )

                    if (response.isSuccessful) {
                        triggerEvent("Account Successfully Created🎊")
                        _authApplicationState.value = ApplicationState.Authenticated()
                    } else {
                        _authApplicationState.value = ApplicationState.Error("An error occurred; ${response.message()}")
                    }
                } catch (e: Exception) {
                    _authApplicationState.value = ApplicationState.Error("An error occurred while trying to create your account.")
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
                            authResponse.token?.let { token ->
                                val refreshToken = authResponse.refreshToken ?: ""
                                sessionManager.saveTokens(token, refreshToken)
                            }
                        }
                        triggerEvent("Login Successful")
                        _authApplicationState.value = ApplicationState.Authenticated()
                    } else {
                        _authApplicationState.value = ApplicationState.Error("${response.message()}; An error occurred while trying to log in")
                    }
                } catch (e: Exception) {
                    _authApplicationState.value = ApplicationState.Error("Network error occurred: ${e.message}")
                }
            }
        }

        fun logout() {
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response = networkService.logoutUser()
                    if (response.isSuccessful) {
                        sessionManager.clearSession()
                        triggerEvent("Logged out successfully")
                        _authApplicationState.value = ApplicationState.Idle
                    } else {
                        _authApplicationState.value = ApplicationState.Error("${response.message()}; An error occurred while trying to log out")
                    }
                } catch (e: Exception) {
                    _authApplicationState.value = ApplicationState.Error("Network error occurred: ${e.message}")
                }
            }
        }

        fun deleteAccount() {
            _authApplicationState.value = ApplicationState.Loading
            viewModelScope.launch {
                try {
                    val response = networkService.deleteUser()
                    if (response.isSuccessful) {
                        sessionManager.clearSession()
                        triggerEvent("Account deleted successfully")
                        _authApplicationState.value = ApplicationState.Idle
                    } else {
                        _authApplicationState.value = ApplicationState.Error("${response.message()}; An error occurred while trying to delete account")
                    }
                } catch (e: Exception) {
                    _authApplicationState.value = ApplicationState.Error("Network error occurred: ${e.message}")
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
