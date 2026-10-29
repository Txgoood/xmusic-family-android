package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus {
    SYNCED, PENDING, ERROR
}

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String?,
    val isChild: Boolean,
    val pinHash: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
