package com.example.expensetracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY account_name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsList(): List<Account>

    @Query("SELECT * FROM accounts WHERE account_name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): Account?

    @Insert
    suspend fun insert(account: Account): Long

    @Delete
    suspend fun delete(account: Account)
}