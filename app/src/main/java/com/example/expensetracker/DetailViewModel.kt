package com.example.expensetracker

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class DetailViewModel : ViewModel() {
    private val _items = mutableStateListOf("Item 1", "Item 2", "Item 3")
    val items: List<String> = _items

    fun addItem() {
        _items.add("New Item")
    }

    fun removeItem(index: Int) {
        _items.removeAt(index)
    }

    fun updateItem(index: Int, text: String) {
        if (index >= 0 && index < _items.size) {
            _items[index] = text
        }
    }
}