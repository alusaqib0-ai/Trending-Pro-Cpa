package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Users
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    // Active Plans
    @Query("SELECT * FROM active_plans WHERE username = :username")
    fun getActivePlansForUserFlow(username: String): Flow<List<ActivePlan>>

    @Query("SELECT * FROM active_plans")
    fun getAllActivePlansFlow(): Flow<List<ActivePlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivePlan(plan: ActivePlan)

    @Update
    suspend fun updateActivePlan(plan: ActivePlan)

    // Withdrawals
    @Query("SELECT * FROM withdrawals WHERE username = :username ORDER BY timestamp DESC")
    fun getWithdrawalsForUserFlow(username: String): Flow<List<Withdrawal>>

    @Query("SELECT * FROM withdrawals ORDER BY timestamp DESC")
    fun getAllWithdrawalsFlow(): Flow<List<Withdrawal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: Withdrawal)

    @Update
    suspend fun updateWithdrawal(withdrawal: Withdrawal)

    // Deposit Requests
    @Query("SELECT * FROM deposit_requests WHERE username = :username ORDER BY timestamp DESC")
    fun getDepositsForUserFlow(username: String): Flow<List<DepositRequest>>

    @Query("SELECT * FROM deposit_requests ORDER BY timestamp DESC")
    fun getAllDepositsFlow(): Flow<List<DepositRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeposit(deposit: DepositRequest)

    @Update
    suspend fun updateDeposit(deposit: DepositRequest)
}
