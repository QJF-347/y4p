package com.sixteenhalves.orangeadmin.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sixteenhalves.orangeadmin.components.shared.SharedTextField
import com.sixteenhalves.orangeadmin.repository.DeviceRegistration
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

@Composable
fun BranchDeviceScreen(
    mainViewModel: MainViewModel,
    branchId: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = true,
) {
    var deviceName by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf<DeviceRegistration?>(null) }
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Add POS Device",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Register a new POS device for this branch. You'll receive a device code and provisioning token to activate it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        SharedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = "Device Name",
            placeHolderText = "e.g. Front Register 1",
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (canManage) {
            Button(
                onClick = {
                    mainViewModel.registerDevice(
                        branchId = branchId,
                        name = deviceName.trim(),
                        onRegistered = { registration = it },
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                enabled = deviceName.isNotBlank() && registration == null,
            ) {
                Text("Register Device", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                text = "Read-only access. Ask an owner to register a device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    registration?.let { device ->
        AlertDialog(
            onDismissRequest = {
                registration = null
                onSave()
            },
            title = {
                Text(
                    text = "Device Registered",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Use these credentials to provision the POS device. The token is shown only once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Device code: ${device.code}\nProvisioning token: ${device.token}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Device credentials", "${device.code} ${device.token}"),
                            )
                            Toast.makeText(context, "Credentials copied", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text("Copy credentials")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        registration = null
                        onSave()
                    },
                ) {
                    Text("Done")
                }
            },
        )
    }
}
