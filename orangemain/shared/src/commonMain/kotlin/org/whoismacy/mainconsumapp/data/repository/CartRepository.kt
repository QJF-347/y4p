package org.whoismacy.mainconsumapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.whoismacy.mainconsumapp.data.model.CartItem
import org.whoismacy.mainconsumapp.data.model.Product

class CartRepository {
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    fun addToCart(product: Product) {
        _cartItems.update { current ->
            val existing = current.find { it.product.id == product.id }
            if (existing != null) {
                current.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                current + CartItem(product = product, quantity = 1)
            }
        }
    }

    fun increaseQuantity(item: CartItem) {
        _cartItems.update { current ->
            current.map {
                if (it.product.id == item.product.id) it.copy(quantity = it.quantity + 1) else it
            }
        }
    }

    fun decreaseQuantity(item: CartItem) {
        _cartItems.update { current ->
            current.mapNotNull {
                if (it.product.id == item.product.id) {
                    if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else null
                } else {
                    it
                }
            }
        }
    }

    fun removeFromCart(item: CartItem) {
        _cartItems.update { current ->
            current.filterNot { it.product.id == item.product.id }
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }
}