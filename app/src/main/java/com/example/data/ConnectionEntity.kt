package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isConnected: Boolean,
    val iconType: String, // WATCH, HEALTH_CONNECT, RING, MEALS
    val statusText: String,
    val lastSyncTime: String
)
