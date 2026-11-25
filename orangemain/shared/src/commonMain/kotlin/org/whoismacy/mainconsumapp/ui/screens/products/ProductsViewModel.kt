package org.whoismacy.mainconsumapp.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.whoismacy.mainconsumapp.data.model.Category
import org.whoismacy.mainconsumapp.data.model.Product
import org.whoismacy.mainconsumapp.data.repository.CategoryRepository
import org.whoismacy.mainconsumapp.data.repository.ProductRepository

data class ProductsState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: String? = null, // null = "All"
)

/**
 * Read-only product catalog. The catalog is owned by admin; the till only
 * lists and searches it, so there is no add/edit/delete surface here.
 */
class ProductsViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductsState())
    val state: StateFlow<ProductsState> = _state.asStateFlow()
    private var productsJob: Job? = null

    init {
        loadCategories()
        loadProducts()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            // Insert defaults if empty
            val existing = categoryRepository.getAllCategories().first()
            if (existing.isEmpty()) {
                categoryRepository.insertDefaultCategories()
            }
            categoryRepository.getAllCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
    }

    private fun loadProducts() {
        _state.update { it.copy(isLoading = true) }
        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            val query = _state.value.searchQuery
            val category = _state.value.selectedCategory

            val flow = when {
                query.isNotBlank() -> productRepository.searchProducts(query)
                category != null -> productRepository.getProductsByCategory(category)
                else -> productRepository.getAllProducts()
            }

            flow.collect { products ->
                _state.update { it.copy(products = products, isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadProducts()
    }

    fun onCategorySelected(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
        loadProducts()
    }
}
