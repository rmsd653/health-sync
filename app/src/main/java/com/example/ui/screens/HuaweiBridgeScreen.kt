package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bridge.BridgeSyncStepResult
import com.example.bridge.HuaweiHealthBridgeManager
import com.example.ui.HealthViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HuaweiBridgeScreen(
    viewModel: HealthViewModel,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val envStatus by viewModel.bridgeEnvStatus.collectAsStateWithLifecycle()
    val huaweiAccount by viewModel.huaweiAccountState.collectAsStateWithLifecycle()
    val syncSteps by viewModel.bridgeSyncSteps.collectAsStateWithLifecycle()
    val verificationSummary by viewModel.lastVerificationSummary.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedHistoricalRange.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var showHuaweiAuthDialog by remember { mutableStateOf(false) }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        viewModel.onHealthConnectPermissionsResult(context, granted)
        onShowSnackbar(
            "Health Connect permissions updated: ${granted.size}/${HuaweiHealthBridgeManager.REQUIRED_PERMISSIONS.size} granted"
        )
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBridgeEnvironment(context)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("huawei_bridge_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: System Environment & Package Detection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Device compatibility check",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Live status of Health Connect & Huawei apps on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.refreshBridgeEnvironment(context)
                                    onShowSnackbar("Re-checked device packages and permissions")
                                },
                                modifier = Modifier.testTag("rescan_environment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check")
                            }
                        }

                        EnvironmentStatusRow(
                            title = "Google Health Connect SDK",
                            subtitle = if (envStatus.isHealthConnectAvailable) {
                                "Installed and available on Android OS"
                            } else {
                                "Not installed or unavailable on this device"
                            },
                            isReady = envStatus.isHealthConnectAvailable
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        EnvironmentStatusRow(
                            title = "Huawei Health app (com.huawei.health)",
                            subtitle = if (envStatus.isHuaweiHealthInstalled) {
                                "Installed on device"
                            } else {
                                "Not installed (Huawei Cloud OAuth 2.0 still supported)"
                            },
                            isReady = envStatus.isHuaweiHealthInstalled
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        EnvironmentStatusRow(
                            title = "Unrestricted background sync",
                            subtitle = if (envStatus.isBatteryOptimizationIgnored) {
                                "Battery optimization unrestricted"
                            } else {
                                "Standard battery mode (tap below to allow background sync)"
                            },
                            isReady = envStatus.isBatteryOptimizationIgnored
                        )

                        if (!envStatus.isBatteryOptimizationIgnored) {
                            OutlinedButton(
                                onClick = {
                                    HuaweiHealthBridgeManager.requestBatteryOptimizationExemption(context)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Configure battery optimization")
                            }
                        }
                    }
                }
            }

            // Section 2: Step 1 — Google Health Connect OS Permissions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Step 1 • Google Health Connect permissions",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${envStatus.grantedPermissionsCount} of ${envStatus.totalPermissionsCount} read/write permissions granted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Grant read and write access for Steps, Heart Rate, Sleep, and Active Calories so HealthSync can read your live Health Connect records and write Huawei Health data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        healthConnectPermissionLauncher.launch(
                                            HuaweiHealthBridgeManager.REQUIRED_PERMISSIONS
                                        )
                                    } catch (_: Exception) {
                                        HuaweiHealthBridgeManager.openHealthConnectSettings(context)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_hc_permissions_button")
                            ) {
                                Text(
                                    if (envStatus.allHealthConnectPermissionsGranted) {
                                        "Review permissions"
                                    } else {
                                        "Grant permissions"
                                    }
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    HuaweiHealthBridgeManager.openHealthConnectSettings(context)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("open_hc_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Health Connect")
                            }
                        }
                    }
                }
            }

            // Section 3: Step 2 — Huawei ID OAuth 2.0 & Bearer Token Setup
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (huaweiAccount.isLinked) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (huaweiAccount.isLinked) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Step 2 • Huawei Health Kit Cloud OAuth 2.0",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (huaweiAccount.isLinked) {
                                        "Connected: ${huaweiAccount.accountIdentifier} (${huaweiAccount.authMode})"
                                    } else {
                                        "Not connected — link Huawei ID or enter Bearer access token"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Ensure Health Kit Cloud Sync is enabled in Huawei Health (Me ➔ Privacy Management ➔ Data Syncing with Cloud), then connect your Huawei OAuth 2.0 account or Bearer token.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showHuaweiAuthDialog = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("configure_huawei_account_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (huaweiAccount.isLinked) "Update Huawei token" else "Connect Huawei Health")
                            }

                            if (huaweiAccount.isLinked) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disconnectHuaweiAccount(context)
                                        onShowSnackbar("Huawei Health account disconnected")
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("disconnect_huawei_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disconnect")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val opened = HuaweiHealthBridgeManager.openHuaweiOAuthInBrowser(context)
                                    if (!opened) {
                                        showHuaweiAuthDialog = true
                                        onShowSnackbar("Enter your Huawei AppGallery Client ID or Bearer token")
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("launch_huawei_oauth_browser_button")
                            ) {
                                Text("Huawei OAuth browser")
                            }

                            OutlinedButton(
                                onClick = {
                                    HuaweiHealthBridgeManager.openHuaweiHealthApp(context)
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open Huawei Health")
                            }
                        }
                    }
                }
            }

            // Section 4: Step 3 — Live Sync Pipeline & Verification
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Step 3 • Sync & Health Connect verification",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        syncSteps.forEach { step ->
                            PipelineStepItem(step = step)
                        }

                        verificationSummary?.let { summary ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = summary.statusMessage,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Health Connect Readback: ${summary.verifiedSteps} steps • ${summary.verifiedHeartRateAvg} bpm • ${summary.verifiedSleepMinutes}m sleep • ${summary.verifiedActiveCaloriesKcal} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.runHuaweiToHealthConnectBridgeSync(context) { msg ->
                                    onShowSnackbar(msg)
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("execute_bridge_pipeline_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Running sync...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Huawei Health ➔ Health Connect")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "Historical backfill (requires Huawei Cloud Bearer token)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val ranges = listOf("Yesterday", "Past 7 Days", "Past 14 Days", "Past 30 Days")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ranges.forEach { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = {
                                        viewModel.runHistoricalSync(context, range) { msg ->
                                            onShowSnackbar(msg)
                                        }
                                    },
                                    label = { Text(range) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHuaweiAuthDialog) {
        HuaweiOAuthConfigDialog(
            initialIdentifier = huaweiAccount.accountIdentifier,
            initialDevice = huaweiAccount.deviceModel,
            initialToken = huaweiAccount.accessToken ?: "",
            onDismiss = { showHuaweiAuthDialog = false },
            onLaunchBrowserOAuth = { customClientId ->
                val launched = HuaweiHealthBridgeManager.openHuaweiOAuthInBrowser(context, customClientId)
                if (!launched) {
                    onShowSnackbar("Enter a valid Huawei AppGallery Client ID first")
                }
            },
            onSaveAccount = { email, device, token ->
                viewModel.linkHuaweiAccount(
                    context = context,
                    accountIdentifier = email,
                    authMode = if (token.isNotBlank()) "Huawei Cloud OAuth Bearer Token" else "Huawei ID Linked",
                    deviceModel = device,
                    accessToken = token
                )
                showHuaweiAuthDialog = false
                onShowSnackbar("Saved Huawei Health connection settings")
            }
        )
    }
}

