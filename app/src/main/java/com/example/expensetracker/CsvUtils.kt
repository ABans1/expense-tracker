package com.example.expensetracker

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates CSV content from a list of transactions.
 * Matches the format used for both local exports and cloud backups.
 */
fun generateCsvContent(transactions: List<Transaction>, categories: List<Category>): String {
    val header = "Date,Time,Remark,Category,Mode,Cash In,Cash Out"
    val rows = transactions.map { transaction ->
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(transaction.date)
        val time = transaction.time
        val remark = escapeCsvField(transaction.remark)
        val categoryName = categories.find { it.id == transaction.categoryId }?.name ?: "N/A"
        val escapedCategory = escapeCsvField(categoryName)
        val mode = escapeCsvField(transaction.mode)
        val cashIn = transaction.cashIn.toString()
        val cashOut = transaction.cashOut.toString()
        "$date,$time,$remark,$escapedCategory,$mode,$cashIn,$cashOut"
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun escapeCsvField(field: String): String {
    return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }
}

/**
 * Parses CSV and imports into the database.
 * Matches the logic used for both local imports and cloud restores.
 */
suspend fun parseAndImportCsv(
    inputStream: InputStream,
    accountId: Int,
    categoryViewModel: CategoryViewModel,
    transactionViewModel: TransactionViewModel
) {
    val reader = BufferedReader(InputStreamReader(inputStream))
    val headerLine = reader.readLine() ?: return
    val headerTokens = parseCsvLine(headerLine)
    val headerMap = headerTokens.mapIndexed { index, s -> s.trim() to index }.toMap()

    reader.useLines { lines ->
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val tokens = parseCsvLine(line)
            try {
                val date = headerMap["Date"]?.let { index -> 
                    tokens.getOrNull(index)?.let { 
                        try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) 
                        } catch (e: Exception) { null }
                    } 
                } ?: Date()
                val time = headerMap["Time"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val remark = headerMap["Remark"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val categoryName = headerMap["Category"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val categoryId = if (categoryName.isNotEmpty() && categoryName != "N/A") {
                    categoryViewModel.getOrCreateCategory(accountId, categoryName).toInt()
                } else null
                
                val mode = headerMap["Mode"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val cashIn = headerMap["Cash In"]?.let { index -> tokens.getOrNull(index)?.toDoubleOrNull() } ?: 0.0
                val cashOut = headerMap["Cash Out"]?.let { index -> tokens.getOrNull(index)?.toDoubleOrNull() } ?: 0.0

                val newTransaction = Transaction(
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    time = time,
                    remark = remark,
                    mode = mode,
                    cashIn = cashIn,
                    cashOut = cashOut
                )
                transactionViewModel.addTransactionSync(newTransaction)
            } catch (e: Exception) {
                Log.e("CSVImport", "Error parsing line: $line", e)
            }
        }
    }
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (ch == '\"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                cur.append('\"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (ch == ',' && !inQuotes) {
            result.add(cur.toString().trim())
            cur = StringBuilder()
        } else {
            cur.append(ch)
        }
        i++
    }
    result.add(cur.toString().trim())
    return result
}
