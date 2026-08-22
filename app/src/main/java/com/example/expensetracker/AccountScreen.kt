package com.example.expensetracker

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.expensetracker.services.GoogleDriveService
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

@Keep
@Composable
fun AccountScreen(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as Application
    val accountViewModel: AccountViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val transactionViewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    
    val accounts by accountViewModel.accounts.collectAsState()
    var newAccountName by remember { mutableStateOf("") }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val googleDriveService = remember { GoogleDriveService(context) }
    var googleSignInAccount by remember { mutableStateOf(googleDriveService.getLastSignedInAccount()) }
    var isSyncing by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val data = result.data
            googleDriveService.handleSignInResult(
                data = data,
                onSignedIn = { account ->
                    googleSignInAccount = account
                    coroutineScope.launch {
                        isSyncing = true
                        try {
                            autoRestore(googleDriveService, accountViewModel, transactionViewModel, categoryViewModel)
                            Toast.makeText(context, "Cloud Restore Successful", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("AccountScreen", "Cloud Restore Failed", e)
                            Toast.makeText(context, "Cloud Restore Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                onError = { e ->
                    val statusCode = (e as? ApiException)?.statusCode
                    val errorMessage = "Sign in failed. Code: $statusCode. Message: ${e.localizedMessage}"
                    Log.e("AccountScreen", errorMessage)
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    )
    
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasSmsPermission = isGranted
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Expense Books",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Cloud Status Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (googleSignInAccount != null) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (googleSignInAccount != null) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (googleSignInAccount != null) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = if (googleSignInAccount != null) "Cloud Sync Active" else "Cloud Backup Off",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (googleSignInAccount != null) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (googleSignInAccount == null) {
                Button(
                    onClick = { googleSignInLauncher.launch(googleDriveService.getSignInIntent()) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Sign In with Google")
                }
            } else {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "Signed in as ${googleSignInAccount?.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            coroutineScope.launch {
                                isSyncing = true
                                try {
                                    val result = autoBackup(googleDriveService, accountViewModel, transactionViewModel, categoryViewModel)
                                    if (result.first) {
                                        Toast.makeText(context, "Sync Success (v4)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sync Error (v4): ${result.second}", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("AccountScreen", "Sync Failed", e)
                                    val errorMsg = e.message ?: e.localizedMessage ?: e.javaClass.simpleName
                                    Toast.makeText(context, "Sync Crash: $errorMsg", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        }) {
                            Text("Sync")
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Button(onClick = {
                            googleDriveService.signOut()
                            googleSignInAccount = null
                        }) {
                            Text("Sign Out")
                        }
                        if (isSyncing) {
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }

        if (hasSmsPermission) {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            }) {
                Text("Disable SMS Reading")
            }
        } else {
            Button(onClick = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }) {
                Text("Enable SMS Reading")
            }
        }

        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Enable Sync Notifications")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(accounts) { account ->
                val isSmsAccount = account.name == "SMS Transactions"
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isSmsAccount) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "SMS Account",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).size(20.dp)
                        )
                    }
                    Text(
                        text = account.name,
                        modifier = Modifier
                            .clickable { 
                                navController.navigate("transaction_screen/${account.id}")
                            }
                            .weight(1f)
                            .padding(8.dp),
                        style = if (isSmsAccount) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { navController.navigate("category_screen/${account.id}") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Categories")
                    }
                    if (!isSmsAccount) {
                        IconButton(onClick = { accountToDelete = account }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Account")
                        }
                    }
                }
            }
        }
        
        if (accountToDelete != null) {
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = { Text("Delete Account") },
                text = { Text("Are you sure you want to delete this account and all its transactions?") },
                confirmButton = {
                    Button(
                        onClick = {
                            accountToDelete?.let { accountViewModel.deleteAccount(it) }
                            accountToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { accountToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            TextField(
                value = newAccountName,
                onValueChange = { newAccountName = it },
                label = { Text("New Account Name") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { 
                    accountViewModel.addAccount(newAccountName)
                    newAccountName = ""
                },
                enabled = newAccountName.isNotBlank()
            ) {
                Text("Add")
            }
        }
    }
}

suspend fun autoBackup(
    googleDriveService: GoogleDriveService,
    accountViewModel: AccountViewModel,
    transactionViewModel: TransactionViewModel,
    categoryViewModel: CategoryViewModel
): Pair<Boolean, String> {
    val accounts = accountViewModel.getAllAccounts()
    if (accounts.isEmpty()) return Pair(true, "No accounts to sync")
    
    val failedBooks = mutableListOf<String>()
    
    for (account in accounts) {
        try {
            val transactions = transactionViewModel.getAllTransactions(account.id)
            val categories = categoryViewModel.getCategories(account.id).first()
            val csvContent = generateCsvContent(transactions, categories)
            
            Log.d("AccountScreen", "Syncing ${account.name} with ${transactions.size} rows")
            googleDriveService.uploadCsvFile("ExpenseTracker ${account.name}.csv", csvContent)
        } catch (e: Exception) {
            Log.e("AccountScreen", "Sync failed for ${account.name}", e)
            val errorMsg = e.message ?: e.localizedMessage ?: e.javaClass.simpleName
            failedBooks.add("${account.name} ($errorMsg)")
        }
    }
    
    return if (failedBooks.isEmpty()) {
        Pair(true, "")
    } else {
        Pair(false, failedBooks.joinToString(", "))
    }
}

suspend fun autoRestore(
    googleDriveService: GoogleDriveService,
    accountViewModel: AccountViewModel,
    transactionViewModel: TransactionViewModel,
    categoryViewModel: CategoryViewModel
) {
    // 1. Get all CSV files from Drive and deduplicate by name (keeping only the latest version)
    val driveFiles = googleDriveService.listCsvFiles()
        .filter { it.first.startsWith("ExpenseTracker ") && !it.first.contains("_snapshot.csv") }
        .associateBy({ it.first }, { it.second }) // associates name to ID, duplicates naturally overwrite to the last one seen

    for ((fileName, fileId) in driveFiles) {
        val accountName = fileName
            .removePrefix("ExpenseTracker ")
            .removeSuffix(".csv")
        
        // 2. Fetch fresh local accounts inside the loop to prevent race conditions/duplicates
        val localAccounts = accountViewModel.getAllAccounts()
        val existingAccount = localAccounts.find { it.name == accountName }
        
        val accountId = existingAccount?.id ?: accountViewModel.addAccountAndGetId(accountName)
        
        val content = googleDriveService.downloadFile(fileId)
        content?.let { csvString ->
            // Clear existing transactions before importing for full override effect
            transactionViewModel.clearTransactionsForAccount(accountId)
            parseAndImportCsv(ByteArrayInputStream(csvString.toByteArray()), accountId, categoryViewModel, transactionViewModel)
        }
    }
}
