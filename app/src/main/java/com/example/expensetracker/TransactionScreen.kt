package com.example.expensetracker

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionScreen(
    navController: NavController,
    accountId: Int
) {
    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val transactionViewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val accountViewModel: AccountViewModel = viewModel(
        factory = ViewModelFactory(application)
    )

    val transactions by transactionViewModel.getTransactions(accountId).collectAsState(initial = emptyList())
    val categories by categoryViewModel.getCategories(accountId).collectAsState(initial = emptyList())
    val accounts by accountViewModel.accounts.collectAsState()
    val balance by transactionViewModel.getAccountBalance(accountId).collectAsState(initial = 0.0)

    val selectedCategory by transactionViewModel.selectedCategory.collectAsState()
    val startDate by transactionViewModel.startDate.collectAsState()
    val endDate by transactionViewModel.endDate.collectAsState()
    val remarkFilter by transactionViewModel.remarkFilter.collectAsState()
    val amountFilter by transactionViewModel.amountFilter.collectAsState()

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var movingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val csvContent = generateCsvContent(transactions, categories)
                        outputStream.write(csvContent.toByteArray())
                    }
                } catch (e: IOException) {
                    // Handle error
                }
            }
        }
    )

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            parseAndImportCsv(inputStream, accountId, categoryViewModel, transactionViewModel)
                        }
                    } catch (e: IOException) {
                        // Handle error
                    }
                }
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            Row {
                FloatingActionButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Filter Transactions")
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(it).padding(16.dp)) {
            Row {
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { 
                    openFileLauncher.launch(arrayOf("*/*"))
                }) {
                    Text("Import")
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Button(onClick = { 
                    createFileLauncher.launch("transactions.csv")
                }) {
                    Text("Export")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Balance: %.2f".format(balance),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Active Filters
            Row {
                selectedCategory?.let { Text("Category: ${it.name}") }
                startDate?.let { Text("From: ${SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(it)}") }
                endDate?.let { Text("To: ${SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(it)}") }
                remarkFilter?.let { Text("Remark: $it") }
                amountFilter?.let { Text("Amount: $it") }
            }

            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Details", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("Amount", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(0.8f)) // For buttons
            }

            // Transactions list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(transactions) { transaction ->
                    TransactionRow(
                        transaction,
                        categories,
                        onEditClick = { editingTransaction = transaction },
                        onDeleteClick = { transactionViewModel.deleteTransaction(transaction) },
                        onMoveClick = { movingTransaction = transaction }
                    )
                    Divider()
                }
            }

            // Edit Dialog
            if (editingTransaction != null) {
                EditTransactionDialog(
                    transaction = editingTransaction!!,
                    categories = categories,
                    onDismiss = { editingTransaction = null },
                    onSave = { updatedTransaction ->
                        transactionViewModel.updateTransaction(updatedTransaction)
                        editingTransaction = null // Close dialog
                    }
                )
            }
            
            // Add Dialog
            if (showAddDialog) {
                AddTransactionDialog(
                    categories = categories, 
                    onDismiss = { showAddDialog = false }, 
                    onAddTransaction = { newTransaction -> 
                        transactionViewModel.addTransaction(newTransaction.copy(accountId = accountId))
                        showAddDialog = false
                    }
                )
            }
            
            // Filter Dialog
            if (showFilterDialog) {
                FilterDialog(
                    categories = categories,
                    onDismiss = { showFilterDialog = false },
                    onApply = {
                        transactionViewModel.setCategoryFilter(it.first)
                        transactionViewModel.setDateRangeFilter(it.second, it.third)
                        transactionViewModel.setRemarkFilter(it.fourth)
                        transactionViewModel.setAmountFilter(it.fifth)
                        showFilterDialog = false
                    },
                    onClear = {
                        transactionViewModel.clearFilters()
                        showFilterDialog = false
                    }
                )
            }

            // Move Dialog
            if (movingTransaction != null) {
                MoveTransactionDialog(
                    accounts = accounts.filter { it.id != accountId },
                    onDismiss = { movingTransaction = null },
                    onMove = { newAccountId ->
                        transactionViewModel.moveTransaction(movingTransaction!!, newAccountId)
                        movingTransaction = null
                    }
                )
            }
        }
    }
}

fun generateCsvContent(transactions: List<Transaction>, categories: List<Category>): String {
    val header = "Date,Time,Remark,Category,Mode,Cash In,Cash Out"
    val rows = transactions.map { transaction ->
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(transaction.date)
        val time = transaction.time
        val remark = transaction.remark.let { if (it.contains(",")) "\"$it\"" else it }
        val categoryName = categories.find { it.id == transaction.categoryId }?.name?.let { if (it.contains(",")) "\"$it\"" else it } ?: "N/A"
        val mode = transaction.mode.let { if (it.contains(",")) "\"$it\"" else it }
        val cashIn = transaction.cashIn.toString()
        val cashOut = transaction.cashOut.toString()
        "$date,$time,$remark,$categoryName,$mode,$cashIn,$cashOut"
    }
    return (listOf(header) + rows).joinToString("\n")
}

