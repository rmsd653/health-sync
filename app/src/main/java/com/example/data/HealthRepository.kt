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

    suspend fun seedInitialDataIfEmpty() {
        if (healthDao.getConnectionCount() > 0) return

        // Seed Settings if null
        healthDao.insertUserSettings(
            UserSettingsEntity(
                id = 1,
                autoSync = true,
                backgroundSync = false,
                dailySyncTime = "08:00 AM",
                syncInterval = "1 hour",
                syncSteps = true,
                syncSleep = true,
                syncHeartRate = true,
                syncWorkouts = false,
                lastSyncTimestampText = "Just now"
            )
        )

        // Seed Connections
        val initialConnections = listOf(
            ConnectionEntity("huawei", "Huawei Health", true, "WATCH", "Connected", "Just now"),
            ConnectionEntity("health_connect", "Health Connect", true, "HEALTH_CONNECT", "Connected", "Just now"),
            ConnectionEntity("apple_health", "Apple Health", true, "WATCH", "Connected", "Today, 08:42 AM"),
            ConnectionEntity("oura", "Oura Ring", true, "RING", "Connected", "Today, 08:41 AM"),
            ConnectionEntity("mfp", "MyFitnessPal", true, "MEALS", "Connected", "Yesterday")
        )
        healthDao.insertConnections(initialConnections)

        // Seed Metrics
        healthDao.insertHealthMetric(
            HealthMetricEntity(
                dateString = "Today",
                steps = 8432,
                heartRateBpm = 68,
                sleepMinutes = 444, // 7h 24m
                activeCalories = 420,
                lastUpdatedText = "Just now"
            )
        )

        // Seed Sync Logs
        val now = System.currentTimeMillis()
        val initialLogs = listOf(
            SyncLogEntity(
                timestamp = now,
                timeString = "08:42 AM",
                sourceName = "Apple Health Sync",
                status = "Success",
                detail = "5,420 steps transferred",
                iconCategory = "STEPS"
            ),
            SyncLogEntity(
                timestamp = now - 60000,
                timeString = "08:41 AM",
                sourceName = "Oura Ring Sleep Data",
                status = "Success",
                detail = "7h 12m sleep updated",
                iconCategory = "SLEEP"
            ),
            SyncLogEntity(
                timestamp = now - 36000000,
                timeString = "Yesterday, 10:15 PM",
                sourceName = "MyFitnessPal Meals",
                status = "Partial",
                detail = "Dinner macros missing",
                iconCategory = "MEALS"
            ),
            SyncLogEntity(
                timestamp = now - 86400000,
                timeString = "Yesterday, 08:30 AM",
                sourceName = "Apple Health Sync",
                status = "Success",
                detail = "10,240 steps transferred",
                iconCategory = "STEPS"
            )
        )
        for (log in initialLogs) {
            healthDao.insertSyncLog(log)
        }
    }

    suspend fun triggerSyncNow(): HealthMetricEntity {
        val currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val now = System.currentTimeMillis()

        // Update random metrics slightly to simulate real device sync
        val newSteps = 8432 + (10..150).random()
        val newHeartRate = (65..72).random()
        val updatedMetric = HealthMetricEntity(
            dateString = "Today",
            steps = newSteps,
            heartRateBpm = newHeartRate,
            sleepMinutes = 444,
            activeCalories = 430,
            lastUpdatedText = "Just now"
        )
        healthDao.insertHealthMetric(updatedMetric)

        // Add a new sync log entry
        val newLog = SyncLogEntity(
            timestamp = now,
            timeString = currentTimeStr,
            sourceName = "Local Device Sync",
            status = "Success",
            detail = "+${(10..150).random()} steps & $newHeartRate bpm heart rate synced",
            iconCategory = "STEPS"
        )
        healthDao.insertSyncLog(newLog)

        // Update settings last sync text while preserving existing user preferences
        val existingSettings = healthDao.getUserSettingsOnce() ?: UserSettingsEntity(id = 1)
        healthDao.insertUserSettings(existingSettings.copy(lastSyncTimestampText = "Just now"))

        return updatedMetric
    }

    suspend fun updateSettings(settings: UserSettingsEntity) {
        healthDao.insertUserSettings(settings)
    }

    suspend fun toggleConnection(id: String, isConnected: Boolean) {
        healthDao.updateConnectionStatus(id, isConnected)
    }

    suspend fun updateMetric(steps: Int, heartRate: Int, sleepMinutes: Int) {
        val updated = HealthMetricEntity(
            dateString = "Today",
            steps = steps,
            heartRateBpm = heartRate,
            sleepMinutes = sleepMinutes,
            activeCalories = (steps * 0.04).toInt(),
            lastUpdatedText = "Just now"
        )
        healthDao.insertHealthMetric(updated)
    }

    suspend fun clearLogs() {
        healthDao.clearSyncLogs()
    }
}
