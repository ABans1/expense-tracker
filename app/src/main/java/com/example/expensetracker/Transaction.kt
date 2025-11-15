package com.example.expensetracker

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Int,
    @ColumnInfo(name = "category_id")
    val categoryId: Int?,
    val date: Date,
    val time: String,
    val remark: String,
    val mode: String,
    @ColumnInfo(name = "cash_in")
    val cashIn: Double = 0.0,
    @ColumnInfo(name = "cash_out")
    val cashOut: Double = 0.0,
) {
    val balance: Double
        get() = cashIn - cashOut
}