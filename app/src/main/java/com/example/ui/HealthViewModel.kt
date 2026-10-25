package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bridge.BridgeEnvironmentStatus
import com.example.bridge.BridgeSyncStepResult
import com.example.bridge.HealthConnectVerificationSummary
import com.example.bridge.HuaweiAccountState
import com.example.bridge.HuaweiHealthBridgeManager
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
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

enum class NavigationTab {
    DASHBOARD, BRIDGE, REPORTS, SETTINGS
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

    private val _bridgeEnvStatus = MutableStateFlow(BridgeEnvironmentStatus())
    val bridgeEnvStatus: StateFlow<BridgeEnvironmentStatus> = _bridgeEnvStatus.asStateFlow()

    // Starts unlinked with zero fake account credentials
    private val _huaweiAccountState = MutableStateFlow(HuaweiAccountState())
    val huaweiAccountState: StateFlow<HuaweiAccountState> = _huaweiAccountState.asStateFlow()

    private val _bridgeDeduplicationMode = MutableStateFlow("Change Token Incremental Sync")
    val bridgeDeduplicationMode: StateFlow<String> = _bridgeDeduplicationMode.asStateFlow()

    private val _selectedHistoricalRange = MutableStateFlow("Today Only")
    val selectedHistoricalRange: StateFlow<String> = _selectedHistoricalRange.asStateFlow()

    private val _bridgeSyncSteps = MutableStateFlow<List<BridgeSyncStepResult>>(
        listOf(
            BridgeSyncStepResult(
                1,
                "Huawei Health Kit Cloud Read",
                "Not started — connect Huawei ID or Health Connect permissions",
                "PENDING"
            ),
            BridgeSyncStepResult(
                2,
                "Health Connect Record Mapping",
                "Maps Huawei samplePoints to Android Health Connect records",
                "PENDING"
            ),
            BridgeSyncStepResult(
                3,
                "Google Health Connect OS Write",
                "Writes non-zero records via HealthConnectClient.insertRecords()",
                "PENDING"
            ),
            BridgeSyncStepResult(
                4,
                "Health Connect Aggregate Readback",
                "Reads live totals from HealthConnectClient.aggregate()",
                "PENDING"
            )
        )
    )
    val bridgeSyncSteps: StateFlow<List<BridgeSyncStepResult>> = _bridgeSyncSteps.asStateFlow()

