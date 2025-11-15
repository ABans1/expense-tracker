package com.example.expensetracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY date DESC, time DESC")
    fun getTransactionsForAccount(accountId: Int): Flow<List<Transaction>>

    @Insert
    suspend fun insert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Update
    suspend fun update(transaction: Transaction)
}