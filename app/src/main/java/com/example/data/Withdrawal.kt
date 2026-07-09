package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "withdrawals")
data class Withdrawal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val amount: Double,
    val paymentMethod: String, // JazzCash, UPaisa, SadaPay, EasyPaisa
    val accountNumber: String,
    val accountName: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val timestamp: Long = System.currentTimeMillis()
)
