package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    var showInAppWebViewDialog by remember { mutableStateOf(false) }
    var useExactUserCasUrlInWebView by remember { mutableStateOf(false) }
    var showHuaweiManualDialog by remember { mutableStateOf(false) }

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
                                "Not installed (Huawei Cloud OAuth 2.0 Web Sign-In supported)"
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

            // Section 3: Step 2 — Automatic Huawei ID In-App Sign-In (No manual token needed)
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
                                    text = "Step 2 • Huawei ID Sign-In (Automatic OAuth 2.0)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (huaweiAccount.isLinked) {
                                        "Connected: ${huaweiAccount.accountIdentifier} (${huaweiAccount.authMode})"
                                    } else {
                                        "Pre-configured with HealthSync Huawei Client ID (${HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_CLIENT_ID})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "You do not need to manually generate an OAuth token. Tap \"Sign in with Huawei ID\" below to open the official Huawei login page inside the app — HealthSync automatically intercepts the redirect callback and exchanges the PKCE code for your token.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Active Huawei OAuth 2.0 Configuration",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Client ID: ${huaweiAccount.clientId} • Redirect: ${huaweiAccount.redirectUri} • Scopes: ${huaweiAccount.authorizedScopes.size} HealthKit permissions (steps, heart rate, sleep, SpO2, calories, activity, etc.)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                useExactUserCasUrlInWebView = false
                                showInAppWebViewDialog = true
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("configure_huawei_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (huaweiAccount.isLinked) {
                                    "Re-authenticate with Huawei ID (In-App)"
                                } else {
                                    "Sign in with Huawei ID (In-App)"
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showHuaweiManualDialog = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("paste_huawei_url_or_token_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste URL / Token")
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

                        if (huaweiAccount.isLinked) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.disconnectHuaweiAccount(context)
                                    onShowSnackbar("Huawei Health account disconnected")
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("disconnect_huawei_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Disconnect Huawei Account")
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
                            text = "Historical backfill (uses connected Huawei OAuth session)",
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

    if (showInAppWebViewDialog) {
        HuaweiInAppWebLoginDialog(
            initialUseExactCasUrl = useExactUserCasUrlInWebView,
            clientId = huaweiAccount.clientId,
            redirectUri = huaweiAccount.redirectUri,
            onDismiss = { showInAppWebViewDialog = false },
            onInterceptedRedirect = { interceptedUrl ->
                showInAppWebViewDialog = false
                viewModel.handleInterceptedHuaweiRedirectUrl(
                    context = context,
                    rawInput = interceptedUrl
                ) { _, message ->
                    onShowSnackbar(message)
                }
            },
            onOpenExternalBrowser = { urlToOpen ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    onShowSnackbar("Could not open external browser")
                }
            }
        )
    }

    if (showHuaweiManualDialog) {
        HuaweiOAuthConfigDialog(
            initialIdentifier = huaweiAccount.accountIdentifier,
            initialDevice = huaweiAccount.deviceModel,
            initialToken = huaweiAccount.accessToken ?: "",
            onDismiss = { showHuaweiManualDialog = false },
            onOpenInAppWebView = { useExactCas ->
                showHuaweiManualDialog = false
                useExactUserCasUrlInWebView = useExactCas
                showInAppWebViewDialog = true
            },
            onProcessUrlOrToken = { email, device, rawUrlOrToken ->
                viewModel.handleInterceptedHuaweiRedirectUrl(
                    context = context,
                    rawInput = rawUrlOrToken,
                    accountIdentifier = email,
                    deviceModel = device
                ) { completed, message ->
                    onShowSnackbar(message)
                    if (completed) {
                        showHuaweiManualDialog = false
                    } else {
                        // If the user pasted the CAS login URL, open the In-App WebView immediately
                        showHuaweiManualDialog = false
                        useExactUserCasUrlInWebView = true
                        showInAppWebViewDialog = true
                    }
                }
            }
        )
    }
}

