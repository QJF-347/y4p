package org.whoismacy.mainconsumapp.ui.screens.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.Product
import org.whoismacy.mainconsumapp.data.repository.CartRepository
import org.whoismacy.mainconsumapp.data.repository.ProductRepository

data class PosState(
    val products: List<Product> = emptyList(),
    val cartItems: List<CartItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val taxRate: Double = 0.16
) {
    val subtotal: Double get() = cartItems.sumOf { it.totalPrice }
    val taxAmount: Double get() = subtotal * taxRate
    val totalAmount: Double get() = subtotal + taxAmount
}

class PosViewModel(
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        loadProducts()
        observeCart()
    }

    private fun loadProducts() {
        _state.update { it.copy(isLoading = true) }
        job?.cancel()
        job = viewModelScope.launch {
            productRepository.getAllProducts().collect { products ->
                _state.update { it.copy(products = products, isLoading = false) }
            }
        }
    }

    private fun observeCart() {
        viewModelScope.launch {
            cartRepository.cartItems.collect { cartItems ->
                _state.update { it.copy(cartItems = cartItems) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        job?.cancel()
        job = viewModelScope.launch {
            val flow = if (query.isBlank()) {
                productRepository.getAllProducts()
            } else {
                productRepository.searchProducts(query)
            }
            flow.collect { products ->
                _state.update { it.copy(products = products) }
            }
        }
    }

    fun addToCart(product: Product) {
        cartRepository.addToCart(product)
    }

    fun increaseQuantity(item: CartItem) {
        cartRepository.increaseQuantity(item)
    }

    fun decreaseQuantity(item: CartItem) {
        cartRepository.decreaseQuantity(item)
    }

    fun removeFromCart(item: CartItem) {
        cartRepository.removeFromCart(item)
    }

    fun clearCart() {
        cartRepository.clearCart()
    }
}