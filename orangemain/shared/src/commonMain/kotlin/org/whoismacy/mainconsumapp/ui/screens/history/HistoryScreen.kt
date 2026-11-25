package org.whoismacy.mainconsumapp.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import orangemain.shared.generated.resources.Res
import orangemain.shared.generated.resources.close
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.PaymentStatus
import org.whoismacy.mainconsumapp.data.model.Sale
import org.whoismacy.mainconsumapp.ui.components.ReceiptView
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Sales History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.filteredSales.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sales history found")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.filteredSales, key = { it.id }) { sale ->
                    SaleItemRow(
                        sale = sale,
                        onClick = { viewModel.selectSale(sale) }
                    )
                }
            }
        }
    }

    if (state.isDetailShowing && state.selectedSale != null) {
        Dialog(
            onDismissRequest = { viewModel.dismissDetail() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f)
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { viewModel.dismissDetail() }) {
                            Icon(painter = painterResource(Res.drawable.close), contentDescription = "Close")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        ReceiptView(
                            sale = state.selectedSale!!,
                            items = state.selectedSaleItems
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { viewModel.dismissDetail() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaleItemRow(
    sale: Sale,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Receipt: ${sale.receiptNumber}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = CurrencyFormatter.format(sale.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val methodLabel = if (sale.paymentMethod == PaymentMethod.SPLIT) {
                "Cash ${CurrencyFormatter.format(sale.cashAmount)} + M-PESA ${CurrencyFormatter.format(sale.totalAmount - sale.cashAmount)}"
            } else {
                sale.paymentMethod.name
            }
            Text(text = "Payment Method: $methodLabel", style = MaterialTheme.typography.bodySmall)
            if (sale.paymentMethod == PaymentMethod.SPLIT && sale.paymentStatus != PaymentStatus.PAID) {
                Text(
                    text = "M-PESA leg: ${sale.paymentStatus.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = "Date: ${sale.createdAt}", style = MaterialTheme.typography.bodySmall)
        }
    }
}