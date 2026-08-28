package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RefundDao {
    @Query("SELECT * FROM refunds ORDER BY timestamp DESC")
    fun getAllRefunds(): Flow<List<RefundEntity>>

    @Query("SELECT * FROM refunds WHERE status = :status ORDER BY timestamp DESC")
    fun getRefundsByStatus(status: String): Flow<List<RefundEntity>>

    @Query("SELECT * FROM refunds WHERE idReembolso = :id LIMIT 1")
    suspend fun getRefundById(id: String): RefundEntity?

    @Query("SELECT * FROM refunds WHERE idTransacao = :txId LIMIT 1")
    suspend fun getRefundByTransactionId(txId: String): RefundEntity?

    @Query("SELECT * FROM refunds WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedRefunds(): List<RefundEntity>

    @Query("SELECT COUNT(*) FROM refunds WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun getUnsyncedRefundsCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefund(refund: RefundEntity)

    @Update
    suspend fun updateRefund(refund: RefundEntity)

    @Delete
    suspend fun deleteRefund(refund: RefundEntity)

    @Query("SELECT COUNT(*) FROM refunds")
    fun getRefundCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM refunds")
    suspend fun getRefundCount(): Int

    @Query("SELECT COUNT(*) FROM refunds WHERE status = 'AGUARDANDO_APROVACAO' OR status = 'AGUARDANDO_PAGAMENTO' OR status = 'EM_ANALISE'")
    fun getPendingRefundCountFlow(): Flow<Int>

    @Query("DELETE FROM refunds")
    suspend fun clearAllRefunds()
}
