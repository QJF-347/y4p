package org.whoismacy.mainconsumapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import orangemain.shared.generated.resources.Res
import orangemain.shared.generated.resources.clear
import orangemain.shared.generated.resources.search
import org.jetbrains.compose.resources.painterResource

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(8.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(painterResource(Res.drawable.search), contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(Res.drawable.clear), contentDescription = "Clear search")
                }
            }
        },
        singleLine = true
    )
}
