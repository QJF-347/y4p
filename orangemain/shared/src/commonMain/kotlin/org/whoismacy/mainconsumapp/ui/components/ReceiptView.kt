package org.whoismacy.mainconsumapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.PaymentStatus
import org.whoismacy.mainconsumapp.data.model.Sale
import org.whoismacy.mainconsumapp.data.model.SaleItem
import org.whoismacy.mainconsumapp.util.CurrencyFormatter

@Composable
fun ReceiptView(
    sale: Sale,
    items: List<SaleItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "🍊 ORANGEMAIN STORE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(text = "123 Main Street", color = Color.Black, fontSize = 12.sp)
        Text(text = "Tel: +254 700 000000", color = Color.Black, fontSize = 12.sp)
        
        Spacer(modifier = Modifier.height(16.dp))
        ReceiptDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        // Transaction Info
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Date: ${sale.createdAt}", color = Color.Black, fontSize = 12.sp) // Needs formatting in real app
            Text(text = "Receipt #: ${sale.receiptNumber}", color = Color.Black, fontSize = 12.sp)
            Text(text = "Cashier: Admin", color = Color.Black, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        ReceiptDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        // Items
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${item.productName} x${item.quantity}",
                    modifier = Modifier.weight(1f),
                    color = Color.Black,
                    fontSize = 12.sp
                )
                Text(
                    text = CurrencyFormatter.format(item.totalPrice),
                    color = Color.Black,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        ReceiptDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        // Totals
        ReceiptRow("Subtotal:", CurrencyFormatter.format(sale.subtotal))
        ReceiptRow("VAT (16%):", CurrencyFormatter.format(sale.taxAmount))
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TOTAL:",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = CurrencyFormatter.format(sale.totalAmount),
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        ReceiptDivider()
        Spacer(modifier = Modifier.height(8.dp))
        
        // Payment Info
        if (sale.paymentMethod == PaymentMethod.SPLIT) {
            ReceiptRow("Cash:", CurrencyFormatter.format(sale.cashAmount))
            ReceiptRow("M-PESA:", CurrencyFormatter.format(sale.totalAmount - sale.cashAmount))
            sale.mpesaRef?.let { ReceiptRow("M-PESA Ref:", it) }
            if (sale.paymentStatus != PaymentStatus.PAID) {
                ReceiptRow("M-PESA leg:", sale.paymentStatus.name)
            }
        } else {
            ReceiptRow("${sale.paymentMethod}:", CurrencyFormatter.format(sale.amountReceived))
            ReceiptRow("Change:", CurrencyFormatter.format(sale.changeAmount))
            if (sale.paymentMethod == PaymentMethod.MPESA) {
                sale.mpesaRef?.let { ReceiptRow("M-PESA Ref:", it) }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        ReceiptDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer
        Text(text = "Thank you for shopping!", color = Color.Black, fontSize = 12.sp)
        Text(text = "COME AGAIN 😊", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
    }
}

@Composable
private fun ReceiptDivider() {
    Text(
        text = "-".repeat(45), // Simple dashed line for receipt feel
        color = Color.Black,
        maxLines = 1,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = Color.Black,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.Black,
            fontSize = 12.sp
        )
    }
}