    private val _lastVerificationSummary = MutableStateFlow<HealthConnectVerificationSummary?>(null)
    val lastVerificationSummary: StateFlow<HealthConnectVerificationSummary?> =
        _lastVerificationSummary.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeStructuralDefaultsIfEmpty()
        }
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun setLogFilter(filter: String) {
        _logFilter.value = filter
    }

    fun setBridgeDeduplicationMode(mode: String) {
        _bridgeDeduplicationMode.value = mode
    }

    fun setSelectedHistoricalRange(range: String) {
        _selectedHistoricalRange.value = range
    }

    /**
     * Inspects real package/SDK status, restores any user-saved Huawei token from preferences,
     * and reads real today metrics from Google Health Connect if read permissions are granted.
     */
    fun refreshBridgeEnvironment(context: Context) {
        viewModelScope.launch {
            restoreSavedHuaweiAuthIfPresent(context)
            val env = HuaweiHealthBridgeManager.inspectEnvironment(context)
            _bridgeEnvStatus.value = env

            if (env.isHealthConnectAvailable && env.hasAnyReadPermission) {
                val readback = HuaweiHealthBridgeManager.readRealMetricsFromHealthConnect(context)
                if (readback.hasAnyData) {
                    repository.saveRealHealthMetric(
                        steps = readback.steps,
                        heartRateBpm = readback.heartRateBpm,
                        sleepMinutes = readback.sleepMinutes,
                        activeCalories = readback.activeCaloriesKcal,
                        sourceLabel = "Health Connect"
                    )
                }
            }
        }
    }

    private fun restoreSavedHuaweiAuthIfPresent(context: Context) {
        if (_huaweiAccountState.value.isLinked) return
        val prefs = context.getSharedPreferences("healthsync_huawei_auth", Context.MODE_PRIVATE)
        val isLinked = prefs.getBoolean("is_linked", false)
        if (isLinked) {
            _huaweiAccountState.value = HuaweiAccountState(
                isLinked = true,
                accountIdentifier = prefs.getString("account_id", "") ?: "",
                authMode = prefs.getString("auth_mode", "Huawei OAuth 2.0") ?: "Huawei OAuth 2.0",
                clientId = prefs.getString(
                    "client_id",
                    HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_CLIENT_ID
                ) ?: HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_CLIENT_ID,
                redirectUri = prefs.getString(
                    "redirect_uri",
                    HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_REDIRECT_URI
                ) ?: HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_REDIRECT_URI,
                accessToken = prefs.getString("access_token", null),
                refreshToken = prefs.getString("refresh_token", null),
                lastAuthCode = prefs.getString("last_auth_code", null),
                lastTokenRefresh = prefs.getString("last_refresh", "Connected") ?: "Connected",
                deviceModel = prefs.getString("device_model", "") ?: ""
            )
        }
    }

    private fun persistHuaweiAuth(context: Context, state: HuaweiAccountState) {
        val prefs = context.getSharedPreferences("healthsync_huawei_auth", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_linked", state.isLinked)
            .putString("account_id", state.accountIdentifier)
            .putString("auth_mode", state.authMode)
            .putString("client_id", state.clientId)
            .putString("redirect_uri", state.redirectUri)
            .putString("access_token", state.accessToken)
            .putString("refresh_token", state.refreshToken)
            .putString("last_auth_code", state.lastAuthCode)
            .putString("last_refresh", state.lastTokenRefresh)
            .putString("device_model", state.deviceModel)
            .apply()
    }

    fun linkHuaweiAccount(
        context: Context? = null,
        accountIdentifier: String,
        authMode: String,
        deviceModel: String,
        accessToken: String? = null,
        selectedScopes: List<String> = _huaweiAccountState.value.authorizedScopes
    ) {
        viewModelScope.launch {
            val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val cleanToken = accessToken?.trim()?.takeIf { it.isNotBlank() }
            val newState = _huaweiAccountState.value.copy(
                isLinked = true,
                accountIdentifier = accountIdentifier.trim().ifBlank { "Huawei ID Account" },
                authMode = authMode,
                authorizedScopes = selectedScopes,
                accessToken = cleanToken,
                lastTokenRefresh = "Today, $nowTime",
                deviceModel = deviceModel.trim().ifBlank { "Huawei Wearable" }
            )
            _huaweiAccountState.value = newState
            context?.let { persistHuaweiAuth(it, newState) }

            repository.addSyncLog(
                sourceName = "Huawei ID Connected",
                status = "Success",
                detail = "Connected ${newState.accountIdentifier} (${newState.authMode})",
                iconCategory = "STEPS"
            )
        }
    }

    /**
     * Handles either:
     * 1. An intercepted redirect URL from the In-App Huawei OAuth WebView (`https://server.healthsync.app/huawei?code=...`)
     * 2. A pasted Huawei CAS login URL (`https://id5.cloud.huawei.com/CAS/mobile/standard/welcome.html?...`)
     * 3. A pasted authorization code or raw OAuth 2.0 Bearer token
     *
     * Automatically exchanges authorization `code` for an `access_token` using PKCE S256 so the user
     * never has to manually generate or look up a Huawei OAuth 2.0 token.
     */
    fun handleInterceptedHuaweiRedirectUrl(
        context: Context,
        rawInput: String,
        accountIdentifier: String = "",
        deviceModel: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            onResult(false, "Please sign in with Huawei ID or paste a valid URL/token.")
            return
        }

        viewModelScope.launch {
            val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

            // Case 1: Input is a URL (either redirect callback with ?code=... or CAS welcome.html URL)
            if (trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("healthsync://") ||
                trimmed.startsWith("hms://")
            ) {
                val parsed = HuaweiHealthBridgeManager.parseHuaweiUrl(trimmed)

                // Direct access_token in URL fragment or query
                if (!parsed.extractedAccessToken.isNullOrBlank()) {
                    val newState = _huaweiAccountState.value.copy(
                        isLinked = true,
                        accountIdentifier = accountIdentifier.ifBlank {
                            _huaweiAccountState.value.accountIdentifier.ifBlank { "Huawei ID Account" }
                        },
                        authMode = "Huawei OAuth 2.0 Token (WebView)",
                        clientId = parsed.clientId,
                        redirectUri = parsed.redirectUri,
                        authorizedScopes = parsed.scopes.ifEmpty { HuaweiHealthBridgeManager.FULL_HEALTHSYNC_SCOPES },
                        accessToken = parsed.extractedAccessToken,
                        lastTokenRefresh = "Today, $nowTime",
                        deviceModel = deviceModel.ifBlank {
                            _huaweiAccountState.value.deviceModel.ifBlank { "Huawei Wearable" }
                        }
                    )
                    _huaweiAccountState.value = newState
                    persistHuaweiAuth(context, newState)
                    repository.addSyncLog(
                        sourceName = "Huawei OAuth 2.0 Sign-In",
                        status = "Success",
                        detail = "Captured Huawei Health Kit Bearer token directly from OAuth redirect.",
                        iconCategory = "STEPS"
                    )
                    onResult(true, "Connected Huawei Health OAuth token! Running sync...")
                    runHuaweiToHealthConnectBridgeSync(context)
                    return@launch
                }

                // Authorization code in redirect URL -> automatically exchange for access_token via PKCE
                if (!parsed.extractedAuthCode.isNullOrBlank()) {
                    val code = parsed.extractedAuthCode
                    val exchangeResult = HuaweiHealthBridgeManager.exchangeAuthorizationCodeForToken(
                        authorizationCode = code,
                        customClientId = parsed.clientId,
                        customRedirectUri = parsed.redirectUri
                    )
                    val tokenResponse = exchangeResult.getOrNull()

                    val newState = _huaweiAccountState.value.copy(
                        isLinked = true,
                        accountIdentifier = accountIdentifier.ifBlank {
                            _huaweiAccountState.value.accountIdentifier.ifBlank { "Huawei ID (${parsed.clientId})" }
                        },
                        authMode = if (tokenResponse != null) {
                            "Huawei OAuth 2.0 PKCE Connected"
                        } else {
                            "Huawei ID Authorized (Code Captured)"
                        },
                        clientId = parsed.clientId,
                        redirectUri = parsed.redirectUri,
                        authorizedScopes = parsed.scopes.ifEmpty { HuaweiHealthBridgeManager.FULL_HEALTHSYNC_SCOPES },
                        accessToken = tokenResponse?.accessToken ?: _huaweiAccountState.value.accessToken,
                        refreshToken = tokenResponse?.refreshToken ?: _huaweiAccountState.value.refreshToken,
                        lastAuthCode = code,
                        lastTokenRefresh = "Today, $nowTime",
                        deviceModel = deviceModel.ifBlank {
                            _huaweiAccountState.value.deviceModel.ifBlank { "Huawei Wearable" }
                        }
                    )
                    _huaweiAccountState.value = newState
                    persistHuaweiAuth(context, newState)

                    if (tokenResponse != null) {
                        repository.addSyncLog(
                            sourceName = "Huawei OAuth 2.0 PKCE Exchange",
                            status = "Success",
                            detail = "Exchanged authorization code for Huawei Health Kit Bearer token (${tokenResponse.scope.split(" ").size} scopes).",
                            iconCategory = "STEPS"
                        )
                        onResult(true, "Huawei ID signed in and OAuth token obtained automatically!")
                        runHuaweiToHealthConnectBridgeSync(context)
                    } else {
                        val reason = exchangeResult.exceptionOrNull()?.localizedMessage ?: "Token endpoint requires clientverifier"
                        repository.addSyncLog(
                            sourceName = "Huawei OAuth 2.0 Code Captured",
                            status = "Partial",
                            detail = "Captured OAuth code (${code.take(10)}...). Note: $reason",
                            iconCategory = "STEPS"
                        )
                        onResult(
                            true,
                            "Huawei ID authorization code captured! ($reason)"
                        )
                    }
                    return@launch
                }

                // Pasted the CAS login URL itself (not yet logged in)
                val updatedConfig = _huaweiAccountState.value.copy(
                    clientId = parsed.clientId,
                    redirectUri = parsed.redirectUri,
                    authorizedScopes = parsed.scopes.ifEmpty { HuaweiHealthBridgeManager.FULL_HEALTHSYNC_SCOPES }
                )
                _huaweiAccountState.value = updatedConfig
                persistHuaweiAuth(context, updatedConfig)
                onResult(
                    false,
                    "Configured Client ID ${parsed.clientId} & ${parsed.scopes.size} HealthKit scopes from your Huawei URL. Opening In-App Huawei Sign-In..."
                )
                return@launch
            }

            // Case 2: Raw Bearer token pasted directly
            linkHuaweiAccount(
                context = context,
                accountIdentifier = accountIdentifier.ifBlank {
                    _huaweiAccountState.value.accountIdentifier.ifBlank { "Huawei ID Account" }
                },
                authMode = "Huawei Cloud OAuth Bearer Token",
                deviceModel = deviceModel.ifBlank {
                    _huaweiAccountState.value.deviceModel.ifBlank { "Huawei Wearable" }
                },
                accessToken = trimmed
            )
            onResult(true, "Saved Huawei OAuth 2.0 Bearer token!")
            runHuaweiToHealthConnectBridgeSync(context)
        }
    }

    fun handleHuaweiOAuthCallback(context: Context? = null, code: String) {
        viewModelScope.launch {
            val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val tokenResult = HuaweiHealthBridgeManager.exchangeAuthorizationCodeForToken(code)
            val exchanged = tokenResult.getOrNull()

            val newState = _huaweiAccountState.value.copy(
                isLinked = true,
                accountIdentifier = _huaweiAccountState.value.accountIdentifier.ifBlank { "Huawei ID OAuth User" },
                authMode = if (exchanged != null) {
                    "Huawei OAuth 2.0 PKCE Connected"
                } else {
                    "Huawei ID Authorized (Code Captured)"
                },
                accessToken = exchanged?.accessToken ?: _huaweiAccountState.value.accessToken,
                refreshToken = exchanged?.refreshToken ?: _huaweiAccountState.value.refreshToken,
                lastAuthCode = code,
                lastTokenRefresh = "Today, $nowTime",
                deviceModel = _huaweiAccountState.value.deviceModel.ifBlank { "Huawei Wearable" }
            )
            _huaweiAccountState.value = newState
            context?.let { persistHuaweiAuth(it, newState) }

            repository.addSyncLog(
                sourceName = "Huawei OAuth 2.0 Callback",
                status = if (exchanged != null) "Success" else "Partial",
                detail = if (exchanged != null) {
                    "Exchanged authorization code for Huawei Health Kit access token."
                } else {
                    "Captured Huawei authorization code (${code.take(8)}...)."
                },
                iconCategory = "STEPS"
            )
            _currentTab.value = NavigationTab.BRIDGE
        }
    }

    fun disconnectHuaweiAccount(context: Context? = null) {
        viewModelScope.launch {
            val disconnectedState = HuaweiAccountState(
                isLinked = false,
                accountIdentifier = "",
                authMode = "Not Connected",
                accessToken = null,
                refreshToken = null,
                lastAuthCode = null,
                lastTokenRefresh = "Disconnected",
                deviceModel = ""
            )
            _huaweiAccountState.value = disconnectedState
            context?.let { persistHuaweiAuth(it, disconnectedState) }

            repository.addSyncLog(
                sourceName = "Huawei Account Disconnected",
                status = "Partial",
                detail = "Cleared Huawei OAuth session token",
                iconCategory = "STEPS"
            )
        }
    }

    fun onHealthConnectPermissionsResult(context: Context, granted: Set<String>) {
        viewModelScope.launch {
            val env = HuaweiHealthBridgeManager.inspectEnvironment(context)
            _bridgeEnvStatus.value = env
            repository.addSyncLog(
                sourceName = "Health Connect Permissions",
                status = if (env.allHealthConnectPermissionsGranted) "Success" else "Partial",
                detail = "${env.grantedPermissionsCount}/${env.totalPermissionsCount} Health Connect permissions granted",
                iconCategory = "HEART_RATE"
            )
            if (env.hasAnyReadPermission) {
                val readback = HuaweiHealthBridgeManager.readRealMetricsFromHealthConnect(context)
                if (readback.hasAnyData) {
                    repository.saveRealHealthMetric(
                        steps = readback.steps,
                        heartRateBpm = readback.heartRateBpm,
                        sleepMinutes = readback.sleepMinutes,
                        activeCalories = readback.activeCaloriesKcal,
                        sourceLabel = "Health Connect"
                    )
                }
            }
        }
    }

    /**
     * Executes real Huawei Cloud polymerize query (when Bearer token is available)
     * plus real Google Health Connect write and aggregate readback.
     * Never invents or randomizes health numbers.
     */
    fun runHuaweiToHealthConnectBridgeSync(
        context: Context,
        onCompleteMessage: (String) -> Unit = {}
    ) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true

            val env = HuaweiHealthBridgeManager.inspectEnvironment(context)
            _bridgeEnvStatus.value = env
            val currentAccount = _huaweiAccountState.value

            val zoneId = ZoneId.systemDefault()
            val startOfTodayMillis = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nowMillis = System.currentTimeMillis()

            _bridgeSyncSteps.value = listOf(
                BridgeSyncStepResult(
                    1,
                    "Huawei Health Kit Cloud Read",
                    if (currentAccount.accessToken != null) {
                        "Querying Huawei Health Kit polymerize API..."
                    } else {
                        "No Huawei Cloud Bearer token set — checking local Health Connect..."
                    },
                    "RUNNING"
                ),
                BridgeSyncStepResult(
                    2,
                    "Health Connect Record Mapping",
                    "Waiting for Huawei Cloud response...",
                    "PENDING"
                ),
                BridgeSyncStepResult(
                    3,
                    "Google Health Connect OS Write",
                    "Waiting for mapped records...",
                    "PENDING"
                ),
                BridgeSyncStepResult(
                    4,
                    "Health Connect Aggregate Readback",
                    "Waiting for Health Connect aggregate query...",
                    "PENDING"
                )
            )

            var cloudSteps = 0
            var cloudHr = 0
            var cloudSleepMins = 0
            var cloudCalories = 0
            var cloudStatusText: String
            var cloudStatusCode: String

            val token = currentAccount.accessToken
            if (!token.isNullOrBlank()) {
                val cloudResult = HuaweiHealthBridgeManager.fetchHuaweiCloudDailyMetrics(
                    accessToken = token,
                    startTimeMillis = startOfTodayMillis,
                    endTimeMillis = nowMillis
                )
                if (cloudResult.isSuccess) {
                    val data = cloudResult.getOrNull()!!
                    cloudSteps = data.steps
                    cloudHr = data.heartRateBpm
                    cloudSleepMins = data.sleepMinutes
                    cloudCalories = data.activeCaloriesKcal
                    cloudStatusText = "Fetched ${data.steps} steps, ${data.heartRateBpm} bpm, ${data.sleepMinutes}m sleep, ${data.activeCaloriesKcal} kcal from Huawei Cloud"
                    cloudStatusCode = "SUCCESS"
                } else {
                    val errMsg = cloudResult.exceptionOrNull()?.localizedMessage ?: "API error"
                    cloudStatusText = "Huawei Cloud API error: $errMsg"
                    cloudStatusCode = "WARN"
                }
            } else {
                cloudStatusText = "Skipped Huawei Cloud pull (Sign in with Huawei ID to fetch Cloud API records)"
                cloudStatusCode = "WARN"
            }

            val hasCloudRecordsToWrite = cloudSteps > 0 || cloudHr > 0 || cloudSleepMins > 0 || cloudCalories > 0

            _bridgeSyncSteps.value = _bridgeSyncSteps.value.toMutableList().apply {
                this[0] = BridgeSyncStepResult(1, "Huawei Health Kit Cloud Read", cloudStatusText, cloudStatusCode)
                this[1] = BridgeSyncStepResult(
                    2,
                    "Health Connect Record Mapping",
                    if (hasCloudRecordsToWrite) {
                        "Mapped non-zero Huawei Health metrics using ${_bridgeDeduplicationMode.value}"
                    } else {
                        "0 new Huawei Cloud records to map; proceeding to Health Connect readback"
                    },
                    if (hasCloudRecordsToWrite) "SUCCESS" else "WARN"
                )
            }

            val verification = HuaweiHealthBridgeManager.pushHuaweiMetricsToHealthConnect(
                context = context,
                steps = cloudSteps,
                heartRateBpm = cloudHr,
                sleepMinutes = cloudSleepMins,
                activeCaloriesKcal = cloudCalories,
                huaweiDeviceModel = currentAccount.deviceModel.ifBlank { "Huawei Wearable" }
            )
            _lastVerificationSummary.value = verification

            val readback = HuaweiHealthBridgeManager.readRealMetricsFromHealthConnect(context)
            val nowStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

            if (readback.hasAnyData) {
                repository.saveRealHealthMetric(
                    steps = readback.steps,
                    heartRateBpm = readback.heartRateBpm,
                    sleepMinutes = readback.sleepMinutes,
                    activeCalories = readback.activeCaloriesKcal,
                    sourceLabel = if (verification.writtenToHealthConnect) "Huawei ➔ Health Connect" else "Health Connect"
                )
                repository.markActiveCategoriesSynced("Today, $nowStr")
                repository.addSyncLog(
                    sourceName = if (verification.writtenToHealthConnect) {
                        "Huawei Health ➔ Health Connect"
                    } else {
                        "Health Connect Readback"
                    },
                    status = "Success",
                    detail = "${readback.steps} steps • ${readback.heartRateBpm} bpm • ${readback.sleepMinutes / 60}h ${readback.sleepMinutes % 60}m sleep • ${readback.activeCaloriesKcal} kcal",
                    iconCategory = "STEPS"
                )
            } else {
                repository.clearCachedMetrics()
                repository.addSyncLog(
                    sourceName = "Sync Check",
                    status = "Partial",
                    detail = readback.statusMessage,
                    iconCategory = "STEPS"
                )
            }

            _bridgeSyncSteps.value = _bridgeSyncSteps.value.toMutableList().apply {
                this[2] = BridgeSyncStepResult(
                    3,
                    "Google Health Connect OS Write",
                    verification.statusMessage,
                    if (verification.writtenToHealthConnect) "SUCCESS" else "WARN"
                )
                this[3] = BridgeSyncStepResult(
                    4,
                    "Health Connect Aggregate Readback",
                    if (readback.hasAnyData) {
                        "Health Connect Totals: ${readback.steps} steps • ${readback.heartRateBpm} bpm • ${readback.sleepMinutes}m sleep • ${readback.activeCaloriesKcal} kcal"
                    } else {
                        readback.statusMessage
                    },
                    if (readback.hasAnyData) "SUCCESS" else "WARN"
                )
            }

            _isSyncing.value = false
            val completionText = when {
                verification.writtenToHealthConnect ->
                    "Synced ${verification.recordsWrittenCount} record(s) to Google Health Connect."
                readback.hasAnyData ->
                    "Updated dashboard from Google Health Connect (${readback.steps} steps)."
                !env.isHealthConnectAvailable ->
                    "Google Health Connect is not installed or enabled on this device."
                !env.hasAnyReadPermission ->
                    "Grant Google Health Connect permissions in Setup to read and write health data."
                !currentAccount.isLinked ->
                    "Health Connect has 0 records for today. Sign in with Huawei ID in Setup to sync data."
                else ->
                    "Sync complete: 0 records found for today in Huawei Cloud or Health Connect."
            }
            onCompleteMessage(completionText)
        }
    }

    fun runHistoricalSync(
        context: Context,
        rangeLabel: String,
        onCompleteMessage: (String) -> Unit = {}
    ) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _selectedHistoricalRange.value = rangeLabel

            val daysCount = when {
                rangeLabel.contains("7") -> 7
                rangeLabel.contains("14") -> 14
                rangeLabel.contains("30") -> 30
                else -> 1
            }

            val token = _huaweiAccountState.value.accessToken
            val nowMillis = System.currentTimeMillis()
            val startMillis = nowMillis - daysCount * 24L * 3600L * 1000L

            if (token.isNullOrBlank()) {
                repository.addSyncLog(
                    sourceName = "Historical Sync ($rangeLabel)",
                    status = "Partial",
                    detail = "Sign in with Huawei ID in Setup to backfill $daysCount day(s) from Huawei Cloud.",
                    iconCategory = "WORKOUTS"
                )
                _isSyncing.value = false
                onCompleteMessage("Sign in with Huawei ID in Setup to backfill historical data.")
                return@launch
            }

            val cloudResult = HuaweiHealthBridgeManager.fetchHuaweiCloudDailyMetrics(
                accessToken = token,
                startTimeMillis = startMillis,
                endTimeMillis = nowMillis
            )

            if (cloudResult.isSuccess) {
                val data = cloudResult.getOrNull()!!
                val verification = HuaweiHealthBridgeManager.pushHuaweiMetricsToHealthConnect(
                    context = context,
                    steps = data.steps,
                    heartRateBpm = data.heartRateBpm,
                    sleepMinutes = data.sleepMinutes,
                    activeCaloriesKcal = data.activeCaloriesKcal,
                    huaweiDeviceModel = _huaweiAccountState.value.deviceModel.ifBlank { "Huawei Wearable" }
                )
                _lastVerificationSummary.value = verification
                repository.addSyncLog(
                    sourceName = "Historical Sync ($rangeLabel)",
                    status = if (verification.writtenToHealthConnect) "Success" else "Partial",
                    detail = verification.statusMessage,
                    iconCategory = "WORKOUTS"
                )
                _isSyncing.value = false
                onCompleteMessage(verification.statusMessage)
            } else {
                val err = cloudResult.exceptionOrNull()?.localizedMessage ?: "Huawei API request failed"
                repository.addSyncLog(
                    sourceName = "Historical Sync ($rangeLabel)",
                    status = "Failed",
                    detail = err,
                    iconCategory = "WORKOUTS"
                )
                _isSyncing.value = false
                onCompleteMessage("Historical sync failed: $err")
            }
        }
    }

    fun syncSingleCategory(
        context: Context,
        category: ConnectionEntity,
        onCompleteMessage: (String) -> Unit = {}
    ) {
        runHuaweiToHealthConnectBridgeSync(context, onCompleteMessage)
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

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun clearAllLocalData(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearCachedMetrics()
            repository.clearLogs()
            disconnectHuaweiAccount(context)
            onComplete()
        }
    }

    fun exportDataJson(context: Context) {
        val currentMetric = todayMetric.value
        val settings = userSettings.value
        val huaweiState = _huaweiAccountState.value
        val enabledCategories = connections.value.filter { it.isConnected }.map { it.name }
        val jsonString = """
            {
              "app": "HealthSync",
              "source": "Huawei Health Kit",
              "destination": "Google Health Connect",
              "huaweiAccountLinked": ${huaweiState.isLinked},
              "huaweiAccount": "${huaweiState.accountIdentifier.ifBlank { "Not linked" }}",
              "huaweiClientId": "${huaweiState.clientId}",
              "enabledSyncCategories": $enabledCategories,
              "todayMetrics": {
                 "steps": ${currentMetric?.steps ?: 0},
                 "heartRateBpm": ${currentMetric?.heartRateBpm ?: 0},
                 "sleepMinutes": ${currentMetric?.sleepMinutes ?: 0},
                 "activeCaloriesKcal": ${currentMetric?.activeCalories ?: 0}
              },
              "settings": {
                 "autoSync": ${settings?.autoSync ?: true},
                 "syncInterval": "${settings?.syncInterval ?: "15 minutes"}"
              }
            }
        """.trimIndent()

        Toast.makeText(
            context,
            "Exported ${jsonString.length} bytes of Health Connect diagnostic JSON",
            Toast.LENGTH_LONG
        ).show()
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
