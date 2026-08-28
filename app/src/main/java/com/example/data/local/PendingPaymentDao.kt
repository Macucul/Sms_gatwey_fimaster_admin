package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPaymentDao {
    @Query("SELECT * FROM pending_payments ORDER BY timestamp DESC")
    fun getAllPending(): Flow<List<PendingPaymentEntity>>

    @Query("SELECT * FROM pending_payments WHERE telefone = :phone LIMIT 1")
    suspend fun getPendingByPhone(phone: String): PendingPaymentEntity?

    @Query("SELECT * FROM pending_payments WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedPending(): List<PendingPaymentEntity>

    @Query("SELECT COUNT(*) FROM pending_payments WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun getUnsyncedPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(pending: PendingPaymentEntity)

    @Update
    suspend fun updatePending(pending: PendingPaymentEntity)

    @Delete
    suspend fun deletePending(pending: PendingPaymentEntity)

    @Query("DELETE FROM pending_payments WHERE telefone = :phone")
    suspend fun deletePendingByPhone(phone: String)

    @Query("SELECT COUNT(*) FROM pending_payments")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_payments")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM pending_payments")
    suspend fun clearAllPending()
}
