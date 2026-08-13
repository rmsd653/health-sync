package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    // Connections
    @Query("SELECT * FROM connections")
    fun getAllConnections(): Flow<List<ConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<ConnectionEntity>)

    @Query("UPDATE connections SET isConnected = :connected WHERE id = :id")
    suspend fun updateConnectionStatus(id: String, connected: Boolean)

    // Health Metrics
    @Query("SELECT * FROM health_metrics WHERE dateString = :date LIMIT 1")
    fun getHealthMetricByDate(date: String): Flow<HealthMetricEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthMetric(metric: HealthMetricEntity)

    // Sync Logs
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()

    // User Settings
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettings(): Flow<UserSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSettings(settings: UserSettingsEntity)
}
