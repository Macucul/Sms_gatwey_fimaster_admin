package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String,
    val messageText: String,
    val type: String, // OUTGOING_WELCOME, OUTGOING_REJECTED
    val status: String, // SENT, FAILED
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
