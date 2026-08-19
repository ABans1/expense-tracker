package com.example.expensetracker

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun CategoryScreen(
    navController: NavController,
    accountId: Int
) {
    val application = LocalContext.current.applicationContext as Application
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = ViewModelFactory(application)
    )
    val categories by categoryViewModel.getCategories(accountId).collectAsState(initial = emptyList())
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) {
            Text("Back to Account")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Manage categories for this account. Categories help you organize and filter your transactions effectively.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(categories) { category ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = category.name, modifier = Modifier.weight(1f))
                    Button(onClick = { categoryToDelete = category }) {
                        Text("Delete")
                    }
                }
            }
        }

        if (categoryToDelete != null) {
            AlertDialog(
                onDismissRequest = { categoryToDelete = null },
                title = { Text("Delete Category") },
                text = { Text("Are you sure you want to delete this category?") },
                confirmButton = {
                    Button(
                        onClick = {
                            categoryToDelete?.let { categoryViewModel.deleteCategory(it.id) }
                            categoryToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { categoryToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            TextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("New Category Name") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { 
                categoryViewModel.addCategory(accountId, newCategoryName)
                newCategoryName = ""
            }) {
                Text("Add")
            }
        }
    }
}