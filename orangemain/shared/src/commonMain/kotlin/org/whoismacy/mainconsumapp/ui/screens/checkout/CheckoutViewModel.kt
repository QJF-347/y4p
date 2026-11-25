package org.whoismacy.mainconsumapp.ui.screens.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.PaymentStatus
import org.whoismacy.mainconsumapp.data.model.Sale
import org.whoismacy.mainconsumapp.data.remote.ApiException
import org.whoismacy.mainconsumapp.data.remote.BackendApi
import org.whoismacy.mainconsumapp.data.repository.CartRepository
import org.whoismacy.mainconsumapp.data.repository.SettingsRepository
import org.whoismacy.mainconsumapp.data.repository.TransactionRepository
import org.whoismacy.mainconsumapp.data.sync.SyncService
import org.whoismacy.mainconsumapp.util.CurrencyFormatter
import org.whoismacy.mainconsumapp.util.UuidV7
import kotlin.math.roundToLong

data class CheckoutState(
    val amountReceivedText: String = "",
    val phoneNumberText: String = "07",
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isProcessing: Boolean = false,
    val paymentStatus: String? = null,
    val paymentError: String? = null,
    val isPaymentComplete: Boolean = false,
    val completedSale: Sale? = null,
    val showReceipt: Boolean = false,
    val cartItems: List<CartItem> = emptyList(),
    /** Cash leg the cashier has typed for a SPLIT sale; the M-PESA leg is derived. */
    val splitCashText: String = "",
    /** Which side the keypad is editing for a SPLIT sale. */
    val splitInputIsCash: Boolean = true,
    /**
     * A SPLIT sale whose M-PESA leg has not settled. The cash is already
     * recorded, so the sale stays PENDING and the prompt can be retried without
     * taking the cash twice.
     */
    val pendingSplitSale: Sale? = null,
    val pendingSplitPhone: String? = null,
)