/**
 * Embedded In-App WebView dialog that loads the Huawei ID OAuth 2.0 login page
 * (`id5.cloud.huawei.com` / `oauth-login5.cloud.huawei.com` with `client_id=103198029`),
 * watches for the OAuth redirect (`https://server.healthsync.app/huawei?code=...`),
 * and automatically intercepts the authorization `code` so the user never has to manually
 * obtain or copy a Bearer token.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HuaweiInAppWebLoginDialog(
    initialUseExactCasUrl: Boolean,
    clientId: String,
    redirectUri: String,
    onDismiss: () -> Unit,
    onInterceptedRedirect: (String) -> Unit,
    onOpenExternalBrowser: (String) -> Unit
) {
    var useExactCasUrl by remember { mutableStateOf(initialUseExactCasUrl) }
    val targetUrl = remember(useExactCasUrl, clientId, redirectUri) {
        if (useExactCasUrl) {
            HuaweiHealthBridgeManager.USER_PROVIDED_CAS_URL
        } else {
            HuaweiHealthBridgeManager.buildFreshPkceHuaweiOAuthUrl(
                clientId = clientId,
                redirectUri = redirectUri
            )
        }
    }
    var currentUrl by remember { mutableStateOf(targetUrl) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Huawei ID Sign-In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onOpenExternalBrowser(targetUrl) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in external browser"
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Huawei Sign-In"
                            )
                        }
                    }
                }

                // Mode selector chips: Fresh PKCE S256 (recommended for auto token exchange) vs Exact Pasted CAS URL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !useExactCasUrl,
                        onClick = { useExactCasUrl = false },
                        label = { Text("Auto PKCE Token Flow (Recommended)") }
                    )
                    FilterChip(
                        selected = useExactCasUrl,
                        onClick = { useExactCasUrl = true },
                        label = { Text("Exact CAS Welcome URL") }
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Embedded Android WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("huawei_oauth_webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportMultipleWindows(false)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        if (HuaweiHealthBridgeManager.isOAuthRedirectCallback(url)) {
                                            onInterceptedRedirect(url)
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val nextUrl = request?.url?.toString() ?: return false
                                    currentUrl = nextUrl
                                    if (HuaweiHealthBridgeManager.isOAuthRedirectCallback(nextUrl)) {
                                        onInterceptedRedirect(nextUrl)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                }
                            }
                            loadUrl(targetUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != targetUrl && !currentUrl.contains("cloud.huawei.com")) {
                            webView.loadUrl(targetUrl)
                        }
                    }
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sign in with your Huawei ID above. As soon as Huawei redirects to server.healthsync.app/huawei, HealthSync automatically captures the code and obtains your OAuth token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
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
    onOpenInAppWebView: (Boolean) -> Unit,
    onProcessUrlOrToken: (String, String, String) -> Unit
) {
    var accountEmail by remember { mutableStateOf(initialIdentifier) }
    var deviceName by remember { mutableStateOf(initialDevice) }
    var urlOrTokenInput by remember {
        mutableStateOf(
            initialToken.ifBlank { HuaweiHealthBridgeManager.USER_PROVIDED_CAS_URL }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect Huawei Health",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Don't know how to obtain a Huawei OAuth 2.0 token? Just tap \"Sign in with Huawei ID (In-App)\" below, or paste your Huawei login/redirect URL and HealthSync will extract the code and exchange the token automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { onOpenInAppWebView(false) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Huawei ID (Auto PKCE)")
                }
                OutlinedButton(
                    onClick = { onOpenInAppWebView(true) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Your Exact Huawei CAS URL")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                OutlinedTextField(
                    value = accountEmail,
                    onValueChange = { accountEmail = it },
                    label = { Text("Huawei ID email or label (optional)") },
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
                    value = urlOrTokenInput,
                    onValueChange = { urlOrTokenInput = it },
                    label = { Text("Huawei OAuth URL, Redirect URL (?code=...), or Token") },
                    placeholder = { Text("Paste id5.cloud.huawei.com URL or redirect URL") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("huawei_token_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onProcessUrlOrToken(accountEmail, deviceName, urlOrTokenInput) },
                modifier = Modifier.testTag("save_huawei_account_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
