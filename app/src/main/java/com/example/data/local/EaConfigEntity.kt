package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ea_configs")
data class EaConfigEntity(
    @PrimaryKey val mt5IdConta: String,
    
    // AUTENTICAÇÃO
    val lJJ: String = "⬛⬛⬛⬛⬛⬛⬛[ AUTENTICAÇÃO ]⬛⬛⬛⬛⬛⬛⬛",
    val senhaEa: String = "123456", // maps to SENHA in MQL5
    
    // COR
    val aYY: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛[ COR ]⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val esquemaCoresEnum: String = "CYAN_NEON", // maps to ESQUEMA_CORES_ENUM
    val corDeCanal: String = "#22D3EE", // maps to cor_de_canal
    val corDeLinhas: String = "#FF00E5", // maps to cor_de_linhas
    val corrDeEquador: String = "#FFFF00", // maps to corr_de_equador
    
    // TENDÊNCIA
    val sJJ: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ TENDÊNCIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val linhasDeEquador: Boolean = false, // maps to LINHAS_DE_EQUADOR
    val tendenciaValue: String = "TENDENCIA_DE_ALTA", // maps to TREND or TENDENCIA
    val mEquadorAlta: Double = 1.2500, // maps to M_equador_alta
    val mEquadorBaixa: Double = 1.2400, // maps to M_equador_baixa
    
    // ESTRATÉGIA
    val xxx: String = "⬛⬛⬛⬛⬛⬛⬛⬛[ ESTRATÉGIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val tema: Boolean = false, // maps to TEMA (MA 9 / 21)
    val estrategiaValue: String = "FIMATHE", // maps to ESTRATÉGIA or ESTRATEGIA
    val viradaDeJogo: Boolean = false, // maps to virada_de_jogo
    val nives: Double = 1.0, // maps to Nives
    val costurar: Boolean = true, // maps to Costurar
    val periodoOperacional: String = "PERIOD_M15", // maps to OperationalPeriod or PeriodoOperacional
    val lot: Double = 0.00, // maps to lot (0.00 default for safety)
    
    // AUTOMATICO
    val dS: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ AUTOMATICO ]⬛⬛⬛⬛⬛⬛⬛⬛",
    val eaAtivo: Boolean = true, // maps to EA_ATIVO
    val eaAuto: Boolean = false, // maps to EA_AUTO
    val periodoAuto: String = "HORA_1", // maps to AUTO_PERIOD or PERIODO_AUTO
    val autoSurfada: Boolean = false, // maps to AUTO_SURFADA
    val sessaoAsiaToquio: Boolean = false, // maps to SESSAO_ASIA_TOQUIO
    val sessaoLondres: Boolean = true, // maps to SESSAO_LONDRES
    val sessaoNovaYorqui: Boolean = true, // maps to SESSAO_NOVA_YORQUI
    val expansaoMinima: Int = 10, // maps to EXPANSAO_MINIMA
    val expansaoMaxima: Int = 30, // maps to EXPANSAO_MAXIMA
    
    // POSIC: DE ORDEM
    val dSS: String = "⬛⬛⬛⬛⬛⬛⬛[ POSIC: DE ORDEM ]⬛⬛⬛⬛⬛⬛⬛",
    val compra: Double = 1.2550, // maps to compra
    val venda: Double = 1.2500, // maps to venda
    val santo: Double = 20.0, // maps to santo
    val dedo: Int = 10, // maps to dedo
    val posicaoTake: Boolean = false, // maps to posicaoTake
    val buyTake: Double = 0.0, // maps to buy_take
    val sellTake: Double = 0.0, // maps to sell_take
    
    // GERENC: DE CAPITAL
    val fDD: String = "⬛⬛⬛⬛⬛⬛[ GERENC: DE CAPITAL ]⬛⬛⬛⬛⬛⬛",
    val saldoDemo: Double = 1000.0, // maps to SALDO
    val gerenciamentoDeRiscoDiario: Boolean = true, // maps to GERENCIAMENTO_DE_RISCO_DIARIO
    val porcentos: Double = 1.0, // maps to porcentos
    val porcentosg: Double = 1.5, // maps to poercentosg
    val gerenciamentoDeRiscoSemanal: Boolean = false, // maps to GERENCIAMENTO_DE_RISCO_SEMANAL
    val porcentoo: Double = 2.0, // maps to PORCENTOO
    val porcentoss: Double = 2.0, // maps to PORCENTOSS
    
    // PARÂM: OPERACIONAIS
    val gG: String = "⬛⬛⬛⬛⬛[ PARÂM: OPERACIONAIS ]⬛⬛⬛⬛⬛",
    val gmail: Boolean = true, // maps to GMAIL
    val notific: Boolean = true, // maps to notific
    val ativarOuDesativarVenda: Boolean = true, // maps to ativar_ou_desativar_venda
    val ativarOuDesativarCompra: Boolean = true, // maps to ativar_ou_desativar_compra
    val modificarSlParaOxO: Boolean = true, // maps to Modify_Sl_For_OxO or Modificar_Sl_Para_OxO
    val condicaoDeRompimentoC: Boolean = true, // maps to condicao_De_rompimento_c
    val condicaoDeRompimentoV: Boolean = true, // maps to condicao_De_rompimento_v
    
    // RESULTADO
    val hFF: String = "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ RESULTADO ]⬛⬛⬛⬛⬛⬛⬛⬛⬛",
    val mony: String = " Meticais ", // maps to mony
    val cambio: Double = 64.0 // maps to CAMBIO
)
