package com.sixteenhalves.orangeadmin.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.sixteenhalves.orangeadmin.components.shared.QrCodeImage
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
                val shareText = device.shareText()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "In the POS app open Device setup and tap Scan QR code, or share this text to the device and use Paste device details. The token is shown only once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QrCodeImage(
                        content = shareText,
                        modifier =
                            Modifier
                                .size(200.dp)
                                .align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text =
                            "Device code: ${device.code}\n" +
                                "Device token: ${device.token}\n" +
                                "Device id: ${device.deviceId}\n" +
                                "Branch id: ${device.branchId}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Device credentials", shareText))
                                Toast.makeText(context, "Credentials copied", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("Copy details")
                        }
                        TextButton(onClick = { context.shareDeviceDetails(shareText) }) {
                            Text("Share")
                        }
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

private const val BACKEND_URL = "https://y4p.onrender.com"

/**
 * The block POS setup understands: it is copied to the clipboard, sent through
 * the share sheet and encoded in the QR code, and parsed by the POS app.
 */
internal fun DeviceRegistration.shareText(): String =
    "Backend URL: $BACKEND_URL\n" +
        "Device token: $token\n" +
        "Device id: $deviceId\n" +
        "Branch id: $branchId"

private fun Context.shareDeviceDetails(text: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "POS device details")
            putExtra(Intent.EXTRA_TEXT, text)
        }
    startActivity(Intent.createChooser(send, "Share device details"))
}
