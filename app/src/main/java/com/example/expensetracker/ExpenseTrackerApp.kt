package com.example.expensetracker

import android.app.Application

class ExpenseTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}