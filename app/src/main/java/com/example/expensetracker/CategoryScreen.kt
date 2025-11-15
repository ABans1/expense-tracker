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
import androidx.compose.material3.Button
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

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) {
            Text("Back to Account")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(categories) { category ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = category.name, modifier = Modifier.weight(1f))
                    Button(onClick = { categoryViewModel.deleteCategory(category.id) }) {
                        Text("Delete")
                    }
                }
            }
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