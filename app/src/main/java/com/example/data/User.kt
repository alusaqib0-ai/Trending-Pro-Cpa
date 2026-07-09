package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val passwordHash: String,
    val balance: Double = 1500.0,
    val referredBy: String? = null,
    val referralEarnings: Double = 0.0
)
