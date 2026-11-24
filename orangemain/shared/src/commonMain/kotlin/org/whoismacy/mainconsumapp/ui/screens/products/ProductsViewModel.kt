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
import org.whoismacy.mainconsumapp.data.sync.SyncService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ProductsState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: String? = null, // null = "All"
    // Add/Edit product dialog
    val isAddEditDialogShowing: Boolean = false,
    val productToEdit: Product? = null,
    // Product form fields
    val formName: String = "",
    val formPrice: String = "",
    val formCostPrice: String = "",
    val formCategory: String = "General",
    val formStock: String = "0",
    val formDescription: String = "",
    val formBarcode: String = "",
    // Form validation
    val formNameError: String? = null,
    val formPriceError: String? = null,
    // Category management
    val isCategoryDialogShowing: Boolean = false,
    val newCategoryName: String = "",
    val newCategoryIcon: String = "🏷️",
    // Delete confirmation
    val showDeleteConfirmation: Boolean = false,
    val productToDelete: Product? = null,
    // Snackbar
    val snackbarMessage: String? = null,
) {
    val isEditing: Boolean get() = productToEdit != null
    val formTitle: String get() = if (isEditing) "Edit Product" else "Add Product"
}

class ProductsViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val syncService: SyncService,
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

    fun showEditProductDialog(product: Product) {
        _state.update {
            it.copy(
                isAddEditDialogShowing = true,
                productToEdit = product,
                formName = product.name,
                formPrice = product.price.toString(),
                formCostPrice = product.costPrice?.toString() ?: "",
                formCategory = product.category,
                formStock = product.stockQuantity.toString(),
                formDescription = product.description,
                formBarcode = product.barcode ?: "",
                formNameError = null,
                formPriceError = null,
            )
        }
    }

    fun dismissAddEditDialog() {
        _state.update { it.copy(isAddEditDialogShowing = false, productToEdit = null) }
    }

    fun onFormNameChanged(value: String) {
        _state.update { it.copy(formName = value, formNameError = null) }
    }

    fun onFormPriceChanged(value: String) {
        _state.update { it.copy(formPrice = value, formPriceError = null) }
    }

    fun onFormCostPriceChanged(value: String) {
        _state.update { it.copy(formCostPrice = value) }
    }

    fun onFormCategoryChanged(value: String) {
        _state.update { it.copy(formCategory = value) }
    }

    fun onFormStockChanged(value: String) {
        _state.update { it.copy(formStock = value) }
    }

    fun onFormDescriptionChanged(value: String) {
        _state.update { it.copy(formDescription = value) }
    }

    fun onFormBarcodeChanged(value: String) {
        _state.update { it.copy(formBarcode = value) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveProduct() {
        val s = _state.value

        // Validate
        var hasError = false
        if (s.formName.isBlank()) {
            _state.update { it.copy(formNameError = "Product name is required") }
            hasError = true
        }
        val price = s.formPrice.toDoubleOrNull()
        if (price == null || price <= 0) {
            _state.update { it.copy(formPriceError = "Enter a valid price") }
            hasError = true
        }
        if (hasError) return

        val costPrice = s.formCostPrice.toDoubleOrNull()
        val stock = s.formStock.toIntOrNull() ?: 0

        val product = if (s.productToEdit != null) {
            s.productToEdit.copy(
                name = s.formName.trim(),
                price = price!!,
                costPrice = costPrice,
                category = s.formCategory,
                stockQuantity = stock,
                description = s.formDescription.trim(),
                barcode = s.formBarcode.ifBlank { null },
                // An edited row always needs pushing; it usually came from a
                // pull and would otherwise still be marked as synced.
                synced = false,
            )
        } else {
            Product(
                id = Uuid.random().toString(),
                name = s.formName.trim(),
                price = price!!,
                costPrice = costPrice,
                category = s.formCategory,
                stockQuantity = stock,
                description = s.formDescription.trim(),
                barcode = s.formBarcode.ifBlank { null },
            )
        }

        viewModelScope.launch {
            productRepository.insertProduct(product)
            runCatching { syncService.pushPending() }
            val action = if (s.isEditing) "updated" else "added"
            _state.update {
                it.copy(
                    isAddEditDialogShowing = false,
                    productToEdit = null,
                    snackbarMessage = "\"${product.name}\" $action successfully",
                )
            }
        }
    }

    // --- Delete ---

    fun requestDelete(product: Product) {
        _state.update { it.copy(showDeleteConfirmation = true, productToDelete = product) }
    }

    fun confirmDelete() {
        val product = _state.value.productToDelete ?: return
        viewModelScope.launch {
            productRepository.deleteProduct(product.id)
            runCatching { syncService.pushPending() }
            _state.update {
                it.copy(
                    showDeleteConfirmation = false,
                    productToDelete = null,
                    snackbarMessage = "\"${product.name}\" deleted",
                )
            }
        }
    }

    fun cancelDelete() {
        _state.update { it.copy(showDeleteConfirmation = false, productToDelete = null) }
    }

    // --- Category Management ---

    fun showCategoryDialog() {
        _state.update { it.copy(isCategoryDialogShowing = true, newCategoryName = "", newCategoryIcon = "🏷️") }
    }

    fun dismissCategoryDialog() {
        _state.update { it.copy(isCategoryDialogShowing = false) }
    }

    fun onNewCategoryNameChanged(value: String) {
        _state.update { it.copy(newCategoryName = value) }
    }

    fun onNewCategoryIconChanged(value: String) {
        _state.update { it.copy(newCategoryIcon = value) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addCategory() {
        val name = _state.value.newCategoryName.trim()
        val icon = _state.value.newCategoryIcon.trim().ifBlank { "🏷️" }
        if (name.isBlank()) return

        val nextOrder = (_state.value.categories.maxOfOrNull { it.sortOrder } ?: -1) + 1

        viewModelScope.launch {
            categoryRepository.insertCategory(
                Category(
                    id = "cat_${Uuid.random().toString().take(8)}",
                    name = name,
                    icon = icon,
                    sortOrder = nextOrder,
                )
            )
            _state.update {
                it.copy(
                    newCategoryName = "",
                    newCategoryIcon = "🏷️",
                    snackbarMessage = "Category \"$name\" added",
                )
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(categoryId)
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }
}
