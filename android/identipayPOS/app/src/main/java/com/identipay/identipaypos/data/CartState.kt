package com.identipay.identipaypos.data

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CartItem(
    val product: Product,
    val quantity: Int,
)

class CartViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    val total: Double
        get() = _items.value.sumOf { it.product.price * it.quantity }

    val itemCount: Int
        get() = _items.value.sumOf { it.quantity }

    val maxAgeGate: Int
        get() = _items.value.maxOfOrNull { it.product.ageGate ?: 0 } ?: 0

    fun addItem(product: Product) {
        _items.update { current ->
            val existing = current.find { it.product.id == product.id }
            if (existing != null) {
                current.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                current + CartItem(product, 1)
            }
        }
    }

    fun removeItem(productId: String) {
        _items.update { current -> current.filter { it.product.id != productId } }
    }

    fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        _items.update { current ->
            current.map {
                if (it.product.id == productId) it.copy(quantity = quantity) else it
            }
        }
    }

    fun clearCart() {
        _items.value = emptyList()
    }
}
