package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionEntity
import com.example.data.HealthMetricEntity
import com.example.ui.HealthViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.SparklineCanvas
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.TertiaryAmber

@Composable
fun DashboardScreen(
    viewModel: HealthViewModel,
    todayMetric: HealthMetricEntity?,
    connections: List<ConnectionEntity>,
    isSyncing: Boolean,
    aiResponse: String?,
    isAiLoading: Boolean,
    onViewDetailsClick: () -> Unit
) {
    var showEditMetricDialog by remember { mutableStateOf(false) }
    var showAiPromptDialog by remember { mutableStateOf(false) }
    var customAiPrompt by remember { mutableStateOf("") }

    val steps = todayMetric?.steps ?: 8432
    val heartRate = todayMetric?.heartRateBpm ?: 68
    val sleepMinutes = todayMetric?.sleepMinutes ?: 444
    val sleepHours = sleepMinutes / 60
    val sleepMinsRemaining = sleepMinutes % 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Dashboard Heading
        item {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Last Synced Status Card
        item {
            val infiniteTransition = rememberInfiniteTransition(label = "sync_rotate")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing)
                ),
                label = "angle"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_status_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Last Synced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Syncing",
                            tint = PrimaryBlue,
                            modifier = Modifier
                                .size(28.dp)
                                .then(if (isSyncing) Modifier.rotate(angle) else Modifier)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) "Syncing..." else (todayMetric?.lastUpdatedText ?: "Just now"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        )
                    }

                    Text(
                        text = if (isSyncing) "Fetching local encrypted sensor data..." else "All data is up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.triggerSync() },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("sync_now_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Now",
                                modifier = Modifier
                                    .size(20.dp)
                                    .then(if (isSyncing) Modifier.rotate(angle) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isSyncing) "Syncing..." else "Sync Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Connections Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Connections",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                connections.forEach { connection ->
                    ConnectionCard(
                        connection = connection,
                        onToggleConnect = { viewModel.toggleConnection(connection.id, !connection.isConnected) }
                    )
                }
            }
        }

        // Synced Today / Activity Summary Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Synced Today",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onViewDetailsClick,
                        modifier = Modifier.testTag("view_details_button")
                    ) {
                        Text("View details", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Grid of metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Steps Card
                    MetricCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEditMetricDialog = true }
                            .testTag("steps_card"),
                        icon = Icons.Default.DirectionsWalk,
                        iconTint = PrimaryBlue,
                        iconBg = PrimaryBlue.copy(alpha = 0.15f),
                        valueText = "%,d".format(steps),
                        labelText = "Steps",
                        dataPoints = listOf(2000f, 3500f, 5200f, 6800f, 8432f),
                        sparklineLineColor = PrimaryBlue,
                        sparklineFillColor = PrimaryBlue.copy(alpha = 0.12f)
                    )

                    // Heart Rate Card
                    MetricCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEditMetricDialog = true }
                            .testTag("heart_rate_card"),
                        icon = Icons.Default.Favorite,
                        iconTint = Color(0xFFD93737),
                        iconBg = Color(0xFFFFDAD6),
                        valueText = "$heartRate bpm",
                        labelText = "Avg Heart Rate",
                        dataPoints = listOf(62f, 72f, 65f, 70f, 68f),
                        sparklineLineColor = Color(0xFFD93737),
                        sparklineFillColor = Color(0xFFFFDAD6).copy(alpha = 0.3f)
                    )
                }

                // Sleep Duration Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditMetricDialog = true }
                        .testTag("sleep_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TertiaryAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep",
                                        tint = TertiaryAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sleep Duration",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${sleepHours}h ${sleepMinsRemaining}m",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress Bar (e.g. Goal 8h = 480m)
                        val sleepProgress = (sleepMinutes / 480f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { sleepProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = TertiaryAmber,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        }

        // AI Privacy Health Insights Card (Gemini 3.1 Pro Preview with High Thinking Mode)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_coach_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Coach",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Health Insights",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryTeal.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "High Thinking",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Query local health trends using Gemini 3.1 Pro with High Thinking Mode. Your metrics stay on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isAiLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Analyzing health metrics with High Thinking Mode...",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryBlue
                            )
                        }
                    } else if (aiResponse != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = aiResponse,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { viewModel.dismissAiResponse() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Dismiss", color = PrimaryBlue)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.askAiCoach("Provide a daily health summary based on my steps, heart rate, and sleep.")
                            },
                            enabled = !isAiLoading,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Analyze Today", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { showAiPromptDialog = true },
                            enabled = !isAiLoading,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ask Question", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Edit Metric Dialog
    if (showEditMetricDialog) {
        var inputSteps by remember { mutableStateOf(steps.toString()) }
        var inputHeartRate by remember { mutableStateOf(heartRate.toString()) }
        var inputSleepMins by remember { mutableStateOf(sleepMinutes.toString()) }

        AlertDialog(
            onDismissRequest = { showEditMetricDialog = false },
            title = { Text("Log Local Health Metrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputSteps,
                        onValueChange = { inputSteps = it },
                        label = { Text("Steps") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputHeartRate,
                        onValueChange = { inputHeartRate = it },
                        label = { Text("Avg Heart Rate (bpm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputSleepMins,
                        onValueChange = { inputSleepMins = it },
                        label = { Text("Sleep Duration (Minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = inputSteps.toIntOrNull() ?: steps
                        val hr = inputHeartRate.toIntOrNull() ?: heartRate
                        val sl = inputSleepMins.toIntOrNull() ?: sleepMinutes
                        viewModel.updateMetric(s, hr, sl)
                        showEditMetricDialog = false
                    }
                ) {
                    Text("Save to Local DB")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMetricDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // AI Custom Prompt Dialog
    if (showAiPromptDialog) {
        AlertDialog(
            onDismissRequest = { showAiPromptDialog = false },
            title = { Text("Ask Local AI Health Coach") },
            text = {
                OutlinedTextField(
                    value = customAiPrompt,
                    onValueChange = { customAiPrompt = it },
                    placeholder = { Text("e.g. How can I improve my sleep recovery?") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customAiPrompt.isNotBlank()) {
                            viewModel.askAiCoach(customAiPrompt)
                            showAiPromptDialog = false
                            customAiPrompt = ""
                        }
                    }
                ) {
                    Text("Ask")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiPromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConnectionCard(
    connection: ConnectionEntity,
    onToggleConnect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val icon: ImageVector = when (connection.iconType) {
        "WATCH" -> Icons.Default.Watch
        "HEALTH_CONNECT" -> Icons.Default.HealthAndSafety
        "RING" -> Icons.Default.Bedtime
        "MEALS" -> Icons.Default.Restaurant
        else -> Icons.Default.DirectionsRun
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = connection.name,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Connected",
                            tint = if (connection.isConnected) SecondaryTeal else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (connection.isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (connection.isConnected) SecondaryTeal else Color.Gray
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (connection.isConnected) "Disconnect" else "Connect") },
                        onClick = {
                            onToggleConnect()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    valueText: String,
    labelText: String,
    dataPoints: List<Float>,
    sparklineLineColor: Color,
    sparklineFillColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Sparkline in background
            SparklineCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter),
                dataPoints = dataPoints,
                lineColor = sparklineLineColor,
                fillColor = sparklineFillColor
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = labelText,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = valueText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
