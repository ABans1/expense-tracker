package com.example.expensetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val accountDao: AccountDao) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountDao.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addAccount(name: String) {
        viewModelScope.launch {
            accountDao.insert(Account(name = name))
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountDao.delete(account)
        }
    }
}