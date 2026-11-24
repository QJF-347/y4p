package org.whoismacy.mainconsumapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.whoismacy.mainconsumapp.data.local.OrangeDatabase
import org.whoismacy.mainconsumapp.data.model.Category

class CategoryRepository(private val database: OrangeDatabase) {

    private val queries get() = database.categoryQueries

    fun getAllCategories(): Flow<List<Category>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities ->
                entities.map { entity ->
                    Category(
                        id = entity.id,
                        name = entity.name,
                        color = entity.color,
                        icon = entity.icon,
                        sortOrder = entity.sort_order.toInt(),
                    )
                }
            }
    }

    fun insertCategory(category: Category) {
        queries.insert(
            id = category.id,
            name = category.name,
            color = category.color,
            icon = category.icon,
            sort_order = category.sortOrder.toLong(),
        )
    }

    fun deleteCategory(categoryId: String) {
        queries.delete(categoryId)
    }

    fun insertDefaultCategories() {
        val defaults = listOf(
            Category(id = "cat_general", name = "General", color = "#FF8B5000", icon = "🏷️", sortOrder = 0),
            Category(id = "cat_beverages", name = "Beverages", color = "#FF1976D2", icon = "🥤", sortOrder = 1),
            Category(id = "cat_bakery", name = "Bakery", color = "#FFF57C00", icon = "🍞", sortOrder = 2),
            Category(id = "cat_dairy", name = "Dairy", color = "#FF388E3C", icon = "🥛", sortOrder = 3),
            Category(id = "cat_household", name = "Household", color = "#FF7B1FA2", icon = "🧴", sortOrder = 4),
            Category(id = "cat_snacks", name = "Snacks", color = "#FFD32F2F", icon = "🍿", sortOrder = 5),
        )
        database.transaction {
            defaults.forEach { insertCategory(it) }
        }
    }
}
