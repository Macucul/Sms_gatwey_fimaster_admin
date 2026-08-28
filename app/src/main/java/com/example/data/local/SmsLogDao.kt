package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogs(): Flow<List<SmsLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(log: SmsLogEntity)

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SENT'")
    fun getSentSmsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    fun getFailedSmsCountFlow(): Flow<Int>

    @Query("DELETE FROM sms_logs")
    suspend fun clearSmsLogs()
}
