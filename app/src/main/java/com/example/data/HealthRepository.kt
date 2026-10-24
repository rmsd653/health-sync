package com.example.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthRepository(private val healthDao: HealthDao) {

    val connections: Flow<List<ConnectionEntity>> = healthDao.getAllConnections()
    val todayMetric: Flow<HealthMetricEntity?> = healthDao.getHealthMetricByDate("Today")
    val syncLogs: Flow<List<SyncLogEntity>> = healthDao.getAllSyncLogs()
    val userSettings: Flow<UserSettingsEntity?> = healthDao.getUserSettings()

    /**
     * Initializes only structural data-type toggles and default preferences.
     * Does NOT seed any fake health metrics or fake sync logs.
     */
    suspend fun initializeStructuralDefaultsIfEmpty() {
        if (healthDao.getConnectionCount() > 0) return

        healthDao.insertUserSettings(
            UserSettingsEntity(
                id = 1,
                autoSync = true,
                backgroundSync = true,
                dailySyncTime = "Every 15 mins",
                syncInterval = "15 minutes",
                syncSteps = true,
                syncSleep = true,
                syncHeartRate = true,
                syncWorkouts = true,
                lastSyncTimestampText = "Never synced"
            )
        )

        val initialCategories = listOf(
            ConnectionEntity(
                id = "steps",
                name = "Steps",
                isConnected = true,
                iconType = "STEPS",
                statusText = "Huawei Health ➔ Health Connect • StepsRecord",
                lastSyncTime = "Never synced"
            ),
            ConnectionEntity(
                id = "heart_rate",
                name = "Heart Rate",
                isConnected = true,
                iconType = "HEART_RATE",
                statusText = "Huawei Health ➔ Health Connect • HeartRateRecord",
                lastSyncTime = "Never synced"
            ),
            ConnectionEntity(
                id = "sleep",
                name = "Sleep (TruSleep™)",
                isConnected = true,
                iconType = "SLEEP",
                statusText = "Huawei Health ➔ Health Connect • SleepSessionRecord",
                lastSyncTime = "Never synced"
            ),
            ConnectionEntity(
                id = "calories",
                name = "Active Calories",
                isConnected = true,
                iconType = "CALORIES",
                statusText = "Huawei Health ➔ Health Connect • ActiveCaloriesBurnedRecord",
                lastSyncTime = "Never synced"
            ),
            ConnectionEntity(
                id = "activities",
                name = "Exercise & Workouts",
                isConnected = true,
                iconType = "WORKOUTS",
                statusText = "Huawei Health ➔ Health Connect • ExerciseSessionRecord",
                lastSyncTime = "Never synced"
            ),
            ConnectionEntity(
                id = "spo2",
                name = "Oxygen Saturation (SpO2)",
                isConnected = false,
                iconType = "SPO2",
                statusText = "Huawei Health ➔ Health Connect • OxygenSaturationRecord",
                lastSyncTime = "Disabled"
            )
        )
        healthDao.insertConnections(initialCategories)
    }

    suspend fun saveRealHealthMetric(
        steps: Int,
        heartRateBpm: Int,
        sleepMinutes: Int,
        activeCalories: Int,
        sourceLabel: String = "Health Connect"
    ) {
        val currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val updated = HealthMetricEntity(
            dateString = "Today",
            steps = steps,
            heartRateBpm = heartRateBpm,
            sleepMinutes = sleepMinutes,
            activeCalories = activeCalories,
            lastUpdatedText = "$sourceLabel • $currentTimeStr"
        )
        healthDao.insertHealthMetric(updated)
    }

    suspend fun clearCachedMetrics() {
        healthDao.clearHealthMetrics()
    }

    suspend fun markActiveCategoriesSynced(timestampLabel: String) {
        healthDao.updateActiveConnectionsLastSync(timestampLabel)
        val existingSettings = healthDao.getUserSettingsOnce() ?: UserSettingsEntity(id = 1)
        healthDao.insertUserSettings(existingSettings.copy(lastSyncTimestampText = timestampLabel))
    }

    suspend fun markSingleCategorySynced(categoryId: String, timestampLabel: String) {
        healthDao.updateSingleConnectionLastSync(categoryId, timestampLabel)
    }

    suspend fun updateSettings(settings: UserSettingsEntity) {
        healthDao.insertUserSettings(settings)
    }

    suspend fun toggleConnection(id: String, isConnected: Boolean) {
        healthDao.updateConnectionStatus(id, isConnected)
    }

    suspend fun addSyncLog(
        sourceName: String,
        status: String,
        detail: String,
        iconCategory: String = "STEPS"
    ) {
        val currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        healthDao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                timeString = currentTimeStr,
                sourceName = sourceName,
                status = status,
                detail = detail,
                iconCategory = iconCategory
            )
        )
    }

    suspend fun clearLogs() {
        healthDao.clearSyncLogs()
    }
}
