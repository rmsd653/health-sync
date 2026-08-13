package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val autoSync: Boolean = true,
    val backgroundSync: Boolean = false,
    val dailySyncTime: String = "08:00 AM",
    val syncInterval: String = "1 hour",
    val syncSteps: Boolean = true,
    val syncSleep: Boolean = true,
    val syncHeartRate: Boolean = true,
    val syncWorkouts: Boolean = false,
    val lastSyncTimestampText: String = "Just now"
)
