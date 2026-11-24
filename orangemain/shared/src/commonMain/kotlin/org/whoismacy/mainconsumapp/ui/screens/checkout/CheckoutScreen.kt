package org.whoismacy.mainconsumapp.ui.screens.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import orangemain.shared.generated.resources.Res
import orangemain.shared.generated.resources.close
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.Product
import org.whoismacy.mainconsumapp.data.model.SaleItem
import org.whoismacy.mainconsumapp.ui.components.NumPad
import org.whoismacy.mainconsumapp.ui.components.ReceiptView
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@Composable
fun CheckoutScreen(
    totalAmount: Double,
    onNavigateBack: () -> Unit,
    viewModel: CheckoutViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val amountReceived = state.amountReceivedText.toDoubleOrNull() ?: 0.0
    val change = if (amountReceived >= totalAmount) amountReceived - totalAmount else 0.0
    
    val canComplete = amountReceived >= totalAmount
    
    val mockCartItems = listOf(
        CartItem(
            product = Product(id = "1", name = "Milk", price = 85.0, category = "Beverages", stockQuantity = 10, createdAt = "", updatedAt = "", barcode = "", costPrice = 0.0, description = "", imageUrl = "", isActive = true, synced = true),
            quantity = 2
        )
    )

    if (state.showReceipt && state.completedSale != null) {
        Dialog(
            onDismissRequest = { 
                viewModel.hideReceipt()
                onNavigateBack()
            },
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
                        IconButton(onClick = { 
                            viewModel.hideReceipt()
                            onNavigateBack()
                        }) {
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
                            sale = state.completedSale!!,
                            items = state.cartItems.ifEmpty { mockCartItems }.map { cartItem ->
                                SaleItem(
                                    id = "",
                                    saleId = state.completedSale!!.id,
                                    productId = cartItem.product.id,
                                    productName = cartItem.product.name,
                                    quantity = cartItem.quantity,
                                    unitPrice = cartItem.product.price,
                                    totalPrice = cartItem.product.price * cartItem.quantity
                                )
                            }
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { /* Print action */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Print")
                        }
                        Button(
                            onClick = { 
                                viewModel.hideReceipt()
                                onNavigateBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PAYMENT",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Total: ${CurrencyFormatter.format(totalAmount)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Payment Method Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isCash = state.selectedPaymentMethod == PaymentMethod.CASH
                    
                    if (isCash) {
                        Button(
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.CASH) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💵 CASH")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.CASH) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("💵 CASH")
                        }
                    }
                    
                    val isMpesa = state.selectedPaymentMethod == PaymentMethod.MPESA
                    if (isMpesa) {
                        Button(
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.MPESA) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📱 M-PESA")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.setPaymentMethod(PaymentMethod.MPESA) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📱 M-PESA")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (state.selectedPaymentMethod == PaymentMethod.CASH) {
                    Text("Amount Received:", style = MaterialTheme.typography.bodyLarge)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = if (state.amountReceivedText.isEmpty()) "KES 0.00" else "KES ${state.amountReceivedText}",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Quick Cash Amounts
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.onAmountReceivedChanged(totalAmount.toInt().toString()) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exact")
                        }
                        OutlinedButton(
                            onClick = { viewModel.onAmountReceivedChanged("500") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("500")
                        }
                        OutlinedButton(
                            onClick = { viewModel.onAmountReceivedChanged("1000") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1000")
                        }
                        OutlinedButton(
                            onClick = { viewModel.onAmountReceivedChanged("2000") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("2000")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NumPad(
                        onNumberClick = { char -> 
                            if (char == "." && state.amountReceivedText.contains(".")) return@NumPad
                            viewModel.onAmountReceivedChanged(state.amountReceivedText + char)
                        },
                        onClearClick = { viewModel.onAmountReceivedChanged("") },
                        onBackspaceClick = {
                            if (state.amountReceivedText.isNotEmpty()) {
                                viewModel.onAmountReceivedChanged(state.amountReceivedText.dropLast(1))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Change: ${CurrencyFormatter.format(change)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (canComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else {
                    // M-PESA Integration
                    Text("Enter Customer Phone Number for STK Push:", style = MaterialTheme.typography.bodyLarge)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = if (state.amountReceivedText.isEmpty()) "07..." else state.amountReceivedText,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else {
                        NumPad(
                            onNumberClick = { char -> 
                                if (char != ".") viewModel.onAmountReceivedChanged(state.amountReceivedText + char)
                            },
                            onClearClick = { viewModel.onAmountReceivedChanged("") },
                            onBackspaceClick = {
                                if (state.amountReceivedText.isNotEmpty()) {
                                    viewModel.onAmountReceivedChanged(state.amountReceivedText.dropLast(1))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                    
                    state.paymentStatus?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    }
                    
                    state.paymentError?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { 
                        if (state.selectedPaymentMethod == PaymentMethod.CASH) {
                            viewModel.completeCashSale(totalAmount)
                        } else {
                            viewModel.sendStkPush(totalAmount)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !state.isProcessing && if (state.selectedPaymentMethod == PaymentMethod.CASH) canComplete else state.amountReceivedText.length >= 9
                ) {
                    Text(
                        text = if (state.selectedPaymentMethod == PaymentMethod.MPESA) "SEND STK PUSH" else "COMPLETE SALE",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}