package com.example.expensetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CategoryViewModel(private val categoryDao: CategoryDao) : ViewModel() {

    fun getCategories(accountId: Int): Flow<List<Category>> {
        return categoryDao.getCategoriesForAccount(accountId)
    }

    fun addCategory(accountId: Int, name: String) {
        viewModelScope.launch {
            categoryDao.insert(Category(accountId = accountId, name = name))
        }
    }

    fun deleteCategory(categoryId: Int) {
        viewModelScope.launch {
            categoryDao.delete(categoryId)
        }
    }

    suspend fun getOrCreateCategory(accountId: Int, name: String): Long {
        val existingCategory = categoryDao.findByName(accountId, name)
        return if (existingCategory != null) {
            existingCategory.id.toLong()
        } else {
            categoryDao.insert(Category(accountId = accountId, name = name))
        }
    }
}