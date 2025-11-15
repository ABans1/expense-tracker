package com.example.expensetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class TransactionViewModel(private val transactionDao: TransactionDao) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _startDate = MutableStateFlow<Date?>(null)
    val startDate = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Date?>(null)
    val endDate = _endDate.asStateFlow()

    private val _remarkFilter = MutableStateFlow<String?>(null)
    val remarkFilter = _remarkFilter.asStateFlow()

    private val _amountFilter = MutableStateFlow<Double?>(null)
    val amountFilter = _amountFilter.asStateFlow()

    private val _modeFilter = MutableStateFlow<String?>(null)
    val modeFilter = _modeFilter.asStateFlow()

    fun getTransactions(accountId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsForAccount(accountId)
            .combine(_selectedCategory) { transactions, category ->
                if (category == null) transactions else transactions.filter { it.categoryId == category.id }
            }
            .combine(_startDate) { transactions, date ->
                if (date == null) transactions else transactions.filter { it.date.after(date) }
            }
            .combine(_endDate) { transactions, date ->
                if (date == null) transactions else transactions.filter { it.date.before(date) }
            }
            .combine(_remarkFilter) { transactions, remark ->
                if (remark.isNullOrEmpty()) transactions else transactions.filter { it.remark.contains(remark, ignoreCase = true) }
            }
            .combine(_amountFilter) { transactions, amount ->
                if (amount == null) transactions else transactions.filter { it.cashIn == amount || it.cashOut == amount }
            }
            .combine(_modeFilter) { transactions, mode ->
                if (mode.isNullOrEmpty()) transactions else transactions.filter { it.mode.equals(mode, ignoreCase = true) }
            }
    }

    fun getAccountBalance(accountId: Int): Flow<Double> {
        return getTransactions(accountId).map { transactions ->
            transactions.sumOf { it.cashIn } - transactions.sumOf { it.cashOut }
        }
    }

    fun setCategoryFilter(category: Category?) {
        _selectedCategory.value = category
    }

    fun setDateRangeFilter(start: Date?, end: Date?) {
        _startDate.value = start
        _endDate.value = end
    }

    fun setRemarkFilter(remark: String?) {
        _remarkFilter.value = remark
    }

    fun setAmountFilter(amount: Double?) {
        _amountFilter.value = amount
    }

    fun setModeFilter(mode: String?) {
        _modeFilter.value = mode
    }

    fun clearFilters() {
        _selectedCategory.value = null
        _startDate.value = null
        _endDate.value = null
        _remarkFilter.value = null
        _amountFilter.value = null
        _modeFilter.value = null
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.insert(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.delete(transaction)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.update(transaction)
        }
    }

    fun moveTransaction(transaction: Transaction, newAccountId: Int) {
        viewModelScope.launch {
            updateTransaction(transaction.copy(accountId = newAccountId))
        }
    }
}