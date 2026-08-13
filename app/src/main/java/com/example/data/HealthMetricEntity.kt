package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_metrics")
data class HealthMetricEntity(
    @PrimaryKey val dateString: String, // e.g. "Today" or "2026-08-10"
    val steps: Int,
    val heartRateBpm: Int,
    val sleepMinutes: Int, // e.g., 444 = 7h 24m
    val activeCalories: Int,
    val lastUpdatedText: String
)
