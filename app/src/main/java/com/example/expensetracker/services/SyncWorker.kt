package com.example.expensetracker.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.AppDatabase
import com.example.expensetracker.generateCsvContent

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val googleDriveService = GoogleDriveService(applicationContext)
        val googleSignInAccount = googleDriveService.getLastSignedInAccount()

        if (googleSignInAccount == null) {
            Log.d("SyncWorker", "User not signed in, skipping auto-sync")
            return Result.success()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val accountDao = database.accountDao()
        val transactionDao = database.transactionDao()
        val categoryDao = database.categoryDao()

        return try {
            val accounts = accountDao.getAllAccountsList()
            for (account in accounts) {
                val transactions = transactionDao.getTransactionsForAccountList(account.id)
                val categories = categoryDao.getCategoriesList(account.id)
                val csvContent = generateCsvContent(transactions, categories)
                googleDriveService.uploadCsvFile("ExpenseTracker ${account.name}.csv", csvContent)
            }
            Log.d("SyncWorker", "Auto-sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Auto-sync failed", e)
            Result.retry()
        }
    }
}
