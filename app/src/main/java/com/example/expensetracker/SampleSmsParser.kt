package com.example.expensetracker

import java.util.Date

class SampleSmsParser : SmsParser {

    override fun parse(message: String): Transaction? {
        // This is a sample parser. You will need to adapt it to your bank's SMS format.
        val regex = "(?i)(?:Rs|INR|Amount)\\.?\\s*([\\d,]+\\.?\\d*)".toRegex()
        val match = regex.find(message)

        return if (match != null) {
            // The first capturing group contains the numeric amount string.
            val amountString = match.groupValues[1]
            val amount = amountString.replace(",", "").toDoubleOrNull() ?: 0.0

            // Determine the transaction type by checking for keywords in the *entire* message.
            val isCredit = message.contains("credited", ignoreCase = true) || message.contains("deposited", ignoreCase = true)
            val isDebit = message.contains("debited", ignoreCase = true) || message.contains("withdrawn", ignoreCase = true)

            // Ensure we have a valid transaction type. If both or neither are found, we can't proceed.
            if (isCredit == isDebit) {
                return null
            }

            Transaction(
                accountId = 1, // You'll need to figure out which account to assign this to
                categoryId = null, // You can try to infer this from the message or leave it null
                date = Date(),
                time = "",
                remark = "", // You can extract a more specific remark from the message
                mode = "SMS", // You can try to infer this from the message
                cashIn = if (isCredit) amount else 0.0,
                cashOut = if (isDebit) amount else 0.0
            )
        } else {
            null
        }
    }
}