package com.sixteenhalves.orangeadmin.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sixteenhalves.orangeadmin.components.shared.SharedTextField

@Composable
fun BranchLocationScreen(
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var county by remember { mutableStateOf("") }
    var constituency by remember { mutableStateOf("") }
    var ward by remember { mutableStateOf("") }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Set Location",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter the location details for this branch.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        SharedTextField(
            value = county,
            onValueChange = { county = it },
            label = "County",
            placeHolderText = "e.g. Nairobi",
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        SharedTextField(
            value = constituency,
            onValueChange = { constituency = it },
            label = "Constituency",
            placeHolderText = "e.g. Westlands",
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        SharedTextField(
            value = ward,
            onValueChange = { ward = it },
            label = "Ward",
            placeHolderText = "e.g. Parklands",
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                onComplete(listOf(ward, constituency, county).joinToString(", ") { it.trim() })
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            enabled = county.isNotBlank() && constituency.isNotBlank() && ward.isNotBlank()
        ) {
            Text("Save Branch", style = MaterialTheme.typography.titleMedium)
        }
    }
}

