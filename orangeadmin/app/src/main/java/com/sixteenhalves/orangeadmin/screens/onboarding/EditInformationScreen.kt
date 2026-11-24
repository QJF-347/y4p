package com.sixteenhalves.orangeadmin.screens.onboarding

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.components.shared.SharedTextField
import com.sixteenhalves.orangeadmin.viewmodels.AuthRegisterFlowData
import com.sixteenhalves.orangeadmin.viewmodels.AuthViewModel

class EditInformationState(initialData: AuthRegisterFlowData) {
    val isPasswordEditable: Boolean = initialData.password.isEmpty()

    var name by mutableStateOf(initialData.name)
    var email by mutableStateOf(initialData.email)
    var password by mutableStateOf(initialData.password)
    var confirmPassword by mutableStateOf(initialData.password)
    var companyName by mutableStateOf(initialData.companyName)
    var companyDesc by mutableStateOf(initialData.companyDescription)

    val isNameValid: Boolean
        get() = name.trim().length >= 4

    val isEmailValid: Boolean
        get() = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    val isPasswordValid: Boolean
        get() = !isPasswordEditable || password.length >= 8

    val doPasswordsMatch: Boolean
        get() = !isPasswordEditable || (password == confirmPassword && confirmPassword.isNotEmpty())

    val canProceed: Boolean
        get() = isNameValid && isEmailValid && isPasswordValid && doPasswordsMatch

    val nameError: String?
        get() = if (name.isNotEmpty() && !isNameValid) "Please enter at least 4 characters." else null

    val emailError: String?
        get() = if (email.isNotEmpty() && !isEmailValid) "Enter a valid email address." else null

    val passwordError: String?
        get() = if (isPasswordEditable && password.isNotEmpty() && !isPasswordValid) "Password should be at least 8 characters." else null

    val confirmPasswordError: String?
        get() = if (isPasswordEditable && confirmPassword.isNotEmpty() && !doPasswordsMatch) "Passwords do not match." else null
}

@Composable
fun rememberEditInformationState(initialData: AuthRegisterFlowData) = remember(initialData) { EditInformationState(initialData) }

@Composable
fun EditInformationScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userState by authViewModel.userState.collectAsStateWithLifecycle()
    val editState = rememberEditInformationState(userState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Edit Information",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Full Name",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.name,
                onValueChange = { editState.name = it },
                placeHolderText = "Full Name"
            )
            if (editState.nameError != null) {
                Text(
                    text = editState.nameError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Email Address",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.email,
                onValueChange = { editState.email = it },
                placeHolderText = "Email Address"
            )
            if (editState.emailError != null) {
                Text(
                    text = editState.emailError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Password",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.password,
                onValueChange = { if (editState.isPasswordEditable) editState.password = it },
                placeHolderText = "Password",
                isPassword = true,
                enabled = editState.isPasswordEditable
            )
            if (editState.isPasswordEditable && editState.passwordError != null) {
                Text(
                    text = editState.passwordError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!editState.isPasswordEditable) {
                Text(
                    text = "Password cannot be changed here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Confirm Password",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.confirmPassword,
                onValueChange = { if (editState.isPasswordEditable) editState.confirmPassword = it },
                placeHolderText = "Confirm Password",
                isPassword = true,
                enabled = editState.isPasswordEditable
            )
            if (editState.isPasswordEditable && editState.confirmPasswordError != null) {
                Text(
                    text = editState.confirmPasswordError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Company Name",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.companyName,
                onValueChange = { editState.companyName = it },
                placeHolderText = "Company Name"
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Company Description",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedTextField(
                value = editState.companyDesc,
                onValueChange = { editState.companyDesc = it },
                placeHolderText = "Company Description",
                singleLine = false
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                authViewModel.updateName(editState.name.trim())
                authViewModel.updateEmail(editState.email.trim())
                if (editState.isPasswordEditable) {
                    authViewModel.updatePassword(editState.password)
                }
                authViewModel.updateCompanyName(editState.companyName.trim())
                authViewModel.updateCompanyDesc(editState.companyDesc.trim())
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = editState.canProceed
        ) {
            Text(
                text = "Save Changes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
