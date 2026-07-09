package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deposit_requests")
data class DepositRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val transId: String,
    val amount: Double,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val timestamp: Long = System.currentTimeMillis(),
    val linkedPlanIndex: Int = -1, // -1 means regular wallet deposit
    val linkedPlanName: String? = null,
    val linkedPlanProfit: Double = 0.0
)
