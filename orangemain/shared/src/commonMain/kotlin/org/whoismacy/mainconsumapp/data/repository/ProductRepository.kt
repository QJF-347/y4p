package org.whoismacy.mainconsumapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.whoismacy.mainconsumapp.data.local.OrangeDatabase
import org.whoismacy.mainconsumapp.data.model.Product
import kotlin.time.Clock

class ProductRepository(
    private val database: OrangeDatabase,
    private val clock: Clock
) {

    private val queries get() = database.productQueries

    fun getAllProducts(): Flow<List<Product>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toProduct() } }
    }

    fun getProductsByCategory(category: String): Flow<List<Product>> {
        return queries.selectByCategory(category)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toProduct() } }
    }

    fun searchProducts(query: String): Flow<List<Product>> {
        return queries.searchByName("%$query%")
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toProduct() } }
    }

    fun getProductById(id: String): Flow<Product?> {
        return queries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toProduct() }
    }

    fun insertProduct(product: Product) {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        queries.insert(
            id = product.id,
            name = product.name,
            description = product.description,
            barcode = product.barcode,
            price = product.price,
            cost_price = product.costPrice,
            category = product.category,
            image_url = product.imageUrl,
            stock_quantity = product.stockQuantity.toLong(),
            is_active = if (product.isActive) 1L else 0L,
            created_at = product.createdAt.ifEmpty { now },
            updated_at = now,
            synced = if (product.synced) 1L else 0L,
        )
    }

    fun updateStock(productId: String, newQuantity: Int) {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        queries.updateStock(
            stock_quantity = newQuantity.toLong(),
            updated_at = now,
            id = productId,
        )
    }

    fun deleteProduct(productId: String) {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        queries.delete(updated_at = now, id = productId)
    }
}

private fun org.whoismacy.mainconsumapp.data.local.ProductEntity.toProduct(): Product {
    return Product(
        id = id,
        name = name,
        description = description,
        barcode = barcode,
        price = price,
        costPrice = cost_price,
        category = category,
        imageUrl = image_url,
        stockQuantity = stock_quantity.toInt(),
        isActive = is_active == 1L,
        createdAt = created_at,
        updatedAt = updated_at,
        synced = synced == 1L,
    )
}