suspend fun parseAndImportCsv(inputStream: java.io.InputStream, accountId: Int, categoryViewModel: CategoryViewModel, transactionViewModel: TransactionViewModel) {
    BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
        val lineIterator = lines.iterator()
        if (!lineIterator.hasNext()) return@useLines

        val headerMap = lineIterator.next().split(",").mapIndexed { index, s -> s.trim().removeSurrounding("\"") to index }.toMap()

        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            val tokens = line.split(",").toMutableList()

            for(i in tokens.indices){
                tokens[i] = tokens[i].removeSurrounding("\"")
            }

            try {
                val date = headerMap["Date"]?.let { index -> tokens.getOrNull(index)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) } } ?: Date()
                val time = headerMap["Time"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val remark = headerMap["Remark"]?.let { index -> tokens.getOrNull(index)?.removeSurrounding("\"") } ?: ""
                val categoryName = headerMap["Category"]?.let { index -> tokens.getOrNull(index) } ?: ""
                val categoryId = if (categoryName.isNotEmpty()) categoryViewModel.getOrCreateCategory(accountId, categoryName).toInt() else null
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
                transactionViewModel.addTransaction(newTransaction)
            } catch (e: Exception) {
                // Handle parsing error for a line
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    categories: List<Category>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val formattedDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(transaction.date)
            Text(transaction.remark, fontWeight = FontWeight.SemiBold)
            val categoryName = categories.find { it.id == transaction.categoryId }?.name ?: "N/A"
            Text("$formattedDate | $categoryName | ${transaction.mode}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        val (amount, color) = when {
            transaction.cashIn > 0 -> "+%.2f".format(transaction.cashIn) to Color(0xFF006400) // Darker Green
            transaction.cashOut > 0 -> "-%.2f".format(transaction.cashOut) to Color.Red
            else -> "0.00" to Color.Gray
        }
        Text(text = amount, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

        Row {
            IconButton(onClick = onMoveClick) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Move Transaction")
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Transaction")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Transaction")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(categories: List<Category>, onDismiss: () -> Unit, onAddTransaction: (Transaction) -> Unit) {
    var remark by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var mode by remember { mutableStateOf("") }
    var cashIn by remember { mutableStateOf("") }
    var cashOut by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = remark, onValueChange = { remark = it }, label = { Text("Remark") })
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                TextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = cashIn, onValueChange = { cashIn = it }, label = { Text("Cash In") }, modifier = Modifier.weight(1f))
                    TextField(value = cashOut, onValueChange = { cashOut = it }, label = { Text("Cash Out") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newTransaction = Transaction(
                    accountId = 0, // This will be set in the screen-level composable
                    categoryId = selectedCategory?.id,
                    date = Date(),
                    time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    remark = remark,
                    mode = mode,
                    cashIn = cashIn.toDoubleOrNull() ?: 0.0,
                    cashOut = cashOut.toDoubleOrNull() ?: 0.0
                )
                onAddTransaction(newTransaction)
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var remark by remember { mutableStateOf(transaction.remark) }
    var selectedCategory by remember { mutableStateOf(categories.find { it.id == transaction.categoryId }) }
    var mode by remember { mutableStateOf(transaction.mode) }
    var cashIn by remember { mutableStateOf(if (transaction.cashIn > 0) transaction.cashIn.toString() else "") }
    var cashOut by remember { mutableStateOf(if (transaction.cashOut > 0) transaction.cashOut.toString() else "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = remark, onValueChange = { remark = it }, label = { Text("Remark") })
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                TextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = cashIn, onValueChange = { cashIn = it }, label = { Text("Cash In") }, modifier = Modifier.weight(1f))
                    TextField(value = cashOut, onValueChange = { cashOut = it }, label = { Text("Cash Out") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedTransaction = transaction.copy(
                    remark = remark,
                    categoryId = selectedCategory?.id,
                    mode = mode,
                    cashIn = cashIn.toDoubleOrNull() ?: 0.0,
                    cashOut = cashOut.toDoubleOrNull() ?: 0.0
                )
                onSave(updatedTransaction)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onApply: (Quintuple<Category?, Date?, Date?, String?, Double?>) -> Unit,
    onClear: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var startDate by remember { mutableStateOf<Date?>(null) }
    var endDate by remember { mutableStateOf<Date?>(null) }
    var remark by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var editingStartDate by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis?.let { Date(it) }
                    if (editingStartDate) {
                        startDate = selectedDate
                    } else {
                        endDate = selectedDate
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = remark, onValueChange = { remark = it }, label = { Text("Remark contains") })
                TextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount equals") })
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedCategory?.name ?: "All Categories",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories") },
                            onClick = {
                                selectedCategory = null
                                expanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { 
                        editingStartDate = true
                        showDatePicker = true 
                    }) {
                        Text(startDate?.let { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(it) } ?: "Start Date")
                    }
                    Button(onClick = { 
                        editingStartDate = false
                        showDatePicker = true 
                    }) {
                        Text(endDate?.let { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(it) } ?: "End Date")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(Quintuple(selectedCategory, startDate, endDate, remark, amount.toDoubleOrNull())) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                Button(onClick = onClear) {
                    Text("Clear")
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun MoveTransactionDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Transaction") },
        text = {
            Column {
                Text("Select an account to move this transaction to:")
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(accounts) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAccountId = account.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAccountId == account.id,
                                onClick = { selectedAccountId = account.id }
                            )
                            Text(account.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    selectedAccountId?.let(onMove)
                },
                enabled = selectedAccountId != null
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)