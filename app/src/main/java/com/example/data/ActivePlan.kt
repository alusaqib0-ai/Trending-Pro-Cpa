package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_plans")
data class ActivePlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val planIndex: Int,
    val planName: String,
    val depositAmount: Double,
    val dailyProfit: Double,
    val activationTime: Long = System.currentTimeMillis(),
    val validityDays: Int = 40,
    val status: String = "ACTIVE" // ACTIVE, EXPIRED
)
