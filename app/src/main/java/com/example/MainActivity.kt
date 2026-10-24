package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.HealthRepository
import com.example.ui.HealthViewModel
import com.example.ui.HealthViewModelFactory
import com.example.ui.NavigationTab
import com.example.ui.components.BottomNavBar
import com.example.ui.components.TopHeader
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HuaweiBridgeScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HealthSyncTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HealthViewModel by viewModels {
        val database = AppDatabase.getDatabase(this)
        val repository = HealthRepository(database.healthDao())
        HealthViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            HealthSyncTheme {
                HealthSyncApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "healthsync" && data.host == "huawei-oauth-callback") {
            val code = data.getQueryParameter("code") ?: ""
            if (code.isNotBlank()) {
                viewModel.handleHuaweiOAuthCallback(this, code)
            }
        } else if (
            intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
        ) {
            viewModel.selectTab(NavigationTab.BRIDGE)
        }
    }
}

@Composable
fun HealthSyncApp(viewModel: HealthViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val todayMetric by viewModel.todayMetric.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val logFilter by viewModel.logFilter.collectAsStateWithLifecycle()
    val bridgeEnv by viewModel.bridgeEnvStatus.collectAsStateWithLifecycle()
    val huaweiAccount by viewModel.huaweiAccountState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshBridgeEnvironment(context)
    }

    if (currentTab != NavigationTab.DASHBOARD) {
        BackHandler {
            viewModel.selectTab(NavigationTab.DASHBOARD)
        }
    }

    val headerTitle = when (currentTab) {
        NavigationTab.DASHBOARD -> "Health Connect Sync"
        NavigationTab.BRIDGE -> "App permissions & setup"
        NavigationTab.REPORTS -> "Recent access & logs"
        NavigationTab.SETTINGS -> "Sync preferences"
    }

    val isFullyConnected = huaweiAccount.isLinked && bridgeEnv.allHealthConnectPermissionsGranted

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopHeader(
                title = headerTitle,
                isFullyConnected = isFullyConnected,
                onAvatarClick = {
                    viewModel.selectTab(NavigationTab.BRIDGE)
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = currentTab,
                onTabSelected = { tab -> viewModel.selectTab(tab) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.DASHBOARD -> {
                    DashboardScreen(
                        viewModel = viewModel,
                        todayMetric = todayMetric,
                        connections = connections,
                        isSyncing = isSyncing,
                        onViewDetailsClick = { viewModel.selectTab(NavigationTab.REPORTS) },
                        onOpenBridgeClick = { viewModel.selectTab(NavigationTab.BRIDGE) },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
                NavigationTab.BRIDGE -> {
                    HuaweiBridgeScreen(
                        viewModel = viewModel,
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
                NavigationTab.REPORTS -> {
                    ReportsScreen(
                        viewModel = viewModel,
                        syncLogs = syncLogs,
                        selectedFilter = logFilter,
                        onSaveReportClick = { ctx -> viewModel.exportDataJson(ctx) }
                    )
                }
                NavigationTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        userSettings = userSettings,
                        onForceSyncClick = {
                            viewModel.runHuaweiToHealthConnectBridgeSync(context) { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }
            }
        }
    }
}
