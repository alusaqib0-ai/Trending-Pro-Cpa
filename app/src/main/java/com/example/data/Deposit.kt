package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deposits")
data class Deposit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val transId: String,
    val amount: Double,
    val timestamp: Long,
    val status: String = "Pending"
)
