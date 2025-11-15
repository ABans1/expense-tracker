package com.example.expensetracker

interface SmsParser {
    fun parse(message: String): Transaction?
}