package com.sixteenhalves.orangeadmin.screens.onboarding

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixteenhalves.orangeadmin.components.shared.SharedTextField
import com.sixteenhalves.orangeadmin.viewmodels.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    onForgot: () -> Unit = {},
    onSignUp: () -> Unit = {},
) {
    val state = rememberLoginState()
    val scrollState = rememberScrollState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderSection()

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InputFieldGroup(
                        label = "Email Address",
                        value = state.email,
                        onValueChange = { state.email = it },
                        placeHolderText = "name@company.com",
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        errorMessage = state.emailError,
                    )

                    Column {
                        InputFieldGroup(
                            label = "Password",
                            value = state.password,
                            onValueChange = { state.password = it },
                            placeHolderText = "Enter your password",
                            isPassword = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (state.canProceed) {
                                            authViewModel.loginToServer(
                                                email = state.email.trim(),
                                                password = state.password,
                                            )
                                        }
                                    },
                                ),
                            errorMessage = state.passwordError,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onForgot) {
                                Text(
                                    text = "Forgot Password?",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (state.canProceed) {
                                authViewModel.loginToServer(
                                    email = state.email.trim(),
                                    password = state.password,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = state.canProceed,
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSignUp) {
                    Text(
                        text = "Sign up",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

class LoginState {
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    val isEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val isPasswordValid: Boolean
        get() = password.length >= 8

    val canProceed: Boolean
        get() = isEmailValid && isPasswordValid

    val emailError: String?
        get() = if (email.isNotEmpty() && !isEmailValid) "Enter a valid email address." else null

    val passwordError: String?
        get() = if (password.isNotEmpty() && !isPasswordValid) "Password should be at least 8 characters." else null
}

@Composable
fun rememberLoginState() = remember { LoginState() }

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sign in to continue to your admin dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InputFieldGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeHolderText: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    errorMessage: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SharedTextField(
            value = value,
            onValueChange = onValueChange,
            placeHolderText = placeHolderText,
            isPassword = isPassword,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
