package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "refunds")
data class RefundEntity(
    @PrimaryKey val idReembolso: String, // e.g. REF000001
    val idUsuario: String,
    val idTransacao: String,
    val valor: Double,
    val status: String, // EM_ANALISE, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, PAGO, REJEITADO
    val dataSolicitacao: String,
    val dataAprovacao: String,
    val dataPagamento: String,
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED, SYNCING
    val lastSyncMessage: String? = null
)
