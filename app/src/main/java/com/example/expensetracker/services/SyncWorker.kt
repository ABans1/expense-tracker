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
            // No notification here to avoid bothering the user if they never signed in
            return Result.success()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val accountDao = database.accountDao()
        val transactionDao = database.transactionDao()
        val categoryDao = database.categoryDao()

        return try {
            val accounts = accountDao.getAllAccountsList()
            var allSuccessful = true
            var failReason = ""
            
            for (account in accounts) {
                val transactions = transactionDao.getTransactionsForAccountList(account.id)
                val categories = categoryDao.getCategoriesList(account.id)
                val csvContent = generateCsvContent(transactions, categories)
                val fileId = googleDriveService.uploadCsvFile("ExpenseTracker ${account.name}.csv", csvContent)
                
                if (fileId == null) {
                    allSuccessful = false
                    failReason = "Upload failed for ${account.name}"
                    Log.e("SyncWorker", "Upload failed for ${account.name}")
                }
            }
            
            if (allSuccessful) {
                Log.d("SyncWorker", "Auto-sync completed successfully")
                showNotification("Cloud Backup", "Backup completed successfully")
                Result.success()
            } else {
                Log.e("SyncWorker", "Auto-sync failed: $failReason")
                showNotification("Cloud Backup", "Backup failed: $failReason. Will retry later.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Auto-sync failed with exception", e)
            showNotification("Cloud Backup", "Backup failed: ${e.localizedMessage}")
            Result.retry()
        }
    }

    private fun showNotification(title: String, content: String) {
        try {
            val builder = NotificationCompat.Builder(applicationContext, "sync_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Using foreground icon as fallback
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(applicationContext)) {
                // notificationId is a unique int for each notification that you must define
                notify(1001, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SyncWorker", "Notification permission not granted", e)
        }
    }
}
