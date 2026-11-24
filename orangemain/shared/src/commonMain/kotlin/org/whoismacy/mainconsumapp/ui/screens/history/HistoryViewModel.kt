package org.whoismacy.mainconsumapp.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import org.whoismacy.mainconsumapp.data.model.PaymentMethod
import org.whoismacy.mainconsumapp.data.model.Sale
import org.whoismacy.mainconsumapp.data.model.SaleItem
import org.whoismacy.mainconsumapp.data.repository.TransactionRepository
import kotlin.time.Clock

enum class DateFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount")
}

data class DailySummary(
    val totalSales: Int = 0,
    val totalRevenue: Double = 0.0,
    val cashSales: Int = 0,
    val mpesaSales: Int = 0,
    val cashRevenue: Double = 0.0,
    val mpesaRevenue: Double = 0.0
)

data class HistoryState(
    val allSales: List<Sale> = emptyList(),
    val filteredSales: List<Sale> = emptyList(),
    val isLoading: Boolean = false,
    val selectedSale: Sale? = null,
    val selectedSaleItems: List<SaleItem> = emptyList(),
    val isDetailShowing: Boolean = false,
    val searchQuery: String = "",
    val dateFilter: DateFilter = DateFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val dailySummary: DailySummary = DailySummary()
)

class HistoryViewModel(
    private val transactionRepository: TransactionRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()
    private var job: Job? = null
    private var detailJob: Job? = null

    init {
        loadHistory()
    }

    private fun loadHistory() {
        _state.update { it.copy(isLoading = true) }
        job?.cancel()
        job = viewModelScope.launch {
            transactionRepository.getAllSales().collect { sales ->
                _state.update { currentState ->
                    val summary = calculateDailySummary(sales)
                    val filtered = applyFilters(
                        sales = sales,
                        searchQuery = currentState.searchQuery,
                        dateFilter = currentState.dateFilter,
                        sortOrder = currentState.sortOrder
                    )
                    currentState.copy(
                        allSales = sales,
                        filteredSales = filtered,
                        isLoading = false,
                        dailySummary = summary
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { currentState ->
            val filtered = applyFilters(
                sales = currentState.allSales,
                searchQuery = query,
                dateFilter = currentState.dateFilter,
                sortOrder = currentState.sortOrder
            )
            currentState.copy(searchQuery = query, filteredSales = filtered)
        }
    }

    fun onDateFilterChanged(filter: DateFilter) {
        _state.update { currentState ->
            val filtered = applyFilters(
                sales = currentState.allSales,
                searchQuery = currentState.searchQuery,
                dateFilter = filter,
                sortOrder = currentState.sortOrder
            )
            currentState.copy(dateFilter = filter, filteredSales = filtered)
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        _state.update { currentState ->
            val filtered = applyFilters(
                sales = currentState.allSales,
                searchQuery = currentState.searchQuery,
                dateFilter = currentState.dateFilter,
                sortOrder = order
            )
            currentState.copy(sortOrder = order, filteredSales = filtered)
        }
    }

    fun selectSale(sale: Sale) {
        _state.update { it.copy(selectedSale = sale, isDetailShowing = true, selectedSaleItems = emptyList()) }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            transactionRepository.getSaleItems(sale.id).collect { items ->
                _state.update { it.copy(selectedSaleItems = items) }
            }
        }
    }

    fun dismissDetail() {
        _state.update { it.copy(selectedSale = null, selectedSaleItems = emptyList(), isDetailShowing = false) }
    }

    private fun applyFilters(
        sales: List<Sale>,
        searchQuery: String,
        dateFilter: DateFilter,
        sortOrder: SortOrder
    ): List<Sale> {
        var result = sales

        // Search filter
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter { sale ->
                sale.receiptNumber.lowercase().contains(query) ||
                sale.paymentMethod.name.lowercase().contains(query) ||
                sale.mpesaRef?.lowercase()?.contains(query) == true
            }
        }

        // Date filter
        result = when (dateFilter) {
            DateFilter.ALL -> result
            DateFilter.TODAY -> {
                val todayPrefix = getTodayDatePrefix()
                result.filter { it.createdAt.startsWith(todayPrefix) }
            }
            DateFilter.THIS_WEEK -> {
                val weekDates = getWeekDatePrefixes()
                result.filter { sale -> weekDates.any { sale.createdAt.startsWith(it) } }
            }
            DateFilter.THIS_MONTH -> {
                val monthPrefix = getMonthPrefix()
                result.filter { it.createdAt.startsWith(monthPrefix) }
            }
        }

        // Sort
        result = when (sortOrder) {
            SortOrder.NEWEST_FIRST -> result.sortedByDescending { it.createdAt }
            SortOrder.OLDEST_FIRST -> result.sortedBy { it.createdAt }
            SortOrder.HIGHEST_AMOUNT -> result.sortedByDescending { it.totalAmount }
            SortOrder.LOWEST_AMOUNT -> result.sortedBy { it.totalAmount }
        }

        return result
    }

    private fun calculateDailySummary(sales: List<Sale>): DailySummary {
        val todayPrefix = getTodayDatePrefix()
        val todaySales = sales.filter { it.createdAt.startsWith(todayPrefix) }
        val cashSales = todaySales.filter { it.paymentMethod == PaymentMethod.CASH }
        val mpesaSales = todaySales.filter { it.paymentMethod == PaymentMethod.MPESA }

        return DailySummary(
            totalSales = todaySales.size,
            totalRevenue = todaySales.sumOf { it.totalAmount },
            cashSales = cashSales.size,
            mpesaSales = mpesaSales.size,
            cashRevenue = cashSales.sumOf { it.totalAmount },
            mpesaRevenue = mpesaSales.sumOf { it.totalAmount }
        )
    }

    /**
     * Returns "YYYY-MM-DD" for today using the createdAt format (LocalDateTime.toString() -> "YYYY-MM-DDTHH:MM:SS")
     */
    private fun getTodayDatePrefix(): String {
        val now = clock.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        return "${now.year}-${now.month.number.toString().padStart(2, '0')}-${now.day.toString().padStart(2, '0')}"
    }

    private fun getMonthPrefix(): String {
        val now = clock.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        return "${now.year}-${now.month.number.toString().padStart(2, '0')}"
    }

    private fun getWeekDatePrefixes(): List<String> {
        val now = clock.now()
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        return (0..6).map { daysAgo ->
            val date = now.minus(daysAgo.days).toLocalDateTime(tz)
            "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
        }
    }
}
