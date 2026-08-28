package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_payments")
data class PendingPaymentEntity(
    @PrimaryKey val idPendente: String, // e.g. PEN000001
    val telefone: String,
    val nome: String,
    val valorAcumulado: Double,
    val valorMinimo: Double,
    val faltam: Double,
    val transacoes: String, // Comma-separated list of transaction IDs
    val status: String = "AGUARDANDO_COMPLEMENTO",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED, SYNCING
    val lastSyncMessage: String? = null
)
