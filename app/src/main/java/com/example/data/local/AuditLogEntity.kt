package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val messageText: String,
    val isMatched: Boolean,
    val extractedData: String?, // JSON or plain description
    val status: String, // FILTERED, SUCCESS, FAILED_BALANCE, FAILED_PARSING, ERROR
    val details: String?,
    val timestamp: Long = System.currentTimeMillis()
)
