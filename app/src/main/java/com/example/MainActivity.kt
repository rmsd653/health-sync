package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.HealthRepository
import com.example.ui.HealthViewModel
import com.example.ui.HealthViewModelFactory
import com.example.ui.NavigationTab
import com.example.ui.components.BottomNavBar
import com.example.ui.components.TopHeader
import com.example.ui.screens.DashboardScreen
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

        setContent {
            HealthSyncTheme {
                HealthSyncApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HealthSyncApp(viewModel: HealthViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val todayMetric by viewModel.todayMetric.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val logFilter by viewModel.logFilter.collectAsStateWithLifecycle()
    val aiResponse by viewModel.aiResponse.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val headerTitle = when (currentTab) {
        NavigationTab.DASHBOARD -> "Dashboard"
        NavigationTab.REPORTS -> "Reports"
        NavigationTab.SETTINGS -> "Settings"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopHeader(
                title = headerTitle,
                onAvatarClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Logged in locally as Anonymous User (100% Privacy)")
                    }
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
                        aiResponse = aiResponse,
                        isAiLoading = isAiLoading,
                        onViewDetailsClick = { viewModel.selectTab(NavigationTab.REPORTS) }
                    )
                }
                NavigationTab.REPORTS -> {
                    ReportsScreen(
                        viewModel = viewModel,
                        syncLogs = syncLogs,
                        selectedFilter = logFilter,
                        onSaveReportClick = { context -> viewModel.exportDataJson(context) }
                    )
                }
                NavigationTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        userSettings = userSettings,
                        onForceSyncClick = {
                            viewModel.triggerSync { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }
            }
        }
    }
}