@Composable
private fun EnvironmentStatusRow(
    title: String,
    subtitle: String,
    isReady: Boolean
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
            )
        }
    )
}

@Composable
private fun PipelineStepItem(step: BridgeSyncStepResult) {
    val icon = when (step.status) {
        "SUCCESS" -> Icons.Outlined.CheckCircle
        "WARN", "ERROR" -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Pending
    }
    val tint = when (step.status) {
        "SUCCESS" -> MaterialTheme.colorScheme.secondary
        "RUNNING" -> MaterialTheme.colorScheme.primary
        "WARN", "ERROR" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = step.status,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${step.stepNumber}. ${step.title}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HuaweiOAuthConfigDialog(
    initialIdentifier: String,
    initialDevice: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onLaunchBrowserOAuth: (String) -> Unit,
    onSaveAccount: (String, String, String) -> Unit
) {
    var accountEmail by remember { mutableStateOf(initialIdentifier) }
    var deviceName by remember { mutableStateOf(initialDevice) }
    var customClientId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf(initialToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect Huawei Health Kit",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Provide your Huawei OAuth 2.0 Bearer access token to pull real steps, heart rate, sleep, and calories from Huawei Health Cloud API, or launch Huawei OAuth in your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = accountEmail,
                    onValueChange = { accountEmail = it },
                    label = { Text("Huawei ID email or label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Huawei wearable model (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Huawei OAuth 2.0 Bearer Token") },
                    placeholder = { Text("Paste Huawei Health Kit access_token") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("huawei_token_input")
                )
                OutlinedTextField(
                    value = customClientId,
                    onValueChange = { customClientId = it },
                    label = { Text("AppGallery OAuth Client ID (for browser sign-in)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { onLaunchBrowserOAuth(customClientId) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in via Huawei OAuth Browser")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAccount(accountEmail, deviceName, accessToken) },
                modifier = Modifier.testTag("save_huawei_account_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
