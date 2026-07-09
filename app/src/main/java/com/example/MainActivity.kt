package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Navigation Screens
enum class Screen {
    Auth, Dashboard
}

// Auth Modes
enum class AuthMode {
    Register, Login
}

// Bottom Tabs
enum class Tab {
    Home, Earn, Team, Wallet, Account
}

// Plan model matching HTML dataset
data class PlanModel(
    val index: Int,
    val name: String,
    val deposit: Int,
    val dailyProfit: Int
)

val ALL_PLANS = listOf(
    PlanModel(0, "Micro Alpha - P1", 205, 51),
    PlanModel(1, "Standard Beta - P2", 650, 116),
    PlanModel(2, "Titan Grade - P3", 1287, 216),
    PlanModel(3, "Nova Core - P4", 2656, 423),
    PlanModel(4, "Vanguard Prime - P5", 5760, 908),
    PlanModel(5, "Apex Sentinel - P6", 13486, 3178),
    PlanModel(6, "Equinox Elite - P7", 34765, 7237),
    PlanModel(7, "Zenith Master - P8", 76072, 18654),
    PlanModel(8, "Infinity Max - P9", 108762, 25295),
    PlanModel(9, "Aether Aura - P10", 157652, 38662),
    PlanModel(10, "Matrix Elite - P11", 203976, 50960),
    PlanModel(11, "Stellar Grand - P12", 265771, 66470),
    PlanModel(12, "Quantum Apex - P13", 310976, 77897),
    PlanModel(13, "Cosmic Titan - P14", 368177, 88028)
)

class AppViewModel(private val repository: AppRepository) : ViewModel() {

    // Auth state
    val currentScreen = MutableStateFlow(Screen.Auth)
    val authMode = MutableStateFlow(AuthMode.Register)
    val usernameInput = MutableStateFlow("")
    val passwordInput = MutableStateFlow("")
    val referralCodeInput = MutableStateFlow("")
    val loggedInUser = MutableStateFlow<User?>(null)

    // Dashboard navigation tab
    val currentTab = MutableStateFlow(Tab.Home)

    // Real-time ticking state
    val tickerFlow = MutableStateFlow(System.currentTimeMillis())

    // Database updates collected for current user
    val activePlans = MutableStateFlow<List<ActivePlan>>(emptyList())
    val deposits = MutableStateFlow<List<DepositRequest>>(emptyList())
    val withdrawals = MutableStateFlow<List<Withdrawal>>(emptyList())
    val referredTeam = MutableStateFlow<List<User>>(emptyList())

    // Modals visibility
    val showDepositModal = MutableStateFlow(false)
    val showWithdrawModal = MutableStateFlow(false)

    // Modal Input fields
    val transIdInput = MutableStateFlow("")
    val depositAmountInput = MutableStateFlow("")
    val selectedSlipUri = MutableStateFlow<Uri?>(null)
    
    // Withdraw inputs
    val withdrawAmountInput = MutableStateFlow("")
    val selectedWithdrawMethod = MutableStateFlow("EasyPaisa")
    val withdrawAccountNumberInput = MutableStateFlow("")
    val withdrawAccountNameInput = MutableStateFlow("")
    val withdrawBankNameInput = MutableStateFlow("")

    private var activePlansJob: Job? = null
    private var depositsJob: Job? = null
    private var withdrawalsJob: Job? = null
    private var tickerJob: Job? = null
    private var teamJob: Job? = null

