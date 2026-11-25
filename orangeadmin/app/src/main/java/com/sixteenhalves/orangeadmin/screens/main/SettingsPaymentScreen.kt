package com.sixteenhalves.orangeadmin.screens.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenhalves.orangeadmin.R
import com.sixteenhalves.orangeadmin.domain.PaymentSettings
import com.sixteenhalves.orangeadmin.viewmodels.MainViewModel

/**
 * Records M-Pesa/Daraja credentials for a future deployment. The values stay in
 * this device's DataStore and are never sent to the API; the backend reads its
 * own Daraja credentials from the environment. Nothing here changes how
 * payments behave today.
 */
@Composable
fun SettingsPaymentScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val stored by mainViewModel.paymentSettings.collectAsStateWithLifecycle()
    var draft by remember(stored) { mutableStateOf(stored) }
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Payment Settings",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "M-Pesa and Daraja details kept on this device for a future deployment. " +
                "They are not sent to the server and do not change how payments work yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                PaymentField(
                    label = "Paybill number",
                    value = draft.paybillNumber,
                    onValueChange = { draft = draft.copy(paybillNumber = it) },
                    keyboardType = KeyboardType.Number,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaymentField(
                    label = "Buy Goods till number",
                    value = draft.buyGoodsTill,
                    onValueChange = { draft = draft.copy(buyGoodsTill = it) },
                    keyboardType = KeyboardType.Number,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaymentField(
                    label = "Daraja consumer key",
                    value = draft.consumerKey,
                    onValueChange = { draft = draft.copy(consumerKey = it) },
                    masked = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaymentField(
                    label = "Daraja consumer secret",
                    value = draft.consumerSecret,
                    onValueChange = { draft = draft.copy(consumerSecret = it) },
                    masked = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaymentField(
                    label = "Daraja passkey",
                    value = draft.passkey,
                    onValueChange = { draft = draft.copy(passkey = it) },
                    masked = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PaymentField(
                    label = "Shortcode",
                    value = draft.shortcode,
                    onValueChange = { draft = draft.copy(shortcode = it) },
                    keyboardType = KeyboardType.Number,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                mainViewModel.updatePaymentSettings(draft)
                Toast.makeText(context, "Saved on this device", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun PaymentField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var revealed by remember { mutableStateOf(false) }
    val hidden = masked && !revealed

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (masked) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        painter =
                            painterResource(
                                if (revealed) R.drawable.baseline_visibility_off_24 else R.drawable.baseline_visibility_24,
                            ),
                        contentDescription = if (revealed) "Hide $label" else "Show $label",
                    )
                }
            }
        },
    )
}
