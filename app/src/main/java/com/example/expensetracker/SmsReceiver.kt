package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val smsParser: SmsParser = SampleSmsParser()

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                val transaction = smsParser.parse(messageBody)
                if (transaction != null) {
                    val pendingResult = goAsync()
                    val appDatabase = (context.applicationContext as ExpenseTrackerApp).database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            appDatabase.transactionDao().insert(transaction)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}