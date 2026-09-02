package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PaymentTransaction
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.MainViewModel
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.SyncWorker
import java.util.concurrent.TimeUnit
import androidx.fragment.app.FragmentActivity

import com.example.update.UpdateManager
import com.example.update.UpdateInfo

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission responses if needed
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = android.content.Intent()
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkAndRequestPermissions()

        // Schedule background auto-retry for unsynced transactions (every 15 mins)
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("SyncWorker", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.settings.themeMode.collectAsState(initial = 2) // Default dark mode
            
            val isDarkTheme = when (themeMode) {
                0 -> isSystemInDarkTheme()
                1 -> false
                2 -> true
                else -> true
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val transactions by viewModel.transactions.collectAsState()
                var currentScreen by remember { mutableStateOf("dashboard") }
                val appLockEnabled by viewModel.settings.appLockEnabled.collectAsState(initial = false)
                val appPin by viewModel.settings.appPin.collectAsState(initial = "")
                val appTitle by viewModel.settings.appTitle.collectAsState(initial = "MFS Automator")
                val webhookUrl by viewModel.settings.webhookUrl.collectAsState(initial = "")
                val isWebhookLive = webhookUrl.isNotBlank() && webhookUrl != "https://yourdomain.com/api/payment-callback"
                var isAuthenticated by remember { mutableStateOf(false) }
                
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                val updateManager = remember { UpdateManager(this@MainActivity) }
                
                LaunchedEffect(Unit) {
                    updateInfo = updateManager.checkForUpdate()
                }

                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = { Text("Update Available") },
                        text = { Text("A new version (${updateInfo!!.version}) is available. Do you want to update now?") },
                        confirmButton = {
                            Button(onClick = { 
                                updateManager.downloadAndInstall(updateInfo!!)
                                updateInfo = null 
                            }) { Text("Update") }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) { Text("Later") }
                        }
                    )
                }

                // Authentication Dialog Trigger
                LaunchedEffect(appPin, appLockEnabled, isAuthenticated) {
                    if (appPin.isNotEmpty() && !isAuthenticated && appLockEnabled) {
                        val biometricManager = androidx.biometric.BiometricManager.from(this@MainActivity)
                        if (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle("App Locked")
                                .setSubtitle("Authenticate to access MFS Automator")
                                .setNegativeButtonText("Use PIN")
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                .build()

                            val biometricPrompt = androidx.biometric.BiometricPrompt(
                                this@MainActivity,
                                ContextCompat.getMainExecutor(this@MainActivity),
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                        isAuthenticated = true
                                    }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        // Do nothing, let them use PIN
                                    }
                                }
                            )
                            biometricPrompt.authenticate(promptInfo)
                        }
                    }
                }

                if (appPin.isNotEmpty() && !isAuthenticated) {
                    // Lock Screen UI with PIN Entry
                    var enteredPin by remember { mutableStateOf("") }
                    var isError by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Enter App PIN", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Dots representation
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(appPin.length) { index ->
                                    val isFilled = index < enteredPin.length
                                    Box(modifier = Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isError) {
                                Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(" ", style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Keypad
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                val keys = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf("Exit", "0", "Delete")
                                )
                                keys.forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        row.forEach { key ->
                                            Box(
                                                modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        if (key == "Exit") {
                                                            finish()
                                                        } else if (key == "Delete") {
                                                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                            isError = false
                                                        } else {
                                                            if (enteredPin.length < appPin.length) {
                                                                enteredPin += key
                                                                if (enteredPin.length == appPin.length) {
                                                                    if (enteredPin == appPin) {
                                                                        isAuthenticated = true
                                                                    } else {
                                                                        isError = true
                                                                        enteredPin = ""
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (key == "Delete") {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else if (key == "Exit") {
                                                    Icon(Icons.Default.Close, contentDescription = "Exit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else {
                                                    Text(key, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    if (currentScreen == "settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBackClick = { currentScreen = "dashboard" },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        DashboardScreen(
                            transactions = transactions,
                            appTitle = appTitle,
                            isWebhookLive = isWebhookLive,
                            onSettingsClick = { currentScreen = "settings" },
                            onRetryClick = { transaction -> viewModel.retrySync(transaction) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkBatteryOptimization()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    transactions: List<PaymentTransaction>, 
    appTitle: String,
    isWebhookLive: Boolean,
    onSettingsClick: () -> Unit, 
    onRetryClick: (PaymentTransaction) -> Unit, 
    modifier: Modifier = Modifier
) {
    val totalAmount = transactions.sumOf { it.amount.replace(",", "").toDoubleOrNull() ?: 0.0 }
    val syncedCount = transactions.count { it.isSynced }
    var currentTab by remember { mutableStateOf("dashboard") }

    var timeFilter by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf("All") }

    val filteredHistory = remember(transactions, timeFilter, statusFilter) {
        transactions.filter {
            val timeMatch = when (timeFilter) {
                "Daily" -> it.timestamp >= (System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                "Weekly" -> it.timestamp >= (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L)
                "Monthly" -> it.timestamp >= (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
                else -> true
            }
            val statusMatch = when (statusFilter) {
                "Succeed" -> it.isSynced
                "Processing" -> !it.isSynced && it.errorLog == null
                "Failed" -> !it.isSynced && it.errorLog != null
                else -> true
            }
            timeMatch && statusMatch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = appTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(com.example.ui.theme.SuccessGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LISTENER ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.example.ui.theme.SuccessGreen,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (currentTab == "dashboard") {
            // Summary Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TODAY'S TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("৳ %,.2f", totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYNC STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$syncedCount Success",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        } // Close if (currentTab == "dashboard")

        if (currentTab == "dashboard") {
            TransactionChartSection(transactions = transactions)
        }

        // List Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentTab == "dashboard") "RECENT TRANSACTIONS" else "ALL TRANSACTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isWebhookLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .border(
                        1.dp,
                        if (isWebhookLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Webhook Live",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isWebhookLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    textDecoration = if (isWebhookLive) TextDecoration.None else TextDecoration.LineThrough
                )
            }
        }

        if (currentTab == "history") {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                var timeExpanded by remember { mutableStateOf(false) }
                var statusExpanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = timeExpanded,
                    onExpandedChange = { timeExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = timeFilter,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Time") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = timeExpanded,
                        onDismissRequest = { timeExpanded = false }
                    ) {
                        listOf("All", "Daily", "Weekly", "Monthly").forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter) },
                                onClick = {
                                    timeFilter = filter
                                    timeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = statusFilter,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        listOf("All", "Succeed", "Processing", "Failed").forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter) },
                                onClick = {
                                    statusFilter = filter
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        val displayTransactions = if (currentTab == "dashboard") transactions.take(5) else filteredHistory
        
        if (displayTransactions.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (transactions.isEmpty()) "No payments received yet." else "No transactions match your filter.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(displayTransactions, key = { it.id }) { transaction ->
                    TransactionItem(transaction = transaction, onRetryClick = onRetryClick)
                }
            }
        }
        
        // Bottom Nav
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { currentTab = "dashboard" }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (currentTab == "dashboard") MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Dashboard",
                        tint = if (currentTab == "dashboard") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (currentTab == "dashboard") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { currentTab = "history" }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (currentTab == "history") MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "History",
                        tint = if (currentTab == "history") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (currentTab == "history") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: PaymentTransaction, onRetryClick: ((PaymentTransaction) -> Unit)? = null) {
    val mfsColor = when (transaction.mfsName) {
        "bKash" -> com.example.ui.theme.MfsBkash
        "Nagad" -> com.example.ui.theme.MfsNagad
        "Rocket" -> com.example.ui.theme.MfsRocket
        "Upay" -> com.example.ui.theme.MfsUpay
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${transaction.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // MFS Logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(mfsColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = transaction.mfsName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "৳ ${transaction.amount}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TrxID: ${transaction.trxId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Sender: ${transaction.senderNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = if (transaction.isSynced) com.example.ui.theme.SuccessGreen else com.example.ui.theme.WarningOrange
                        val statusText = if (transaction.isSynced) "Synced to API" else "API Retrying..."
                        
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(statusColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (transaction.isSynced) androidx.compose.material.icons.Icons.Default.CheckCircle else androidx.compose.material.icons.Icons.Default.Warning,
                                contentDescription = statusText,
                                tint = statusColor,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!transaction.isSynced && onRetryClick != null) {
                        TextButton(
                            onClick = { onRetryClick(transaction) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Sync Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                if (!transaction.isSynced && !transaction.errorLog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: ${transaction.errorLog}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionChartSection(transactions: List<PaymentTransaction>) {
    // Only show chart if we have at least 2 transactions to make a meaningful chart
    if (transactions.size < 2) return

    val recentTransactions = transactions.take(10).reversed()
    val amounts = recentTransactions.map { it.amount.replace(",", "").toFloatOrNull() ?: 0f }.toTypedArray()
    
    val chartEntryModel = entryModelOf(*amounts)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "RECENT AMOUNTS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Chart(
                chart = columnChart(),
                model = chartEntryModel,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        }
    }
}