class CheckoutViewModel(
    private val backendApi: BackendApi,
    private val syncService: SyncService,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val cartRepository: CartRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    init {
        _state.update { it.copy(cartItems = cartRepository.cartItems.value) }
    }

    fun onAmountReceivedChanged(value: String) {
        _state.update { it.copy(amountReceivedText = value, paymentError = null) }
    }

    fun onPhoneNumberChanged(value: String) {
        _state.update { it.copy(phoneNumberText = value, paymentError = null) }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        // Keep the cash amount and the M-PESA number in separate fields so
        // switching method never overwrites either.
        _state.update { it.copy(selectedPaymentMethod = method, paymentError = null) }
    }

    fun onSplitCashChanged(value: String) {
        _state.update { it.copy(splitCashText = value, paymentError = null) }
    }

    fun toggleSplitInputSide() {
        _state.update { it.copy(splitInputIsCash = !it.splitInputIsCash, paymentError = null) }
    }

    /**
     * Records the sale as PENDING, pushes it to the backend, then asks the
     * backend to start the STK push and polls for the server-owned outcome.
     * API_CONTRACT §5: the gateway, not the till, talks to Daraja.
     */
    fun sendStkPush(totalAmount: Double) {
        val phoneNumber = _state.value.phoneNumberText
        val items = cartRepository.cartItems.value
        if (!isPlausibleKenyanMsisdn(phoneNumber)) {
            _state.update { it.copy(paymentError = "Enter a valid Safaricom number, e.g. 0712345678") }
            return
        }

        _state.update { it.copy(isProcessing = true, paymentStatus = "Sending M-PESA request…", paymentError = null) }

        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (settings == null) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    paymentError = "Set up this till in Device setup before taking M-PESA payments."
                ) }
                return@launch
            }

            val saleId = UuidV7.new()
            val sale = try {
                transactionRepository.completeSale(
                    saleId = saleId,
                    cartItems = items,
                    paymentMethod = PaymentMethod.MPESA,
                    amountReceived = 0.0,
                    cashierName = settings.cashierName,
                    paymentStatus = PaymentStatus.PENDING,
                    deviceTag = settings.deviceId,
                ).also { cartRepository.clearCart() }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, paymentError = e.message ?: "Could not record the sale") }
                return@launch
            }

            try {
                syncService.pushPending()
                if (!transactionRepository.isSaleSynced(saleId)) {
                    _state.update { it.copy(
                        isProcessing = false,
                        paymentStatus = null,
                        paymentError = "M-PESA needs a connection: the sale was saved as PENDING but not sent. " +
                            "It will sync when connectivity returns."
                    ) }
                    return@launch
                }

                val attempt = backendApi.stkPush(saleId, phoneNumber.trim())
                _state.update { it.copy(paymentStatus = "Check the customer's phone for the M-PESA PIN prompt.") }

                var status = attempt.status
                var receipt: String? = null
                var failureReason: String? = null
                var waitedMs = 0L
                while (status == "PENDING" && waitedMs < POLL_TIMEOUT_MS) {
                    val interval = attempt.pollAfterMs ?: DEFAULT_POLL_MS
                    delay(interval)
                    waitedMs += interval
                    val polled = backendApi.paymentStatus(attempt.paymentTransactionId)
                    status = polled.status
                    receipt = polled.mpesaReceiptNumber
                    failureReason = polled.resultDesc
                }

                when (status) {
                    "SUCCESS" -> {
                        transactionRepository.applyPaymentOutcome(saleId, PaymentStatus.PAID, receipt)
                        _state.update { it.copy(
                            isProcessing = false,
                            paymentStatus = "Payment successful",
                            isPaymentComplete = true,
                            completedSale = sale.copy(
                                paymentStatus = PaymentStatus.PAID,
                                mpesaRef = receipt,
                                synced = true,
                                amountReceived = sale.totalAmount,
                                changeAmount = 0.0,
                            ),
                            showReceipt = true,
                        ) }
                    }
                    "FAILED" -> {
                        transactionRepository.applyPaymentOutcome(saleId, PaymentStatus.FAILED, null)
                        _state.update { it.copy(
                            isProcessing = false,
                            paymentStatus = null,
                            paymentError = "M-PESA payment failed: ${failureReason ?: "customer did not authorise"}",
                        ) }
                    }
                    else -> _state.update { it.copy(
                        isProcessing = false,
                        paymentStatus = "Still waiting for M-PESA confirmation. Check History later.",
                    ) }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    paymentError = "${e.message}${if (e.code.isBlank()) "" else " (${e.code})"}. " +
                        "The sale is saved as PENDING.",
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    paymentError = "Network error: ${e.message ?: "could not reach the backend"}. " +
                        "The sale is saved as PENDING.",
                ) }
            }
        }
    }

    fun completeCashSale(totalAmount: Double) {
        val amountReceived = _state.value.amountReceivedText.toDoubleOrNull()
        if (amountReceived == null || amountReceived <= 0.0) {
            _state.update { it.copy(paymentError = "Enter how much cash the customer gave you") }
            return
        }
        if (amountReceived < totalAmount) {
            _state.update { it.copy(paymentError = "Cash received is less than the total due") }
            return
        }
        val items = cartRepository.cartItems.value

        _state.update { it.copy(isProcessing = true, paymentError = null) }

        viewModelScope.launch {
            val settings = settingsRepository.current()
            val sale = transactionRepository.completeSale(
                saleId = UuidV7.new(),
                cartItems = items,
                paymentMethod = PaymentMethod.CASH,
                amountReceived = amountReceived,
                cashierName = settings?.cashierName,
                deviceTag = settings?.deviceId,
            )

            cartRepository.clearCart()
            _state.update { it.copy(
                isProcessing = false,
                isPaymentComplete = true,
                completedSale = sale,
                showReceipt = true
            ) }

            // Cash sales stay valid offline; push in the background and let a
            // later sync retry if the network is down.
            runCatching { syncService.pushPending() }
        }
    }

    /**
     * Split tender: take some of the total in cash and charge the remainder to
     * M-PESA. The cash is recorded immediately, so the sale is persisted
     * PENDING and its M-PESA leg can be retried without taking the cash again.
     */
    fun sendSplitSale(totalAmount: Double) {
        val cash = _state.value.splitCashText.toDoubleOrNull() ?: 0.0
        val phone = _state.value.phoneNumberText
        val cashCents = (cash * 100).roundToLong()
        val totalCents = (totalAmount * 100).roundToLong()
        splitShapeError(cashCents, totalCents)?.let { message ->
            _state.update { it.copy(paymentError = message) }
            return
        }
        if (!isPlausibleKenyanMsisdn(phone)) {
            _state.update { it.copy(paymentError = "Enter a valid Safaricom number, e.g. 0712345678") }
            return
        }
        val items = cartRepository.cartItems.value

        _state.update { it.copy(isProcessing = true, paymentStatus = "Recording cash and starting M-PESA…", paymentError = null) }

        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (settings == null) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    paymentError = "Set up this till in Device setup before taking payments."
                ) }
                return@launch
            }

            val saleId = UuidV7.new()
            val cashAmount = cashCents / 100.0
            val sale = try {
                transactionRepository.completeSale(
                    saleId = saleId,
                    cartItems = items,
                    paymentMethod = PaymentMethod.SPLIT,
                    amountReceived = cashAmount,
                    cashApplied = cashAmount,
                    cashierName = settings.cashierName,
                    paymentStatus = PaymentStatus.PENDING,
                    deviceTag = settings.deviceId,
                ).also { cartRepository.clearCart() }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, paymentError = e.message ?: "Could not record the sale") }
                return@launch
            }

            runCatching { syncService.pushPending() }
            if (!transactionRepository.isSaleSynced(saleId)) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    pendingSplitSale = sale,
                    pendingSplitPhone = phone,
                    paymentError = "Cash ${CurrencyFormatter.format(cashAmount)} recorded. M-PESA needs a connection — retry when you're back online.",
                ) }
                return@launch
            }
            runSplitStk(sale, phone)
        }
    }

    /** Restarts just the M-PESA leg of a PENDING split sale; the cash is untouched. */
    fun retrySplitStk() {
        val sale = _state.value.pendingSplitSale ?: return
        val phone = _state.value.pendingSplitPhone ?: _state.value.phoneNumberText
        if (!isPlausibleKenyanMsisdn(phone)) {
            _state.update { it.copy(paymentError = "Enter a valid Safaricom number, e.g. 0712345678") }
            return
        }
        _state.update { it.copy(isProcessing = true, paymentStatus = "Retrying M-PESA…", paymentError = null) }
        viewModelScope.launch {
            runCatching { syncService.pushPending() }
            if (!transactionRepository.isSaleSynced(sale.id)) {
                _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = null,
                    paymentError = "Still offline: the cash is recorded and the M-PESA leg will start when you're back online.",
                ) }
                return@launch
            }
            runSplitStk(sale, phone)
        }
    }

    private suspend fun runSplitStk(sale: Sale, phone: String) {
        try {
            // A fresh key per attempt: the same key would replay the previous
            // (possibly failed) STK prompt instead of reaching Daraja again.
            val attempt = backendApi.stkPush(
                saleId = sale.id,
                phoneNumber = phone.trim(),
                idempotencyKey = "stk-${sale.id}-${UuidV7.new()}",
            )
            _state.update { it.copy(paymentStatus = "Check the customer's phone for the M-PESA PIN prompt.") }

            var status = attempt.status
            var receipt: String? = null
            var failureReason: String? = null
            var waitedMs = 0L
            while (status == "PENDING" && waitedMs < POLL_TIMEOUT_MS) {
                val interval = attempt.pollAfterMs ?: DEFAULT_POLL_MS
                delay(interval)
                waitedMs += interval
                val polled = backendApi.paymentStatus(attempt.paymentTransactionId)
                status = polled.status
                receipt = polled.mpesaReceiptNumber
                failureReason = polled.resultDesc
            }

            when (status) {
                "SUCCESS" -> {
                    transactionRepository.applyPaymentOutcome(sale.id, PaymentStatus.PAID, receipt)
                    _state.update { it.copy(
                        isProcessing = false,
                        paymentStatus = "Payment successful",
                        isPaymentComplete = true,
                        completedSale = sale.copy(paymentStatus = PaymentStatus.PAID, mpesaRef = receipt, synced = true),
                        showReceipt = true,
                        pendingSplitSale = null,
                        pendingSplitPhone = null,
                    ) }
                }
                "FAILED" -> {
                    // Do not mark the sale FAILED: keep it PENDING so the M-PESA
                    // leg can be retried and the cash already taken stays on record.
                    _state.update { it.copy(
                        isProcessing = false,
                        paymentStatus = null,
                        pendingSplitSale = sale,
                        pendingSplitPhone = phone,
                        paymentError = "M-PESA leg failed: ${failureReason ?: "customer did not authorise"}. " +
                            "Cash ${CurrencyFormatter.format(sale.cashAmount)} is recorded — retry the M-PESA leg or settle it in History.",
                    ) }
                }
                else -> _state.update { it.copy(
                    isProcessing = false,
                    paymentStatus = "M-PESA not confirmed yet. The sale stays pending — retry or check History later.",
                    pendingSplitSale = sale,
                    pendingSplitPhone = phone,
                ) }
            }
        } catch (e: ApiException) {
            val detail = if (e.code == "STK_IN_PROGRESS") {
                "An M-PESA prompt is already in flight for this sale — wait for the customer to respond."
            } else {
                "${e.message}${if (e.code.isBlank()) "" else " (${e.code})"}."
            }
            _state.update { it.copy(
                isProcessing = false,
                paymentStatus = null,
                pendingSplitSale = sale,
                pendingSplitPhone = phone,
                paymentError = "$detail Cash ${CurrencyFormatter.format(sale.cashAmount)} is recorded; the sale stays pending.",
            ) }
        } catch (e: Exception) {
            _state.update { it.copy(
                isProcessing = false,
                paymentStatus = null,
                pendingSplitSale = sale,
                pendingSplitPhone = phone,
                paymentError = "Network error: ${e.message ?: "could not reach the backend"}. " +
                    "Cash ${CurrencyFormatter.format(sale.cashAmount)} is recorded; retry the M-PESA leg when back online.",
            ) }
        }
    }

    private fun splitShapeError(cashCents: Long, totalCents: Long): String? {
        if (cashCents <= 0L) return "Enter the cash the customer is paying (more than zero)"
        if (cashCents >= totalCents) return "Cash covers the whole sale — use the Cash method instead"
        val remainder = totalCents - cashCents
        if (remainder < 100L) return "The M-PESA remainder must be at least KES 1"
        if (remainder % 100L != 0L) return "The M-PESA remainder must be a whole number of shillings — adjust the cash"
        return null
    }

    fun hideReceipt() {
        _state.update { it.copy(showReceipt = false) }
    }

    private fun isPlausibleKenyanMsisdn(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        return digits.length == 9 ||
            (digits.length == 10 && digits.startsWith("0")) ||
            (digits.length == 12 && digits.startsWith("254"))
    }

    private companion object {
        const val DEFAULT_POLL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 120_000L
    }
}
