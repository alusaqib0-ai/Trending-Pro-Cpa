package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class Screen { AUTH, HOME, EARN, TEAM, WALLET, ADMIN }

    data class PlanData(val name: String, val deposit: Double, val profit: Double)

    val investmentPlans = listOf(
        PlanData("Plan 1", 1000.0, 100.0),
        PlanData("Plan 2", 2500.0, 260.0),
        PlanData("Plan 3", 5000.0, 540.0),
        PlanData("Plan 4", 8000.0, 900.0),
        PlanData("Plan 5", 12000.0, 1400.0),
        PlanData("Plan 6", 18000.0, 2200.0),
        PlanData("Plan 7", 25000.0, 3200.0),
        PlanData("Plan 8", 35000.0, 4600.0),
        PlanData("Plan 9", 50000.0, 6800.0),
        PlanData("Plan 10", 70000.0, 9800.0),
        PlanData("Plan 11", 90000.0, 13000.0),
        PlanData("Plan 12", 120000.0, 18000.0),
        PlanData("Plan 13", 150000.0, 23500.0),
        PlanData("Plan 14", 200000.0, 32000.0)
    )

    private val repository: AppRepository

    private val _currentScreen = MutableStateFlow(Screen.AUTH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _ticker = MutableStateFlow(System.currentTimeMillis())
    val ticker: StateFlow<Long> = _ticker.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.appDao())

        // Start real-time profit ticking and ticker updates
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                _ticker.value = System.currentTimeMillis()

                // Live dynamic fractional profit ticking
                val curUser = _currentUser.value
                if (curUser != null) {
                    val activeUserPlans = repository.getActivePlansForUserFlow(curUser.username).first()
                    val activeRunning = activeUserPlans.filter { it.status == "ACTIVE" }
                    if (activeRunning.isNotEmpty()) {
                        val fractionalProfitPerSecond = activeRunning.sumOf { it.dailyProfit / 86400.0 }
                        if (fractionalProfitPerSecond > 0.0) {
                            val freshUser = repository.getUserByUsername(curUser.username)
                            if (freshUser != null) {
                                val updated = freshUser.copy(balance = freshUser.balance + fractionalProfitPerSecond)
                                repository.updateUser(updated)
                                _currentUser.value = updated
                            }
                        }
                    }
                }
            }
        }
    }

    // Dynamic Lists & Admin Flows
    val allUsers: StateFlow<List<User>> = repository.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allWithdrawals: StateFlow<List<Withdrawal>> = repository.getAllWithdrawalsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allDeposits: StateFlow<List<DepositRequest>> = repository.getAllDepositsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // User Specific Lists
    val userDeposits: StateFlow<List<DepositRequest>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getDepositsForUserFlow(user.username)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val userWithdrawals: StateFlow<List<Withdrawal>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getWithdrawalsForUserFlow(user.username)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activePlans: StateFlow<List<ActivePlan>> = _currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList())
        else repository.getActivePlansForUserFlow(user.username)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Referral Team Dynamic Calculations (L1: 13%, L2: 4%, L3: 2%)
    val teamLevel1: StateFlow<List<User>> = combine(allUsers, _currentUser) { users, curUser ->
        if (curUser == null) emptyList()
        else users.filter { it.referredBy == curUser.username }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val teamLevel2: StateFlow<List<User>> = combine(allUsers, teamLevel1) { users, l1 ->
        val l1Names = l1.map { it.username }.toSet()
        if (l1Names.isEmpty()) emptyList()
        else users.filter { it.referredBy in l1Names }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val teamLevel3: StateFlow<List<User>> = combine(allUsers, teamLevel2) { users, l2 ->
        val l2Names = l2.map { it.username }.toSet()
        if (l2Names.isEmpty()) emptyList()
        else users.filter { it.referredBy in l2Names }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ----------------- AUTHENTICATION METHODS -----------------

    fun login(usernameInput: String, passwordInput: String) {
        val usernameClean = usernameInput.trim()
        val passwordClean = passwordInput.trim()

        if (usernameClean.isEmpty() || passwordClean.isEmpty()) {
            _authError.value = "Kindly Username aur Password enter karein!"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Check for admin default profile creation for test convenience
            if (usernameClean.lowercase() == "admin" && passwordClean == "admin123") {
                val adminUser = repository.getUserByUsername("admin")
                if (adminUser == null) {
                    val newAdmin = User(username = "admin", passwordHash = "admin123", balance = 50000.0)
                    repository.insertUser(newAdmin)
                }
            }

            val user = repository.getUserByUsername(usernameClean)
            if (user == null) {
                _authError.value = "Username mojood nahi hai!"
            } else if (user.passwordHash != passwordClean) {
                _authError.value = "Ghalat password enter kiya hai!"
            } else {
                _authError.value = null
                _currentUser.value = user
                _currentScreen.value = Screen.HOME
            }
        }
    }

    fun register(usernameInput: String, passwordInput: String, referralInput: String = "") {
        val usernameClean = usernameInput.trim()
        val passwordClean = passwordInput.trim()
        val referralClean = referralInput.trim()

        if (usernameClean.isEmpty() || passwordClean.isEmpty()) {
            _authError.value = "Kindly Username aur Password enter karein!"
            return
        }

        if (usernameClean.lowercase() == "admin") {
            _authError.value = "Admin username register nahi kiya ja sakta!"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getUserByUsername(usernameClean)
            if (existing != null) {
                _authError.value = "Yeh username pehle se mojood hai!"
                return@launch
            }

            var referrer: String? = null
            if (referralClean.isNotEmpty()) {
                val refUser = repository.getUserByUsername(referralClean)
                if (refUser == null) {
                    _authError.value = "Referral code (username) ghalat hai!"
                    return@launch
                }
                referrer = refUser.username
            }

            val newUser = User(
                username = usernameClean,
                passwordHash = passwordClean,
                balance = 1500.0, // starting balance gift
                referredBy = referrer
            )
            repository.insertUser(newUser)

            _authError.value = null
            _currentUser.value = newUser
            _currentScreen.value = Screen.HOME
        }
    }

    fun logout() {
        _currentUser.value = null
        _currentScreen.value = Screen.AUTH
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    // ----------------- TRANSACTION FLOWS -----------------

    fun submitDeposit(
        transId: String,
        amount: Double,
        linkedPlanIndex: Int = -1,
        linkedPlanName: String? = null
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val request = DepositRequest(
                username = user.username,
                transId = transId.trim(),
                amount = amount,
                linkedPlanIndex = linkedPlanIndex,
                linkedPlanName = linkedPlanName
            )
            repository.insertDeposit(request)
        }
    }

    fun submitWithdrawal(
        amount: Double,
        method: String,
        accountNum: String,
        accountName: String
    ): String? {
        val user = _currentUser.value ?: return "Aap logged in nahi hain!"
        if (amount <= 0) return "Sahi withdrawal amount enter karein!"
        if (user.balance < amount) return "Aapka available balance na-kafi hai!"
        if (accountNum.trim().isEmpty() || accountName.trim().isEmpty()) return "Account info enter karein!"

        viewModelScope.launch(Dispatchers.IO) {
            // Deduct immediately to prevent double spending
            val updatedUser = user.copy(balance = user.balance - amount)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            val withdrawal = Withdrawal(
                username = user.username,
                amount = amount,
                paymentMethod = method,
                accountNumber = accountNum.trim(),
                accountName = accountName.trim()
            )
            repository.insertWithdrawal(withdrawal)
        }
        return null
    }

    // ----------------- PLAN ACTIVATION -----------------

    fun activatePlan(planIndex: Int, planName: String, deposit: Double, dailyProfit: Double): Boolean {
        val user = _currentUser.value ?: return false
        if (user.balance < deposit) return false

        viewModelScope.launch(Dispatchers.IO) {
            val updatedUser = user.copy(balance = user.balance - deposit)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            val activePlan = ActivePlan(
                username = user.username,
                planIndex = planIndex,
                planName = planName,
                depositAmount = deposit,
                dailyProfit = dailyProfit
            )
            repository.insertActivePlan(activePlan)

            // Distribute bonuses (L1: 13%, L2: 4%, L3: 2%)
            distributeReferralBonuses(user.username, deposit)
        }
        return true
    }

    private suspend fun distributeReferralBonuses(buyerName: String, planDeposit: Double) {
        val buyer = repository.getUserByUsername(buyerName) ?: return
        val l1ReferrerName = buyer.referredBy ?: return

        // Level 1 Referrer gets 13% commission
        val l1Referrer = repository.getUserByUsername(l1ReferrerName)
        if (l1Referrer != null) {
            val bonus1 = planDeposit * 0.13
            val updatedL1 = l1Referrer.copy(balance = l1Referrer.balance + bonus1)
            repository.updateUser(updatedL1)

            // Level 2 Referrer gets 4% commission
            val l2ReferrerName = l1Referrer.referredBy
            if (!l2ReferrerName.isNullOrBlank()) {
                val l2Referrer = repository.getUserByUsername(l2ReferrerName)
                if (l2Referrer != null) {
                    val bonus2 = planDeposit * 0.04
                    val updatedL2 = l2Referrer.copy(balance = l2Referrer.balance + bonus2)
                    repository.updateUser(updatedL2)

                    // Level 3 Referrer gets 2% commission
                    val l3ReferrerName = l2Referrer.referredBy
                    if (!l3ReferrerName.isNullOrBlank()) {
                        val l3Referrer = repository.getUserByUsername(l3ReferrerName)
                        if (l3Referrer != null) {
                            val bonus3 = planDeposit * 0.02
                            val updatedL3 = l3Referrer.copy(balance = l3Referrer.balance + bonus3)
                            repository.updateUser(updatedL3)
                        }
                    }
                }
            }
        }
    }

    // ----------------- ADMIN CONTROLS -----------------

    fun approveDeposit(deposit: DepositRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedDeposit = deposit.copy(status = "APPROVED")
            repository.updateDeposit(updatedDeposit)

            val depositor = repository.getUserByUsername(deposit.username)
            if (depositor != null) {
                if (deposit.linkedPlanIndex != -1) {
                    // Activate plan directly
                    val planIndex = deposit.linkedPlanIndex
                    val planName = deposit.linkedPlanName ?: "Plan"
                    val planProfit = investmentPlans.getOrNull(planIndex)?.profit ?: 0.0

                    val activePlan = ActivePlan(
                        username = depositor.username,
                        planIndex = planIndex,
                        planName = planName,
                        depositAmount = deposit.amount,
                        dailyProfit = planProfit
                    )
                    repository.insertActivePlan(activePlan)

                    // Distribute referral bonuses
                    distributeReferralBonuses(depositor.username, deposit.amount)
                } else {
                    // Regular deposit, update depositor balance
                    val updatedDepositor = depositor.copy(balance = depositor.balance + deposit.amount)
                    repository.updateUser(updatedDepositor)

                    if (_currentUser.value?.username == depositor.username) {
                        _currentUser.value = updatedDepositor
                    }
                }
            }
        }
    }

    fun rejectDeposit(deposit: DepositRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedDeposit = deposit.copy(status = "REJECTED")
            repository.updateDeposit(updatedDeposit)
        }
    }

    fun approveWithdrawal(withdrawal: Withdrawal) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedWithdrawal = withdrawal.copy(status = "APPROVED")
            repository.updateWithdrawal(updatedWithdrawal)
        }
    }

    fun rejectWithdrawal(withdrawal: Withdrawal) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedWithdrawal = withdrawal.copy(status = "REJECTED")
            repository.updateWithdrawal(updatedWithdrawal)

            // Refund user balance
            val requester = repository.getUserByUsername(withdrawal.username)
            if (requester != null) {
                val refunded = requester.copy(balance = requester.balance + withdrawal.amount)
                repository.updateUser(refunded)

                if (_currentUser.value?.username == requester.username) {
                    _currentUser.value = refunded
                }
            }
        }
    }

    fun editUserBalance(user: User, newBalance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedUser = user.copy(balance = newBalance)
            repository.updateUser(updatedUser)

            if (_currentUser.value?.username == user.username) {
                _currentUser.value = updatedUser
            }
        }
    }
}
