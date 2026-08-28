package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "license_tiers")
data class LicenseTierEntity(
    @PrimaryKey val id: String, // "starter", "pro", "master_vip", "trial"
    val nome: String,           // "Starter", "Pro", "Master VIP", "Trial"
    val valor: Double,          // Valor em MT (ex: 500.0, 1500.0, 3000.0, 50.0)
    val diasValidade: Int,      // Dias de validade (ex: 30, 90, 365, 7)
    val descricao: String,      // Descrição de benefícios e recursos
    
    // Parâmetros de Recursos da Licença
    val templates: Boolean = false,         // teamplates / templates (Booleano)
    val capturaTela: Boolean = false,       // captura de tela (Booleano)
    val graficoPatrimonio: Boolean = false, // grafico de património (Booleano)
    val audio: Boolean = false,             // audio (Booleano)
    val vincularConta: Int = 1,             // vincular conta (Numérico)
    val sala: Boolean = false,              // sala (Booleano)
    
    // Links de Redes Sociais / Canais de Atendimento
    val whatsappLink: String = "",          // Link completo do WhatsApp (ex: https://wa.me/...)
    val telegramLink: String = "",          // Link completo do Telegram (ex: https://t.me/...)
    
    // QR Code de Pagamento
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val qrCodeBytes: ByteArray? = null,     // Dados binários (bytes) da imagem do QR Code
    val qrCodeLink: String = "",            // Link / chave decodificada do QR Code
    
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LicenseTierEntity

        if (id != other.id) return false
        if (nome != other.nome) return false
        if (valor != other.valor) return false
        if (diasValidade != other.diasValidade) return false
        if (descricao != other.descricao) return false
        if (templates != other.templates) return false
        if (capturaTela != other.capturaTela) return false
        if (graficoPatrimonio != other.graficoPatrimonio) return false
        if (audio != other.audio) return false
        if (vincularConta != other.vincularConta) return false
        if (sala != other.sala) return false
        if (whatsappLink != other.whatsappLink) return false
        if (telegramLink != other.telegramLink) return false
        if (qrCodeLink != other.qrCodeLink) return false
        if (qrCodeBytes != null) {
            if (other.qrCodeBytes == null) return false
            if (!qrCodeBytes.contentEquals(other.qrCodeBytes)) return false
        } else if (other.qrCodeBytes != null) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nome.hashCode()
        result = 31 * result + valor.hashCode()
        result = 31 * result + diasValidade
        result = 31 * result + descricao.hashCode()
        result = 31 * result + templates.hashCode()
        result = 31 * result + capturaTela.hashCode()
        result = 31 * result + graficoPatrimonio.hashCode()
        result = 31 * result + audio.hashCode()
        result = 31 * result + vincularConta
        result = 31 * result + sala.hashCode()
        result = 31 * result + whatsappLink.hashCode()
        result = 31 * result + telegramLink.hashCode()
        result = 31 * result + qrCodeLink.hashCode()
        result = 31 * result + (qrCodeBytes?.contentHashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}


