package com.hari.ftqr.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scans", indices = [Index("createdAt")])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawValue: String,
    val format: Int,
    val kind: String,
    val title: String,
    val createdAt: Long,
)
