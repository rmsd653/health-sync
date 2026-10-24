package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ConnectionEntity
import com.example.data.HealthMetricEntity
import com.example.ui.HealthViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: HealthViewModel,
    todayMetric: HealthMetricEntity?,
    connections: List<ConnectionEntity>,
    isSyncing: Boolean,
    onViewDetailsClick: () -> Unit,
    onOpenBridgeClick: () -> Unit = {},
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val huaweiAccount by viewModel.huaweiAccountState.collectAsStateWithLifecycle()
    val bridgeEnv by viewModel.bridgeEnvStatus.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshBridgeEnvironment(context)
    }

    val hasRealMetrics = todayMetric != null && (
        todayMetric.steps > 0 ||
            todayMetric.heartRateBpm > 0 ||
            todayMetric.sleepMinutes > 0 ||
            todayMetric.activeCalories > 0
        )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("dashboard_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Google Health Connect Connection & Status Hero Card
            item {
                GoogleHealthConnectHeroCard(
                    isHuaweiLinked = huaweiAccount.isLinked,
                    huaweiAccountLabel = huaweiAccount.accountIdentifier,
                    isHealthConnectAvailable = bridgeEnv.isHealthConnectAvailable,
                    grantedPermissionsCount = bridgeEnv.grantedPermissionsCount,
                    totalPermissionsCount = bridgeEnv.totalPermissionsCount,
                    lastSyncText = userSettings?.lastSyncTimestampText ?: "Never synced",
                    isSyncing = isSyncing,
                    onSetupClick = onOpenBridgeClick,
                    onSyncNowClick = {
                        viewModel.runHuaweiToHealthConnectBridgeSync(context) { msg ->
                            onShowSnackbar(msg)
                        }
                    }
                )
            }

            // 2. Today's Real Health Connect Data OR Clean "Not Synced" Empty State
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Health Connect data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            viewModel.refreshBridgeEnvironment(context)
                            onShowSnackbar("Refreshed data from Google Health Connect")
                        },
                        modifier = Modifier.testTag("refresh_health_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
            }

            if (!hasRealMetrics) {
                item {
                    NotSyncedEmptyStateCard(
                        isHuaweiLinked = huaweiAccount.isLinked,
                        hasHealthConnectPermissions = bridgeEnv.hasAnyReadPermission,
                        isSyncing = isSyncing,
                        onOpenSetupClick = onOpenBridgeClick,
                        onSyncNowClick = {
                            viewModel.runHuaweiToHealthConnectBridgeSync(context) { msg ->
                                onShowSnackbar(msg)
                            }
                        }
                    )
                }
            } else {
                item {
                    RealHealthMetricsGrid(
                        metric = todayMetric!!,
                        onOpenLogsClick = onViewDetailsClick
                    )
                }
            }

            // 3. Data Categories & Permissions List (Google Health Connect M3 ListItem style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Data categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose which Huawei Health data types write to Health Connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onViewDetailsClick,
                        modifier = Modifier.testTag("view_sync_logs_button")
                    ) {
                        Text("Recent access")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        connections.forEachIndexed { index, category ->
                            GoogleDataCategoryListItem(
                                category = category,
                                onToggle = { enabled ->
                                    viewModel.toggleConnection(category.id, enabled)
                                },
                                onManualSyncClick = {
                                    viewModel.syncSingleCategory(context, category) { msg ->
                                        onShowSnackbar(msg)
                                    }
                                }
                            )
                            if (index < connections.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Google Health Connect Privacy Footer Note
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Data synced from Huawei Health is stored directly in Android's on-device Health Connect datastore. No sample or simulated numbers are generated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleHealthConnectHeroCard(
    isHuaweiLinked: Boolean,
    huaweiAccountLabel: String,
    isHealthConnectAvailable: Boolean,
    grantedPermissionsCount: Int,
    totalPermissionsCount: Int,
    lastSyncText: String,
    isSyncing: Boolean,
    onSetupClick: () -> Unit,
    onSyncNowClick: () -> Unit
) {
    val allPermissionsGranted = grantedPermissionsCount >= totalPermissionsCount && totalPermissionsCount > 0
    val isReady = isHuaweiLinked && allPermissionsGranted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_bridge_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isReady) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isReady) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Outlined.HealthAndSafety
                                },
                                contentDescription = null,
                                tint = if (isReady) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (isReady) {
                                "Huawei Health ➔ Health Connect"
                            } else {
                                "Finish setting up Health Sync"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isReady) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = "Last sync: $lastSyncText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isReady) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            }
                        )
                    }
                }
            }

            // Status Checklist Chips inside Hero
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SetupRequirementRow(
                        label = "Google Health Connect SDK",
                        status = if (isHealthConnectAvailable) "Available" else "Not installed",
                        isComplete = isHealthConnectAvailable
                    )
                    SetupRequirementRow(
                        label = "Health Connect permissions",
                        status = "$grantedPermissionsCount of $totalPermissionsCount granted",
                        isComplete = allPermissionsGranted
                    )
                    SetupRequirementRow(
                        label = "Huawei Health account",
                        status = if (isHuaweiLinked) {
                            huaweiAccountLabel.ifBlank { "Connected" }
                        } else {
                            "Not connected"
                        },
                        isComplete = isHuaweiLinked
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncNowClick,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sync_now_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync now")
                    }
                }

                FilledTonalButton(
                    onClick = onSetupClick,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_bridge_setup_button")
                ) {
                    Text(if (isReady) "Manage" else "Set up")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRequirementRow(
    label: String,
    status: String,
    isComplete: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge,
            color = if (isComplete) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun NotSyncedEmptyStateCard(
    isHuaweiLinked: Boolean,
    hasHealthConnectPermissions: Boolean,
    isSyncing: Boolean,
    onOpenSetupClick: () -> Unit,
    onSyncNowClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("not_synced_empty_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (!hasHealthConnectPermissions) {
                            Icons.Outlined.LockOpen
                        } else {
                            Icons.Outlined.CloudSync
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = when {
                    !hasHealthConnectPermissions -> "Health Connect permissions needed"
                    !isHuaweiLinked -> "No health data synced for today"
                    else -> "No records recorded for today yet"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when {
                    !hasHealthConnectPermissions ->
                        "Grant Google Health Connect read and write permissions so HealthSync can read your existing Health Connect data and write Huawei Health records."
                    !isHuaweiLinked ->
                        "Connect your Huawei ID (OAuth 2.0 Bearer token) in Permissions & Setup to pull today's steps, heart rate, sleep, and calories from Huawei Cloud."
                    else ->
                        "Your account is connected, but 0 records were returned for today from Huawei Cloud or Google Health Connect."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Button(
                    onClick = onOpenSetupClick,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("empty_state_setup_button")
                ) {
                    Text(
                        when {
                            !hasHealthConnectPermissions -> "Grant permissions"
                            !isHuaweiLinked -> "Connect Huawei Health"
                            else -> "Check connection"
                        }
                    )
                }

                if (hasHealthConnectPermissions) {
                    FilledTonalButton(
                        onClick = onSyncNowClick,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Read Health Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun RealHealthMetricsGrid(
    metric: HealthMetricEntity,
    onOpenLogsClick: () -> Unit
) {
    val formattedSteps = if (metric.steps > 0) {
        NumberFormat.getNumberInstance(Locale.US).format(metric.steps)
    } else {
        "No data"
    }
    val formattedHr = if (metric.heartRateBpm > 0) "${metric.heartRateBpm} bpm" else "No data"
    val sleepHours = metric.sleepMinutes / 60
    val sleepMins = metric.sleepMinutes % 60
    val formattedSleep = if (metric.sleepMinutes > 0) "${sleepHours}h ${sleepMins}m" else "No data"
    val formattedCalories = if (metric.activeCalories > 0) "${metric.activeCalories} kcal" else "No data"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GoogleMetricCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_steps"),
                title = "Steps",
                value = formattedSteps,
                subtitle = metric.lastUpdatedText,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenLogsClick
            )
            GoogleMetricCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_hr"),
                title = "Heart rate",
                value = formattedHr,
                subtitle = "Daily average",
                icon = Icons.Default.Favorite,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenLogsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GoogleMetricCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_sleep"),
                title = "Sleep",
                value = formattedSleep,
                subtitle = "SleepSessionRecord",
                icon = Icons.Default.Bedtime,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onOpenLogsClick
            )
            GoogleMetricCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_calories"),
                title = "Active energy",
                value = formattedCalories,
                subtitle = "ActiveCaloriesBurned",
                icon = Icons.Default.LocalFireDepartment,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenLogsClick
            )
        }
    }
}

@Composable
private fun GoogleMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconContainerColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoogleDataCategoryListItem(
    category: ConnectionEntity,
    onToggle: (Boolean) -> Unit,
    onManualSyncClick: () -> Unit
) {
    val iconVector = when (category.iconType) {
        "STEPS" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "SLEEP" -> Icons.Default.Bedtime
        "HEART_RATE" -> Icons.Default.Favorite
        "WORKOUTS" -> Icons.AutoMirrored.Filled.DirectionsRun
        "CALORIES" -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.WaterDrop
    }

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (category.isConnected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = category.name,
                        tint = if (category.isConnected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = category.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Status: ${category.lastSyncTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onManualSyncClick,
                    modifier = Modifier.testTag("sync_category_${category.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync ${category.name}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = category.isConnected,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("toggle_${category.id}")
                )
            }
        }
    )
}
