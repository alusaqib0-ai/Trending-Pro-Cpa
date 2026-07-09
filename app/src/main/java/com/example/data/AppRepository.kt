package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    suspend fun getUserByUsername(username: String): User? {
        return appDao.getUserByUsername(username)
    }

    fun getAllUsersFlow(): Flow<List<User>> = appDao.getAllUsersFlow()

    suspend fun insertUser(user: User) {
        appDao.insertUser(user)
    }

    suspend fun updateUser(user: User) {
        appDao.updateUser(user)
    }

    fun getActivePlansForUserFlow(username: String): Flow<List<ActivePlan>> =
        appDao.getActivePlansForUserFlow(username)

    fun getAllActivePlansFlow(): Flow<List<ActivePlan>> =
        appDao.getAllActivePlansFlow()

    suspend fun insertActivePlan(plan: ActivePlan) {
        appDao.insertActivePlan(plan)
    }

    suspend fun updateActivePlan(plan: ActivePlan) {
        appDao.updateActivePlan(plan)
    }

    fun getWithdrawalsForUserFlow(username: String): Flow<List<Withdrawal>> =
        appDao.getWithdrawalsForUserFlow(username)

    fun getAllWithdrawalsFlow(): Flow<List<Withdrawal>> =
        appDao.getAllWithdrawalsFlow()

    suspend fun insertWithdrawal(withdrawal: Withdrawal) {
        appDao.insertWithdrawal(withdrawal)
    }

    suspend fun updateWithdrawal(withdrawal: Withdrawal) {
        appDao.updateWithdrawal(withdrawal)
    }

    fun getDepositsForUserFlow(username: String): Flow<List<DepositRequest>> =
        appDao.getDepositsForUserFlow(username)

    fun getAllDepositsFlow(): Flow<List<DepositRequest>> =
        appDao.getAllDepositsFlow()

    suspend fun insertDeposit(deposit: DepositRequest) {
        appDao.insertDeposit(deposit)
    }

    suspend fun updateDeposit(deposit: DepositRequest) {
        appDao.updateDeposit(deposit)
    }
}
