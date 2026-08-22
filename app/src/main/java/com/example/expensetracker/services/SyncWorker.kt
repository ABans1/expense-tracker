package com.example.expensetracker.services

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensetracker.AppDatabase
import com.example.expensetracker.R
import com.example.expensetracker.generateCsvContent

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Auto-sync starting...")
        showNotification("Cloud Backup", "Automatic backup started...")

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
            val failedAccounts = mutableListOf<String>()
            
            for (account in accounts) {
                try {
                    val transactions = transactionDao.getTransactionsForAccountList(account.id)
                    val categories = categoryDao.getCategoriesList(account.id)
                    val csvContent = generateCsvContent(transactions, categories)
                    googleDriveService.uploadCsvFile("ExpenseTracker ${account.name}.csv", csvContent)
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync ${account.name}", e)
                    val errorMsg = e.message ?: e.localizedMessage ?: e.javaClass.simpleName
                    failedAccounts.add("${account.name} ($errorMsg)")
                }
            }
            
            if (failedAccounts.isEmpty()) {
                Log.d("SyncWorker", "Auto-sync completed successfully")
                showNotification("Cloud Backup", "Backup completed successfully")
                Result.success()
            } else {
                val errorSummary = failedAccounts.joinToString(", ")
                Log.e("SyncWorker", "Auto-sync partially failed: $errorSummary")
                showNotification("Cloud Backup", "Backup failed for: $errorSummary")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Auto-sync fatal error", e)
            val fatalMsg = e.message ?: e.localizedMessage ?: e.javaClass.simpleName
            showNotification("Cloud Backup", "Backup fatal error: $fatalMsg")
            Result.retry()
        }
    }

    private fun showNotification(title: String, content: String) {
        try {
            val builder = NotificationCompat.Builder(applicationContext, "sync_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(1001, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SyncWorker", "Notification permission not granted", e)
        }
    }
}
