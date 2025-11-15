package com.example.expensetracker

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Keep
@Composable
fun AccountScreen(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as Application
    val accountViewModel: AccountViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val accounts by accountViewModel.accounts.collectAsState()
    var newAccountName by remember { mutableStateOf("") }
    val context = LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasSmsPermission = isGranted
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
        Text("Enable SMS reading to automatically add transactions from your bank messages.")
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
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(accounts) { account ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = account.name,
                        modifier = Modifier
                            .clickable { 
                                navController.navigate("transaction_screen/${account.id}")
                            }
                            .weight(1f)
                            .padding(8.dp)
                    )
                    IconButton(onClick = { navController.navigate("category_screen/${account.id}") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Categories")
                    }
                    IconButton(onClick = { accountViewModel.deleteAccount(account) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Account")
                    }
                }
            }
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