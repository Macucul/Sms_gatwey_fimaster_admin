package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val idUsuario: String, // e.g., USR000001
    val status: String, // e.g., "AGUARDANDO_ATIVACAO", "ATIVO", "REJEITADO"
    val origem: String = "sms_fimaster",
    val telefone: String, // maps to "numero" in the JSON
    val nome: String,
    val idTransacao: String,
    val saldo: Double,
    val senhaHash: String, // SHA-256 hash
    val salt: String,
    val tokenRecuperacao: String,
    val nivelAutorizacao: String = "CLIENTE",
    val dataRegistro: String, // e.g., "2026-06-25T08:30:00Z"
    val ultimaAtualizacao: String, // e.g., "2026-06-25T08:30:00Z"
    
    // MT5 account registration
    val mt5Registrado: Boolean = false,
    val mt5IdConta: String = "",
    
    // Licença
    val licencaAtiva: Boolean = false,
    val licencaProduto: String = "FiMaster EA Pro",
    val licencaValidade: String = "",
    
    // Reembolso
    val reembolsoSolicitado: Boolean = false,
    val reembolsoStatus: String = "NENHUM",
    
    // Autorização
    val autorizacaoStatus: String = "PENDENTE",
    val autorizacaoAprovadoPor: String = "",
    val autorizacaoDataAprovacao: String = "",
    
    // Crédito guardado como segunda via
    val creditoGuardado: Double = 0.0,
    
    // Novas propriedades de Renovação/Expiração de Licença
    val licencaPlano: String = "Pro",
    val ultimaRenovacao: String = "",
    val totalRenovacoes: Int = 0,
    val historicoRenovacoes: String = "[]", // JSON Array string: [{"data": "2026-08-16 22:30:00", "valor": 3500.0, "descricao": "Assinatura Trimestral Pro"}]
    val deviceId: String = "",
    
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED, SYNCING
    val lastSyncMessage: String? = null,
    val syncAttempts: Int = 0
)