    init {
        // Start live update clock for timers & dynamic profits
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                tickerFlow.value = System.currentTimeMillis()
            }
        }
    }

    fun toggleAuthMode() {
        authMode.value = if (authMode.value == AuthMode.Register) AuthMode.Login else AuthMode.Register
    }

    fun handleAuth(context: Context) {
        val username = usernameInput.value.trim()
        val password = passwordInput.value.trim()
        val referralCode = referralCodeInput.value.trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Kindly Username aur Password dono fill karein!", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            val existingUser = repository.getUserByUsername(username)
            if (authMode.value == AuthMode.Register) {
                if (existingUser != null) {
                    Toast.makeText(context, "Username already exists. Please login.", Toast.LENGTH_LONG).show()
                } else {
                    var refByUser: String? = null
                    if (referralCode.isNotEmpty()) {
                        val referrer = repository.getUserByUsername(referralCode)
                        if (referrer != null) {
                            refByUser = referrer.username
                            // Give referrer Rs. 300 referral bonus!
                            val updatedReferrer = referrer.copy(
                                balance = referrer.balance + 300.0,
                                referralEarnings = referrer.referralEarnings + 300.0
                            )
                            repository.updateUser(updatedReferrer)
                        } else {
                            Toast.makeText(context, "Referral code incorrect! Baghair referral ke register ho raha hai.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    val newUser = User(
                        username = username, 
                        passwordHash = password, 
                        balance = 1500.0, 
                        referredBy = refByUser
                    )
                    repository.insertUser(newUser)
                    loggedInUser.value = newUser
                    onUserLoggedIn(username)
                    
                    if (refByUser != null) {
                        Toast.makeText(context, "Registration Success! Rs. 1,500 credited & Rs. 300 referral bonus given to $refByUser!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Registration Success! Demo Rs. 1,500 credited.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (existingUser == null || existingUser.passwordHash != password) {
                    Toast.makeText(context, "Incorrect Username or Password!", Toast.LENGTH_LONG).show()
                } else {
                    loggedInUser.value = existingUser
                    onUserLoggedIn(username)
                    Toast.makeText(context, "Welcome back, $username!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onUserLoggedIn(username: String) {
        currentScreen.value = Screen.Dashboard
        
        activePlansJob?.cancel()
        activePlansJob = viewModelScope.launch {
            repository.getActivePlansForUserFlow(username).collect {
                activePlans.value = it
            }
        }

        depositsJob?.cancel()
        depositsJob = viewModelScope.launch {
            repository.getDepositsForUserFlow(username).collect {
                deposits.value = it
            }
        }

        withdrawalsJob?.cancel()
        withdrawalsJob = viewModelScope.launch {
            repository.getWithdrawalsForUserFlow(username).collect {
                withdrawals.value = it
            }
        }

        teamJob?.cancel()
        teamJob = viewModelScope.launch {
            repository.getAllUsersFlow().collect { allUsers ->
                referredTeam.value = allUsers.filter { it.referredBy == username }
            }
        }
    }

    fun logout() {
        currentScreen.value = Screen.Auth
        loggedInUser.value = null
        currentTab.value = Tab.Home
        activePlansJob?.cancel()
        depositsJob?.cancel()
        withdrawalsJob?.cancel()
        teamJob?.cancel()
        activePlans.value = emptyList()
        deposits.value = emptyList()
        withdrawals.value = emptyList()
        referredTeam.value = emptyList()
        usernameInput.value = ""
        passwordInput.value = ""
        referralCodeInput.value = ""
        withdrawAmountInput.value = ""
        withdrawAccountNumberInput.value = ""
        withdrawAccountNameInput.value = ""
        withdrawBankNameInput.value = ""
    }

    fun submitDeposit(context: Context) {
        val txId = transIdInput.value.trim()
        val amountStr = depositAmountInput.value.trim()
        val user = loggedInUser.value ?: return

        if (txId.isEmpty() || amountStr.isEmpty() || selectedSlipUri.value == null) {
            Toast.makeText(context, "Kindly Transaction ID, Amount aur Payment Slip select karein!", Toast.LENGTH_LONG).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(context, "Valid deposit amount enter karein!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            // Save the Pending deposit request in database
            val deposit = DepositRequest(
                username = user.username,
                transId = txId,
                amount = amount,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )
            repository.insertDeposit(deposit)

            // Trigger WhatsApp redirection with pre-filled message text
            val whatsappNumber = "923143709782"
            val message = "*🔥 New Deposit Request - Trading Pro *\n\n" +
                    "*👤 Username:* ${user.username}\n" +
                    "*🆔 Transaction ID:* $txId\n" +
                    "*💰 Amount:* Rs. $amount\n" +
                    "*💳 Method:* EasyPaisa (Rahat Ali)\n\n" +
                    "_Main agle step mein payment slip attach kar raha/rahi hoon._"

            val encodedMsg = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
            val whatsappUrl = "https://wa.me/$whatsappNumber?text=$encodedMsg"

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Aapko WhatsApp par bheja ja raha hai. Screenshot lazmi attach karein!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp open nahi ho saka: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }

            // Clear inputs & close modal
            transIdInput.value = ""
            depositAmountInput.value = ""
            selectedSlipUri.value = null
            showDepositModal.value = false
        }
    }

    // Demo feature for instant self-approval of deposits (prevents dead end)
    fun simulateApproveDeposit(deposit: DepositRequest, context: Context) {
        val user = loggedInUser.value ?: return
        viewModelScope.launch {
            // Update deposit status to Approved
            val updatedDeposit = deposit.copy(status = "APPROVED")
            repository.updateDeposit(updatedDeposit)

            // Credit the amount to user's persistent balance
            val updatedUser = user.copy(balance = user.balance + deposit.amount)
            repository.updateUser(updatedUser)
            loggedInUser.value = updatedUser

            Toast.makeText(context, "Deposit of Rs. ${deposit.amount} successfully approved in Demo Mode!", Toast.LENGTH_SHORT).show()
        }
    }

    fun submitWithdraw(context: Context) {
        val amountStr = withdrawAmountInput.value.trim()
        val user = loggedInUser.value ?: return

        if (amountStr.isEmpty()) {
            Toast.makeText(context, "Kindly withdrawal amount enter karein!", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(context, "Valid amount enter karein!", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount > user.balance) {
            Toast.makeText(context, "Insufficient balance! Available: Rs. ${user.balance}", Toast.LENGTH_LONG).show()
            return
        }

        val method = selectedWithdrawMethod.value
        val number = withdrawAccountNumberInput.value.trim()
        val accountTitle = withdrawAccountNameInput.value.trim()
        val bankName = withdrawBankNameInput.value.trim()

        if (number.isEmpty() || accountTitle.isEmpty()) {
            Toast.makeText(context, "Kindly account details mukammal fill karein!", Toast.LENGTH_SHORT).show()
            return
        }

        if (method == "Bank Account" && bankName.isEmpty()) {
            Toast.makeText(context, "Kindly Bank Name enter karein!", Toast.LENGTH_SHORT).show()
            return
        }

        val finalPaymentMethod = if (method == "Bank Account") "Bank: $bankName" else method

        viewModelScope.launch {
            // Update user balance in Room database
            val updatedUser = user.copy(balance = user.balance - amount)
            repository.updateUser(updatedUser)
            loggedInUser.value = updatedUser

            // Add simulated approved/pending withdraw transaction record
            val withdrawRecord = Withdrawal(
                username = user.username,
                amount = amount,
                paymentMethod = finalPaymentMethod,
                accountNumber = number,
                accountName = accountTitle,
                status = "APPROVED",
                timestamp = System.currentTimeMillis()
            )
            repository.insertWithdrawal(withdrawRecord)

            withdrawAmountInput.value = ""
            withdrawAccountNumberInput.value = ""
            withdrawAccountNameInput.value = ""
            withdrawBankNameInput.value = ""
            showWithdrawModal.value = false
            Toast.makeText(context, "Withdrawal request of Rs. $amount approved instantly!", Toast.LENGTH_LONG).show()
        }
    }

    fun simulateReferralJoin(context: Context) {
        val user = loggedInUser.value ?: return
        viewModelScope.launch {
            val randomSuffix = (1000..9999).random()
            val dummyUsername = "team_user_$randomSuffix"
            
            // Give this user Rs. 300 referral bonus!
            val updatedUser = user.copy(
                balance = user.balance + 300.0,
                referralEarnings = user.referralEarnings + 300.0
            )
            repository.updateUser(updatedUser)
            loggedInUser.value = updatedUser

            // Insert the dummy referred user
            val dummyUser = User(
                username = dummyUsername,
                passwordHash = "password123",
                balance = 1500.0,
                referredBy = user.username
            )
            repository.insertUser(dummyUser)

            Toast.makeText(context, "Demo Referral! $dummyUsername joined. Rs. 300 credited to your balance!", Toast.LENGTH_LONG).show()
        }
    }

    fun activatePlan(plan: PlanModel, context: Context) {
        val user = loggedInUser.value ?: return

        if (user.balance < plan.deposit) {
            Toast.makeText(context, "Insufficient Balance! Kindly EasyPaisa deposit karein ya free demo request approve karein.", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            // Deduct the investment amount
            val updatedUser = user.copy(balance = user.balance - plan.deposit)
            repository.updateUser(updatedUser)
            loggedInUser.value = updatedUser

            // Save active plan to Room
            val activePlan = ActivePlan(
                username = user.username,
                planIndex = plan.index,
                planName = plan.name,
                depositAmount = plan.deposit.toDouble(),
                dailyProfit = plan.dailyProfit.toDouble(),
                activationTime = System.currentTimeMillis()
            )
            repository.insertActivePlan(activePlan)

            // 13% Referral bonus to the referrer
            val referrerUsername = user.referredBy
            if (!referrerUsername.isNullOrEmpty()) {
                val referrer = repository.getUserByUsername(referrerUsername)
                if (referrer != null) {
                    val bonus = plan.deposit * 0.13
                    val updatedReferrer = referrer.copy(
                        balance = referrer.balance + bonus,
                        referralEarnings = referrer.referralEarnings + bonus
                    )
                    repository.updateUser(updatedReferrer)
                    // If current logged in user is the referrer, update the UI state
                    if (loggedInUser.value?.username == referrerUsername) {
                        loggedInUser.value = updatedReferrer
                    }
                    Toast.makeText(context, "${plan.name} active ho gaya! Referrer $referrerUsername ko Rs. ${"%,.0f".format(bonus)} (13%) bonus mil gaya hai.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "${plan.name} kamyabi se active ho chuka hai! Timer shuru ho gaya hai.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "${plan.name} kamyabi se active ho chuka hai! Timer shuru ho gaya hai.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room and Repository instantiations
        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao())

        setContent {
            MyApplicationTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(repository))
                val screen by viewModel.currentScreen.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BgDark
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (screen) {
                            Screen.Auth -> AuthScreen(viewModel)
                            Screen.Dashboard -> DashboardScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- COMPOSE SCREEN COMPONENTS ----------------

@Composable
fun AuthScreen(viewModel: AppViewModel) {
    val authMode by viewModel.authMode.collectAsState()
    val username by viewModel.usernameInput.collectAsState()
    val password by viewModel.passwordInput.collectAsState()
    val referralCode by viewModel.referralCodeInput.collectAsState()
    val context = LocalContext.current

    var isPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .background(CardBg, shape = RoundedCornerShape(28.dp))
                .border(1.dp, FieldBorder, shape = RoundedCornerShape(28.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(PrimaryEmerald, DarkEmerald)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Trading Pro Icon",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TRADING PRO",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald,
                    letterSpacing = 2.sp
                )
            )

            Text(
                text = if (authMode == AuthMode.Register) "Register Trading Pro" else "Login Account",
                color = TextLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Username input
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.usernameInput.value = it },
                label = { Text("Username", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password input
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.passwordInput.value = it },
                label = { Text("Password", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Password Visibility",
                            tint = TextMuted
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input")
            )

            if (authMode == AuthMode.Register) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = referralCode,
                    onValueChange = { viewModel.referralCodeInput.value = it },
                    label = { Text("Referral Code (Optional)", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = FieldBorder,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("referral_code_input")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = { viewModel.handleAuth(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryEmerald,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if (authMode == AuthMode.Register) "Register Account" else "Login Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle mode switcher
            Text(
                text = if (authMode == AuthMode.Register) "Already have an account? Login" else "Don't have an account? Register",
                color = PrimaryEmerald,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { viewModel.toggleAuthMode() }
                    .padding(8.dp)
                    .testTag("toggle_auth_mode")
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val tab by viewModel.currentTab.collectAsState()
    val showDeposit by viewModel.showDepositModal.collectAsState()
    val showWithdraw by viewModel.showWithdrawModal.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = tab,
                onTabSelected = { viewModel.currentTab.value = it }
            )
        },
        containerColor = BgDark
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                Tab.Home -> HomeScreen(viewModel)
                Tab.Earn -> EarnScreen(viewModel)
                Tab.Team -> TeamScreen(viewModel)
                Tab.Wallet -> WalletScreen(viewModel)
                Tab.Account -> AccountScreen(viewModel)
            }

            // EasyPaisa Deposit Modal Overlay
            if (showDeposit) {
                DepositModal(viewModel)
            }

            // Withdraw Modal Overlay
            if (showWithdraw) {
                WithdrawModal(viewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Tab, onTabSelected: (Tab) -> Unit) {
    NavigationBar(
        containerColor = BgDark,
        tonalElevation = 8.dp,
        modifier = Modifier
            .height(80.dp)
            .border(width = (0.5).dp, color = FieldBorder.copy(alpha = 0.5f))
    ) {
        val tabs = listOf(
            Triple(Tab.Home, "Home", Icons.Default.Home),
            Triple(Tab.Earn, "Earn", Icons.Default.TrendingUp),
            Triple(Tab.Team, "Team", Icons.Default.People),
            Triple(Tab.Wallet, "Wallet", Icons.Default.AccountBalanceWallet),
            Triple(Tab.Account, "Account", Icons.Default.Person)
        )

        tabs.forEach { (tab, label, icon) ->
            val isSelected = selectedTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) PrimaryEmerald else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        color = if (isSelected) PrimaryEmerald else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = PrimaryEmerald.copy(alpha = 0.1f)
                ),
                modifier = Modifier.testTag("tab_${label.lowercase()}")
            )
        }
    }
}

// ---------------- TAB 1: HOME SCREEN (PLANS GRID) ----------------

@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val user by viewModel.loggedInUser.collectAsState()
    val activePlans by viewModel.activePlans.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Trading Pro",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Dashboard",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextLight
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(CardBg, shape = CircleShape)
                    .border(1.dp, FieldBorder, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile icon",
                    tint = PrimaryEmerald,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Balance Card
        BalanceCard(
            balance = user?.balance ?: 0.0,
            onDepositClick = { viewModel.showDepositModal.value = true },
            onWithdrawClick = { viewModel.showWithdrawModal.value = true }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Investment Plans",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )
            Text(
                text = "14 Total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryEmerald
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of 14 plans
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                WhatsAppGroupCard(context = context)
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(ALL_PLANS) { plan ->
                val isActive = activePlans.any { it.planIndex == plan.index }
                PlanCard(
                    plan = plan,
                    isActive = isActive,
                    onActivate = { viewModel.activatePlan(plan, context) }
                )
            }
        }
    }
}

@Composable
fun BalanceCard(
    balance: Double,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryEmerald, DarkEmerald)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Available Balance",
                        color = TextLight.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Rs. %,.2f".format(balance),
                            color = TextLight,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDepositClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("deposit_cash_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Deposit Icon",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Deposit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = onWithdrawClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("withdraw_cash_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BgDark.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Withdraw Icon",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Withdraw",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    plan: PlanModel,
    isActive: Boolean,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FieldBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan ${plan.index + 1}",
                    color = PrimaryEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .background(PrimaryEmerald.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = PrimaryEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plan.name,
                color = TextLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Plan Info Lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InfoRow(label = "Deposit:", value = "Rs. ${plan.deposit}")
                InfoRow(label = "Daily Profit:", value = "Rs. ${plan.dailyProfit}")
                InfoRow(label = "Validity:", value = "40 Days")
                InfoRow(
                    label = "Total Profit:",
                    value = "Rs. ${plan.dailyProfit * 40}",
                    valueColor = PrimaryEmerald
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isActive) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = FieldBorder,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Active ✔",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Button(
                    onClick = onActivate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activate_plan_${plan.index}_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryEmerald,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Activate",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextLight
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------- TAB 2: EARN SCREEN (ACTIVE PORTFOLIO) ----------------

@Composable
fun EarnScreen(viewModel: AppViewModel) {
    val activePlans by viewModel.activePlans.collectAsState()
    val tickerTime by viewModel.tickerFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Active Portfolio",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextLight
        )
        Text(
            text = "Track your dynamic investments and live earnings",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (activePlans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "No active investments",
                        tint = FieldBorder,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Active Plans",
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose any plan from the Home tab to activate live 40-days earnings!",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(activePlans) { activePlan ->
                    val planModel = ALL_PLANS.getOrNull(activePlan.planIndex)
                    if (planModel != null) {
                        ActivePlanProgressCard(
                            plan = planModel,
                            activatedAt = activePlan.activationTime,
                            currentTime = tickerTime
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivePlanProgressCard(
    plan: PlanModel,
    activatedAt: Long,
    currentTime: Long
) {
    val totalDurationMs = 40L * 24 * 60 * 60 * 1000
    val elapsedMs = currentTime - activatedAt
    val remainingMs = totalDurationMs - elapsedMs

    val isExpired = remainingMs <= 0
    val elapsedSeconds = if (isExpired) totalDurationMs / 1000 else elapsedMs / 1000
    val progress = if (isExpired) 1.0f else (elapsedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.0f, 1.0f)

    // Calculate live ticking profit (accrued per second)
    val dailyProfit = plan.dailyProfit
    val totalExpectedProfit = dailyProfit * 40
    val profitPerSec = dailyProfit / 86400.0
    val liveAccruedProfit = elapsedSeconds * profitPerSec

    // Formatting remaining countdown string
    val remainingString = if (isExpired) {
        "Expired"
    } else {
        val remainingSec = (remainingMs / 1000) % 60
        val remainingMin = (remainingMs / (1000 * 60)) % 60
        val remainingHrs = (remainingMs / (1000 * 60 * 60)) % 24
        val remainingDays = remainingMs / (1000 * 60 * 60 * 24)
        "${remainingDays}d ${remainingHrs}h ${remainingMin}m ${remainingSec}s Left"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FieldBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .background(PrimaryEmerald.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE TICKING",
                            color = PrimaryEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = plan.name,
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Daily Profit", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "Rs. ${plan.dailyProfit}",
                        color = PrimaryEmerald,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Live Profit display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Accrued Profit", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "Rs. %,.4f".format(liveAccruedProfit),
                        color = PrimaryEmerald,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Target (40d)", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = "Rs. $totalExpectedProfit",
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live remaining time ticker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Countdown Timer icon",
                        tint = if (isExpired) ButtonRed else PrimaryEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = remainingString,
                        color = if (isExpired) ButtonRed else TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "%.1f%%".format(progress * 100),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom colored Material Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (isExpired) ButtonRed else PrimaryEmerald,
                trackColor = SurfaceDark,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

// ---------------- TAB 3: WALLET SCREEN (TRANSACTION HISTORY & ACTIONS) ----------------

sealed class TransactionItem {
    abstract val timestamp: Long
    abstract val status: String
    abstract val amount: Double

    data class DepositTx(val deposit: DepositRequest) : TransactionItem() {
        override val timestamp = deposit.timestamp
        override val status = deposit.status
        override val amount = deposit.amount
    }

    data class WithdrawalTx(val withdrawal: Withdrawal) : TransactionItem() {
        override val timestamp = withdrawal.timestamp
        override val status = withdrawal.status
        override val amount = withdrawal.amount
    }
}

@Composable
fun WalletScreen(viewModel: AppViewModel) {
    val user by viewModel.loggedInUser.collectAsState()
    val deposits by viewModel.deposits.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Digital Wallet",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextLight
        )
        Text(
            text = "Manage your EasyPaisa deposits, withdrawals and history",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Duplicate Balance Card for fast transaction triggers
        BalanceCard(
            balance = user?.balance ?: 0.0,
            onDepositClick = { viewModel.showDepositModal.value = true },
            onWithdrawClick = { viewModel.showWithdrawModal.value = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Transaction list builder
        val combinedTransactions = remember(deposits, withdrawals) {
            val list = mutableListOf<TransactionItem>()
            list.addAll(deposits.map { TransactionItem.DepositTx(it) })
            list.addAll(withdrawals.map { TransactionItem.WithdrawalTx(it) })
            list.sortByDescending { it.timestamp }
            list
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaction Records",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )
            Text(
                text = "${combinedTransactions.size} Total",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryEmerald
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (combinedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "No transactions",
                        tint = FieldBorder,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Transactions Found",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(combinedTransactions) { tx ->
                    TransactionListItem(
                        tx = tx,
                        onApproveClick = {
                            if (tx is TransactionItem.DepositTx) {
                                viewModel.simulateApproveDeposit(tx.deposit, context)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(tx: TransactionItem, onApproveClick: () -> Unit) {
    val isWithdraw = tx is TransactionItem.WithdrawalTx
    val amountAbs = kotlin.math.abs(tx.amount)
    val transId = if (tx is TransactionItem.DepositTx) tx.deposit.transId else "WD-${tx.timestamp.toString().takeLast(6)}"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, FieldBorder.copy(alpha = 0.4f), shape = RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Direction icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isWithdraw) ButtonRed.copy(alpha = 0.15f) else PrimaryEmerald.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWithdraw) Icons.Default.ArrowOutward else Icons.Default.CallReceived,
                        contentDescription = "Transaction Direction icon",
                        tint = if (isWithdraw) ButtonRed else PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = if (isWithdraw) "Withdraw Cash" else "Deposit EasyPaisa",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Text(
                        text = "ID: $transId",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isWithdraw) "-" else "+"} Rs. $amountAbs",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isWithdraw) ButtonRed else PrimaryEmerald
                )

                Spacer(modifier = Modifier.height(4.dp))

                val statusText = tx.status
                if (statusText.equals("PENDING", ignoreCase = true)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEAB308).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Pending",
                                color = Color(0xFFFACC15),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Demo Quick Approval Button to avoid dead end
                        Button(
                            onClick = onApproveClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryEmerald,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(text = "Approve (Demo)", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(PrimaryEmerald.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Approved",
                            color = PrimaryEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------- TAB 4: ACCOUNT SCREEN (USER PROFILE & STATISTICS) ----------------

@Composable
fun AccountScreen(viewModel: AppViewModel) {
    val user by viewModel.loggedInUser.collectAsState()
    val activePlans by viewModel.activePlans.collectAsState()
    val deposits by viewModel.deposits.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()

    // Calculate dynamic stats
    val totalInvested = activePlans.sumOf {
        ALL_PLANS.getOrNull(it.planIndex)?.deposit ?: 0
    }
    val approvedDepositsSum = deposits.filter { it.status == "APPROVED" }.sumOf { it.amount }
    val totalWithdrawsSum = withdrawals.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "My Account",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextLight
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg, shape = RoundedCornerShape(24.dp))
                .border(1.dp, FieldBorder, shape = RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(listOf(PrimaryEmerald, DarkEmerald)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile icon huge",
                        tint = Color.Black,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = user?.username ?: "User",
                    color = TextLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Exclusive Trading Pro Member",
                    color = PrimaryEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Analytics Row Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                label = "Total Investment",
                value = "Rs. $totalInvested",
                icon = Icons.Default.TrendingUp,
                iconColor = PrimaryEmerald,
                modifier = Modifier.weight(1f)
            )

            StatBox(
                label = "Active Plans",
                value = "${activePlans.size} Active",
                icon = Icons.Default.Timer,
                iconColor = PrimaryEmerald,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                label = "Total Deposited",
                value = "Rs. %,.0f".format(approvedDepositsSum),
                icon = Icons.Default.Add,
                iconColor = PrimaryEmerald,
                modifier = Modifier.weight(1f)
            )

            StatBox(
                label = "Total Withdrawn",
                value = "Rs. %,.0f".format(totalWithdrawsSum),
                icon = Icons.Default.ArrowUpward,
                iconColor = ButtonRed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val context = LocalContext.current
        WhatsAppGroupCard(context = context)

        Spacer(modifier = Modifier.height(24.dp))

        // Logout Action
        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = ButtonRed.copy(alpha = 0.15f), contentColor = ButtonRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, ButtonRed.copy(alpha = 0.5f), shape = RoundedCornerShape(25.dp))
                .testTag("logout_button"),
            shape = RoundedCornerShape(25.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Logout icon",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LOGOUT ACCOUNT",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = modifier.border(0.5.dp, FieldBorder.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, color = TextMuted, fontSize = 11.sp)
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------- EASYPAISA DEPOSIT MODAL ----------------

@Composable
fun DepositModal(viewModel: AppViewModel) {
    val txId by viewModel.transIdInput.collectAsState()
    val amount by viewModel.depositAmountInput.collectAsState()
    val selectedUri by viewModel.selectedSlipUri.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.selectedSlipUri.value = uri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { /* Block clicks to background */ }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .background(CardBg, shape = RoundedCornerShape(28.dp))
                .border(1.dp, FieldBorder, shape = RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deposit via EasyPaisa",
                    color = PrimaryEmerald,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.showDepositModal.value = false }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Dialog", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // EasyPaisa Credentials display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Account Number: ", color = TextMuted, fontSize = 13.sp)
                    Text(text = "03475104768", color = PrimaryEmerald, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Account Name: ", color = TextMuted, fontSize = 13.sp)
                    Text(text = "Rahat Ali", color = TextLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deposit Amount Input
            OutlinedTextField(
                value = amount,
                onValueChange = { viewModel.depositAmountInput.value = it },
                label = { Text("Deposit Amount (Rs.)", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction ID input
            OutlinedTextField(
                value = txId,
                onValueChange = { viewModel.transIdInput.value = it },
                label = { Text("Enter 11 Digit Transaction ID", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Screenshot attachment field
            Text(
                text = "Payment Slip/Screenshot select karein:",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(SurfaceDark, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, FieldBorder, shape = RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedUri?.path?.takeLast(24) ?: "Choose Image File...",
                        color = if (selectedUri != null) PrimaryEmerald else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File icon",
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = { viewModel.submitDeposit(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("submit_deposit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = Color.Black),
                shape = RoundedCornerShape(23.dp)
            ) {
                Text(text = "Submit via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.showDepositModal.value = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, ButtonRed.copy(alpha = 0.5f), shape = RoundedCornerShape(23.dp))
                    .testTag("cancel_deposit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonRed.copy(alpha = 0.15f), contentColor = ButtonRed),
                shape = RoundedCornerShape(23.dp)
            ) {
                Text(text = "Cancel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ---------------- WITHDRAW MODAL ----------------

@Composable
fun WithdrawModal(viewModel: AppViewModel) {
    val amount by viewModel.withdrawAmountInput.collectAsState()
    val selectedMethod by viewModel.selectedWithdrawMethod.collectAsState()
    val accountNumber by viewModel.withdrawAccountNumberInput.collectAsState()
    val accountName by viewModel.withdrawAccountNameInput.collectAsState()
    val bankName by viewModel.withdrawBankNameInput.collectAsState()
    val user by viewModel.loggedInUser.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { /* Block clicks to background */ }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .background(CardBg, shape = RoundedCornerShape(28.dp))
                .border(1.dp, FieldBorder, shape = RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Withdraw Cash",
                    color = PrimaryEmerald,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.showWithdrawModal.value = false }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Dialog", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Method Selector
            Text(
                text = "Select Payment Method",
                color = TextLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val methods = listOf("EasyPaisa", "JazzCash", "Bank Account")
                methods.forEach { method ->
                    val isSelected = selectedMethod == method
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) PrimaryEmerald.copy(alpha = 0.15f) else SurfaceDark,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) PrimaryEmerald else FieldBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectedWithdrawMethod.value = method }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = method,
                            color = if (isSelected) PrimaryEmerald else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Inputs based on selection
            if (selectedMethod == "Bank Account") {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { viewModel.withdrawBankNameInput.value = it },
                    label = { Text("Bank Name (e.g. Meezan, HBL)", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = FieldBorder,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("withdraw_bank_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = accountNumber,
                onValueChange = { viewModel.withdrawAccountNumberInput.value = it },
                label = { 
                    Text(
                        text = if (selectedMethod == "Bank Account") "Account Number / IBAN" else "Mobile Number / Account No", 
                        color = TextMuted
                    ) 
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_account_number_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = accountName,
                onValueChange = { viewModel.withdrawAccountNameInput.value = it },
                label = { Text("Account Title / Holder Name", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_account_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { viewModel.withdrawAmountInput.value = it },
                label = { Text("Enter Amount (Rs.)", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = FieldBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("withdraw_amount_input")
            )

            // Show Available Balance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Available Balance:", color = TextMuted, fontSize = 12.sp)
                Text(
                    text = "Rs. %,.2f".format(user?.balance ?: 0.0), 
                    color = PrimaryEmerald, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Bold
                )
            }

            // Action Buttons
            Button(
                onClick = { viewModel.submitWithdraw(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("submit_withdraw_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = Color.Black),
                shape = RoundedCornerShape(23.dp)
            ) {
                Text(text = "Confirm Withdraw", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.showWithdrawModal.value = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, FieldBorder, shape = RoundedCornerShape(23.dp))
                    .testTag("cancel_withdraw_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextLight),
                shape = RoundedCornerShape(23.dp)
            ) {
                Text(text = "Cancel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ---------------- TEAM SCREEN ----------------

@Composable
fun TeamScreen(viewModel: AppViewModel) {
    val user by viewModel.loggedInUser.collectAsState()
    val teamMembers by viewModel.referredTeam.collectAsState()
    val context = LocalContext.current

    val referralCode = user?.username ?: ""
    val referralLink = "https://tradingpro.com/join?ref=$referralCode"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = "Referral Program",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "My Referral Team",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextLight
                )
                Text(
                    text = "Invite friends to register, earn Rs. 300 bonus for signup and 13% commission on plan purchases!",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Stats boxes row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBox(
                    label = "Total Referrals",
                    value = "${teamMembers.size} Friends",
                    icon = Icons.Default.People,
                    iconColor = PrimaryEmerald,
                    modifier = Modifier.weight(1f)
                )

                StatBox(
                    label = "Referral Earnings",
                    value = "Rs. %,.0f".format(user?.referralEarnings ?: 0.0),
                    icon = Icons.Default.AccountBalanceWallet,
                    iconColor = PrimaryEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Link Share Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, FieldBorder.copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Share Your Invite Link",
                        fontSize = 15.sp,
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Aapke friends is link se register karenge tu unko Rs. 1500 aur aapko Rs. 300 signup bonus + 13% plan buy commission milega!",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Link row with Copy Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark, shape = RoundedCornerShape(12.dp))
                            .border(1.dp, FieldBorder, shape = RoundedCornerShape(12.dp))
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = referralLink,
                            color = PrimaryEmerald,
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Trading Pro Referral Link", referralLink)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Referral link copied successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // WhatsApp Direct Share Button
                    Button(
                        onClick = {
                            val msg = "*🔥 Trading Pro Earning App!*\n\n" +
                                    "Daily online profit earn karein. Join karte hi *Rs. 1,500 Free Welcome Bonus* payein!\n\n" +
                                    "👉 *Referral Link:* $referralLink\n" +
                                    "👉 *Referral Code:* $referralCode\n\n" +
                                    "Invite friends and get Rs. 300 instant signup bonus + *13% lifetime plan commission*!"
                            val encoded = URLEncoder.encode(msg, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp not available!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Demo test referral trigger
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Demo Mode Tester", color = PrimaryEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Simulate a friend joining using your code to test your Rs. 300 bonus instantly!", color = TextMuted, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { viewModel.simulateReferralJoin(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text("Test Signup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Team list heading
        item {
            Text(
                text = "My Team Members (${teamMembers.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Empty state or Team list items
        if (teamMembers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "No team members",
                            tint = FieldBorder,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Team Members Yet",
                            color = TextLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Friends ko invite karein aur har active registration par Rs. 300 reward hasal karein!",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(teamMembers) { member ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, FieldBorder.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Icon",
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = member.username,
                                    color = TextLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Referred Member",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(PrimaryEmerald.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Bonus Credited ✔",
                                color = PrimaryEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppGroupCard(context: Context, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF25D366).copy(alpha = 0.5f), Color.Transparent)
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF25D366).copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "WhatsApp Group Icon",
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Official WhatsApp Group",
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Latest updates, support, aur daily offers ke liye hamara official group join karein!",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Button(
                onClick = {
                    val url = "https://chat.whatsapp.com/FdQuJrR6Ed7Fpc8TqAal56"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "WhatsApp link open nahi ho saka: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("whatsapp_group_button")
            ) {
                Text("Join", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

