package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import android.provider.Settings
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    BackHandler(onBack = onBackClick)
    
    // States from DataStore
    val appTitle by viewModel.settings.appTitle.collectAsState(initial = "MFS Automator")
    val webhookUrl by viewModel.settings.webhookUrl.collectAsState(initial = "")
    val apiToken by viewModel.settings.apiToken.collectAsState(initial = "")
    val themeMode by viewModel.settings.themeMode.collectAsState(initial = 2)
    val appLockEnabled by viewModel.settings.appLockEnabled.collectAsState(initial = false)
    val appPin by viewModel.settings.appPin.collectAsState(initial = "")
    
    val bkashEnabled by viewModel.settings.bkashEnabled.collectAsState(initial = true)
    val nagadEnabled by viewModel.settings.nagadEnabled.collectAsState(initial = true)
    val rocketEnabled by viewModel.settings.rocketEnabled.collectAsState(initial = true)
    val upayEnabled by viewModel.settings.upayEnabled.collectAsState(initial = true)
    
    val deviceId by viewModel.settings.deviceId.collectAsState(initial = "")
    val minAmount by viewModel.settings.minAmount.collectAsState(initial = "0")
    val customHeader by viewModel.settings.customHeader.collectAsState(initial = "")

    var showClearDialog by remember { mutableStateOf(false) }
    
    var isApiSectionEditable by remember { mutableStateOf(false) }
    var tempAppTitle by remember(appTitle) { mutableStateOf(appTitle) }
    var tempWebhookUrl by remember(webhookUrl) { mutableStateOf(webhookUrl) }
    var tempApiToken by remember(apiToken) { mutableStateOf(apiToken) }
    var tempDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var tempCustomHeader by remember(customHeader) { mutableStateOf(customHeader) }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var inputPin by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(if (appPin.isEmpty()) "Set App PIN" else "Change App PIN") },
            text = {
                OutlinedTextField(
                    value = inputPin,
                    onValueChange = { if (it.length <= 6) inputPin = it }, // max 6 digits
                    label = { Text("Enter 4-6 digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputPin.isEmpty()) {
                        // User wants to remove the PIN
                        coroutineScope.launch { 
                            viewModel.settings.saveAppPin("") 
                            viewModel.settings.saveAppLock(false) // Must disable biometric if no PIN
                        }
                        showPinSetupDialog = false
                    } else if (inputPin.length >= 4) {
                        coroutineScope.launch { viewModel.settings.saveAppPin(inputPin) }
                        showPinSetupDialog = false
                    } else {
                        Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will permanently delete all transaction history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: General
            item {
                SettingsSectionTitle("GENERAL")
                SettingsCard {
                    OutlinedTextField(
                        value = tempAppTitle,
                        onValueChange = { tempAppTitle = it; coroutineScope.launch { viewModel.settings.saveAppTitle(it) } },
                        label = { Text("App Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
            
            // Section: Webhook
            item {
                SettingsSectionTitle("WEBHOOK / API")
                SettingsCard {
                    OutlinedTextField(
                        value = if (isApiSectionEditable) tempWebhookUrl else webhookUrl,
                        onValueChange = { tempWebhookUrl = it },
                        label = { Text("Webhook URL") },
                        readOnly = !isApiSectionEditable,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = if (isApiSectionEditable) tempApiToken else apiToken,
                        onValueChange = { tempApiToken = it },
                        label = { Text("API Secret / Auth Token") },
                        readOnly = !isApiSectionEditable,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = if (isApiSectionEditable) tempDeviceId else deviceId,
                        onValueChange = { tempDeviceId = it },
                        label = { Text("Device ID / Shop Name") },
                        readOnly = !isApiSectionEditable,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = if (isApiSectionEditable) tempCustomHeader else customHeader,
                        onValueChange = { tempCustomHeader = it },
                        label = { Text("Custom Header (Key:Value)") },
                        readOnly = !isApiSectionEditable,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (!isApiSectionEditable) {
                        Button(
                            onClick = { isApiSectionEditable = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Edit")
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.settings.saveWebhookUrl(tempWebhookUrl)
                                    viewModel.settings.saveApiToken(tempApiToken)
                                    viewModel.settings.saveDeviceId(tempDeviceId)
                                    viewModel.settings.saveCustomHeader(tempCustomHeader)
                                    isApiSectionEditable = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save changes")
                        }
                    }
                }
            }

            // Section: Preferences
            item {
                SettingsSectionTitle("PREFERENCES")
                SettingsCard {
                    Text("Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeButton(text = "System", selected = themeMode == 0) { coroutineScope.launch { viewModel.settings.saveThemeMode(0) } }
                        ThemeButton(text = "Light", selected = themeMode == 1) { coroutineScope.launch { viewModel.settings.saveThemeMode(1) } }
                        ThemeButton(text = "Dark", selected = themeMode == 2) { coroutineScope.launch { viewModel.settings.saveThemeMode(2) } }
                    }
                }
            }

            // Section: MFS Filters
            item {
                SettingsSectionTitle("MFS LISTENERS")
                SettingsCard {
                    ProviderToggle("bKash", bkashEnabled) { coroutineScope.launch { viewModel.settings.toggleProvider("bkash", it) } }
                    ProviderToggle("Nagad", nagadEnabled) { coroutineScope.launch { viewModel.settings.toggleProvider("nagad", it) } }
                    ProviderToggle("Rocket", rocketEnabled) { coroutineScope.launch { viewModel.settings.toggleProvider("rocket", it) } }
                    ProviderToggle("Upay", upayEnabled) { coroutineScope.launch { viewModel.settings.toggleProvider("upay", it) } }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = minAmount,
                        onValueChange = { coroutineScope.launch { viewModel.settings.saveMinAmount(it) } },
                        label = { Text("Minimum Amount Filter (৳)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
            
            // Section: Security
            item {
                SettingsSectionTitle("SECURITY & SYSTEM")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Lock (PIN)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            if (appPin.isNotEmpty()) {
                                Text(
                                    text = "Change PIN",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            inputPin = ""
                                            showPinSetupDialog = true
                                        }
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                        Switch(
                            checked = appPin.isNotEmpty(),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    inputPin = ""
                                    showPinSetupDialog = true
                                } else {
                                    coroutineScope.launch {
                                        viewModel.settings.saveAppPin("")
                                        viewModel.settings.saveAppLock(false) // Must disable biometric if no PIN
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("App Lock (Biometric)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { 
                                if (appPin.isEmpty()) {
                                    Toast.makeText(context, "Please set a PIN first", Toast.LENGTH_SHORT).show()
                                } else {
                                    coroutineScope.launch { viewModel.settings.saveAppLock(it) } 
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent()
                            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Battery Optimization is already disabled (Unrestricted)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            com.example.service.MfsForegroundService.startService(context)
                            Toast.makeText(context, "MFS Background Service Active (24/7)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🟢 Restart 24/7 Background Service")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: For Xiaomi (MIUI/HyperOS), Vivo & Oppo, enable 'Auto-start' and set Battery Saver to 'No restrictions' in App Info so the system never stops the SMS listener.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section: Data
            item {
                SettingsSectionTitle("DATA & STORAGE")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Scanning inbox for recent MFS SMS...", Toast.LENGTH_SHORT).show()
                                viewModel.scanInbox(hoursBack = 72) { count ->
                                    Toast.makeText(
                                        context,
                                        if (count > 0) "Found and imported $count new transaction(s)!"
                                        else "Scan complete. All MFS transactions are up to date.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Scan SMS Inbox", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                            Text("Import any missed bKash, Nagad, Rocket SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Clear All Transaction History", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                coroutineScope.launch {
                                    val transactions = viewModel.transactions.value
                                    if (transactions.isNotEmpty()) {
                                        try {
                                            val file = File(context.cacheDir, "transactions.csv")
                                            val writer = FileWriter(file)
                                            writer.append("ID,MFS,Amount,Sender,TrxID,Date,Synced\n")
                                            for (t in transactions) {
                                                writer.append("${t.id},${t.mfsName},${t.amount},${t.senderNumber},${t.trxId},${t.timestamp},${t.isSynced}\n")
                                            }
                                            writer.flush()
                                            writer.close()
                                            
                                            // Provide file via FileProvider (requires manifest setup, simpler to use direct intent if cache is readable, but ACTION_SEND needs uri)
                                            // To keep it simple without adding FileProvider xml, let's write to external storage or just generate the CSV string and let user share it as text
                                            
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/csv"
                                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Export CSV"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Export History to CSV", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            
            // Section: Footer
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val packageInfo = try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) {
                        null
                    }
                    val versionName = packageInfo?.versionName ?: "1.0.0"
                    
                    Text(
                        text = "Version: $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "© 2026 Shamim : All Rights Reserved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.dp.value.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun RowScope.ThemeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text)
    }
}

@Composable
fun ProviderToggle(name: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
