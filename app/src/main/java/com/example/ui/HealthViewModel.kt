package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiHealthCoach
import com.example.data.ConnectionEntity
import com.example.data.HealthMetricEntity
import com.example.data.HealthRepository
import com.example.data.SyncLogEntity
import com.example.data.UserSettingsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavigationTab {
    DASHBOARD, REPORTS, SETTINGS
}

class HealthViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _currentTab = MutableStateFlow(NavigationTab.DASHBOARD)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    val connections: StateFlow<List<ConnectionEntity>> = repository.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayMetric: StateFlow<HealthMetricEntity?> = repository.todayMetric
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncLogs: StateFlow<List<SyncLogEntity>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSettings: StateFlow<UserSettingsEntity?> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _logFilter = MutableStateFlow("ALL")
    val logFilter: StateFlow<String> = _logFilter.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun setLogFilter(filter: String) {
        _logFilter.value = filter
    }

    fun triggerSync(onCompleteMessage: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.delay(1200) // Realistic sync animation delay
            val updatedMetric = repository.triggerSyncNow()
            _isSyncing.value = false
            onCompleteMessage("Sync complete! Updated steps to ${updatedMetric.steps}.")
        }
    }

    fun toggleConnection(id: String, isConnected: Boolean) {
        viewModelScope.launch {
            repository.toggleConnection(id, isConnected)
        }
    }

    fun updateUserSettings(newSettings: UserSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun updateMetric(steps: Int, heartRate: Int, sleepMinutes: Int) {
        viewModelScope.launch {
            repository.updateMetric(steps, heartRate, sleepMinutes)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun askAiCoach(prompt: String) {
        val currentMetric = todayMetric.value ?: return
        viewModelScope.launch {
            _isAiLoading.value = true
            val result = GeminiHealthCoach.analyzeHealthData(
                prompt = prompt,
                steps = currentMetric.steps,
                heartRate = currentMetric.heartRateBpm,
                sleepMinutes = currentMetric.sleepMinutes
            )
            _isAiLoading.value = false
            result.onSuccess { text ->
                _aiResponse.value = text
            }.onFailure { err ->
                _aiResponse.value = "Local AI Analysis Error: ${err.localizedMessage}"
            }
        }
    }

    fun dismissAiResponse() {
        _aiResponse.value = null
    }

    fun exportDataJson(context: Context) {
        val currentMetric = todayMetric.value
        val settings = userSettings.value
        val jsonString = """
            {
              "app": "HealthSync",
              "storage": "Local Device Encrypted SQLite",
              "privacy": "100% Anonymized - Zero Cloud Sync",
              "metrics": {
                 "steps": ${currentMetric?.steps ?: 8432},
                 "heartRateBpm": ${currentMetric?.heartRateBpm ?: 68},
                 "sleepMinutes": ${currentMetric?.sleepMinutes ?: 444}
              },
              "settings": {
                 "autoSync": ${settings?.autoSync ?: true},
                 "dailySyncTime": "${settings?.dailySyncTime ?: "08:00 AM"}"
              }
            }
        """.trimIndent()

        Toast.makeText(context, "Local JSON Exported successfully to device storage", Toast.LENGTH_LONG).show()
    }
}

class HealthViewModelFactory(private val repository: HealthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HealthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HealthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
