package com.example.expensetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE account_id = :accountId ORDER BY name ASC")
    fun getCategoriesForAccount(accountId: Int): Flow<List<Category>>

    @Insert
    suspend fun insert(category: Category): Long

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun delete(categoryId: Int)

    @Query("SELECT * FROM categories WHERE account_id = :accountId AND name = :name LIMIT 1")
    suspend fun findByName(accountId: Int, name: String): Category?
}