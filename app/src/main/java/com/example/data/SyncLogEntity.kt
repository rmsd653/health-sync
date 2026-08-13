package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val timeString: String,
    val sourceName: String,
    val status: String, // Success, Partial, Failed
    val detail: String,
    val iconCategory: String // STEPS, SLEEP, MEALS, HEART_RATE, WORKOUTS
)
