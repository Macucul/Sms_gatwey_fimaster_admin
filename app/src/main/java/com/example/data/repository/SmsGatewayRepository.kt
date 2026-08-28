package com.example.data.repository

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import com.example.data.local.*
import com.example.data.remote.*
import com.example.util.SecurityUtils
import com.example.util.SmsParser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SmsGatewayRepository(private val context: Context) {
    private val TAG = "SmsGatewayRepository"
    private val db = SmsGatewayDatabase.getDatabase(context)
    
    val userDao = db.userDao()
    val auditLogDao = db.auditLogDao()
    val smsLogDao = db.smsLogDao()
    val pendingPaymentDao = db.pendingPaymentDao()
    val refundDao = db.refundDao()
    val eaConfigDao = db.eaConfigDao()
    val licenseTierDao = db.licenseTierDao()
    val configManager = ConfigManager(context)

    // Flow listings
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val allAuditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()
    val allSmsLogs: Flow<List<SmsLogEntity>> = smsLogDao.getAllSmsLogs()
    val allPending: Flow<List<PendingPaymentEntity>> = pendingPaymentDao.getAllPending()
    val allRefunds: Flow<List<RefundEntity>> = refundDao.getAllRefunds()
    val allLicenseTiers: Flow<List<LicenseTierEntity>> = licenseTierDao.getAllLicenseTiers()

    // Counts for Dashboard
    val totalUsersFlow: Flow<Int> = userDao.getUserCountFlow()
    val approvedUsersFlow: Flow<Int> = userDao.getApprovedCountFlow()
    val pendingUsersFlow: Flow<Int> = userDao.getPendingCountFlow()
    val rejectedUsersFlow: Flow<Int> = userDao.getRejectedCountFlow()
    val lastUserFlow: Flow<UserEntity?> = userDao.getLastUserFlow()

    val sentSmsCountFlow: Flow<Int> = smsLogDao.getSentSmsCountFlow()
    val failedSmsCountFlow: Flow<Int> = smsLogDao.getFailedSmsCountFlow()

    val totalRefundsCountFlow: Flow<Int> = refundDao.getRefundCountFlow()
    val pendingRefundsCountFlow: Flow<Int> = refundDao.getPendingRefundCountFlow()

    // Unsynced queue count flows
    val unsyncedUsersCountFlow: Flow<Int> = userDao.getUnsyncedUsersCountFlow()
    val unsyncedPendingCountFlow: Flow<Int> = pendingPaymentDao.getUnsyncedPendingCountFlow()
    val unsyncedRefundsCountFlow: Flow<Int> = refundDao.getUnsyncedRefundsCountFlow()

    // Retrofit builders
    private fun getOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private fun getGitHubService(): GitHubService {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(getOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubService::class.java)
    }

    private fun getFastApiService(customUrl: String): FastApiService {
        val formattedUrl = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(getOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FastApiService::class.java)
    }

    // Date Utilities
    fun getCurrentTimestampIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun calculateExpiryDate(months: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, months)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(calendar.time)
    }

    fun getDaysDifference(pastDateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val pastDate = sdf.parse(pastDateStr) ?: return 0
            val currentDate = Date()
            val diffInMillis = currentDate.time - pastDate.time
            java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffInMillis)
        } catch (e: Exception) {
            0
        }
    }

    fun extendExpiryDate(currentValidade: String, extraMonths: Double): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = try {
            sdf.parse(currentValidade) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        val wholeMonths = extraMonths.toInt()
        val fractionalMonth = extraMonths - wholeMonths
        val extraDays = (fractionalMonth * 30.44).toInt()
        
        calendar.add(Calendar.MONTH, wholeMonths)
        calendar.add(Calendar.DAY_OF_MONTH, extraDays)
        return sdf.format(calendar.time)
    }

    fun getCurrentDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getCurrentDateTimeStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date())
    }

    fun parseDateOrDateTime(dateStr: String): Date? {
        if (dateStr.isBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val d = sdf.parse(dateStr)
                if (d != null) return d
            } catch (_: Exception) {}
        }
        return null
    }

    fun calculateExpiryDateFromDays(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, days)
        val sdf = SimpleDateFormat("yyyy-MM-dd 23:59:59", Locale.US)
        return sdf.format(calendar.time)
    }

    fun extendExpiryDateByDays(currentValidade: String, extraDays: Int): String {
        val date = parseDateOrDateTime(currentValidade) ?: Date()
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_MONTH, extraDays)
        val sdf = SimpleDateFormat("yyyy-MM-dd 23:59:59", Locale.US)
        return sdf.format(calendar.time)
    }

    fun isDateExpired(dateStr: String, todayStr: String): Boolean {
        return try {
            val date = parseDateOrDateTime(dateStr)
            val today = parseDateOrDateTime(todayStr) ?: parseDateOrDateTime(getCurrentDateStr())
            if (date != null && today != null) today.after(date) else false
        } catch (e: Exception) {
            false
        }
    }

    fun getFormattedProduto(name: String): String {
        val clean = name.trim()
        return if (clean.startsWith("FiMaster EA", ignoreCase = true)) clean else "FiMaster EA $clean"
    }

    fun getFormattedPlano(name: String): String {
        val clean = name.trim()
        val withoutPrefix = clean.replace(Regex("^(?i)FiMaster\\s*(EA)?\\s*"), "").trim()
        return withoutPrefix.ifEmpty { clean }
    }

    fun buildLicenseDescription(tierName: String, days: Int): String {
        val plano = getFormattedPlano(tierName)
        val period = when {
            days in 25..35 -> "Mensal"
            days in 80..100 -> "Trimestral"
            days in 170..200 -> "Semestral"
            days >= 350 -> "Anual"
            days <= 10 -> "Avaliação"
            else -> "${days} Dias"
        }
        return if (plano.equals("Trial", ignoreCase = true) || days <= 10) {
            "Avaliação Trial"
        } else {
            "Assinatura $period $plano"
        }
    }

    fun isSyncConfigured(): Boolean {
        return when (configManager.syncMode) {
            ConfigManager.MODE_GITHUB -> {
                val token = configManager.githubToken.trim()
                val repo = configManager.githubRepo.trim()
                token.isNotEmpty() && repo.isNotEmpty() && repo.split("/").size == 2
            }
            ConfigManager.MODE_FASTAPI -> {
                val url = configManager.fastApiUrl.trim()
                url.isNotEmpty() && !url.contains("SEU_") && (url.startsWith("http://") || url.startsWith("https://"))
            }
            ConfigManager.MODE_FIREBASE -> true
            else -> false
        }
    }

    suspend fun ensureDefaultLicenseTiers() = withContext(Dispatchers.IO) {
        if (licenseTierDao.count() == 0) {
            val defaultTiers = listOf(
                LicenseTierEntity(
                    id = "starter",
                    nome = "Starter",
                    valor = 500.0,
                    diasValidade = 30,
                    descricao = "Acesso inicial ao robô MT5 com 1 conta vinculada, gerenciamento de risco padrão e suporte comunitário.",
                    templates = true,
                    capturaTela = false,
                    graficoPatrimonio = true,
                    audio = true,
                    vincularConta = 1,
                    sala = false
                ),
                LicenseTierEntity(
                    id = "pro",
                    nome = "Pro",
                    valor = 1500.0,
                    diasValidade = 90,
                    descricao = "Acesso profissional com até 2 contas MT5, estratégias de alta frequência, trailing stop avançado e suporte prioritário.",
                    templates = true,
                    capturaTela = true,
                    graficoPatrimonio = true,
                    audio = true,
                    vincularConta = 2,
                    sala = true
                ),
                LicenseTierEntity(
                    id = "master_vip",
                    nome = "Master VIP",
                    valor = 3000.0,
                    diasValidade = 365,
                    descricao = "Licença VIP Anual ilimitada: Multi-contas MT5, assessoria direta de setup, parâmetros exclusivos e suporte 24/7.",
                    templates = true,
                    capturaTela = true,
                    graficoPatrimonio = true,
                    audio = true,
                    vincularConta = 5,
                    sala = true
                ),
                LicenseTierEntity(
                    id = "trial",
                    nome = "Trial",
                    valor = 50.0,
                    diasValidade = 7,
                    descricao = "Período de avaliação de 7 dias para testes em ambiente real ou demo com parâmetros pré-configurados.",
                    templates = false,
                    capturaTela = false,
                    graficoPatrimonio = false,
                    audio = true,
                    vincularConta = 1,
                    sala = false
                )
            )
            licenseTierDao.insertAll(defaultTiers)
        }
    }

    suspend fun saveLicenseTier(tier: LicenseTierEntity) = withContext(Dispatchers.IO) {
        licenseTierDao.insertOrUpdate(tier)
        if (configManager.autoSync) {
            syncLicenseTiers()
        }
    }

    suspend fun syncLicenseTiers() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        try {
            val tiers = licenseTierDao.getAllLicenseTiersList()
            val builder = StringBuilder()
            builder.append("{\n")
            val tierEntries = tiers.map { tier ->
                val base64Qr = if (tier.qrCodeBytes != null && tier.qrCodeBytes.isNotEmpty()) {
                    android.util.Base64.encodeToString(tier.qrCodeBytes, android.util.Base64.NO_WRAP)
                } else ""
                """  "${tier.id}": {
    "nome": "${tier.nome}",
    "valor": ${tier.valor},
    "dias_validade": ${tier.diasValidade},
    "descricao": "${tier.descricao.replace("\"", "\\\"")}",
    "teamplates": ${tier.templates},
    "templates": ${tier.templates},
    "captura_de_tela": ${tier.capturaTela},
    "grafico_de_patrimonio": ${tier.graficoPatrimonio},
    "audio": ${tier.audio},
    "vincular_conta": ${tier.vincularConta},
    "sala": ${tier.sala},
    "whatsapp_link": "${tier.whatsappLink.replace("\"", "\\\"")}",
    "telegram_link": "${tier.telegramLink.replace("\"", "\\\"")}",
    "qr_code_link": "${tier.qrCodeLink.replace("\"", "\\\"")}",
    "qr_code_base64": "$base64Qr"
  }"""
            }
            builder.append(tierEntries.joinToString(",\n"))
            builder.append("\n}")
            val jsonPayload = builder.toString()
            syncRawFile("dados/indice/licenca.json", jsonPayload, "SMS Gateway: Atualização de Planos de Licenças (dados/indice/licenca.json)")
            syncRawFile("dados/indice/licença.json", jsonPayload, "SMS Gateway: Atualização de Planos de Licenças (dados/indice/licença.json)")
            syncRawFile("dados/indices/licenca.json", jsonPayload, "SMS Gateway: Atualização de Planos de Licenças (dados/indices/licenca.json)")
            syncRawFile("dados/configuracao/licenca.json", jsonPayload, "SMS Gateway: Atualização de Planos de Licenças (dados/configuracao/licenca.json)")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar planos de licenças: ${e.message}")
        }
    }

    suspend fun findMatchingLicenseTier(valorRecebido: Double, customerPhone: String): LicenseTierEntity? = withContext(Dispatchers.IO) {
        ensureDefaultLicenseTiers()
        val tiers = licenseTierDao.getAllLicenseTiersList()
        val hasDiscount = if (configManager.discountEnabled) {
            auditLogDao.getAllLogs().first().any {
                (SmsParser.cleanPhone(it.sender) == customerPhone) && it.status == "DISCOUNT_APPLIED"
            }
        } else false

        for (tier in tiers) {
            val baseDiff = Math.abs(valorRecebido - tier.valor)
            if (baseDiff < 0.05) {
                return@withContext tier
            }
            if (hasDiscount) {
                val discountFactor = (100.0 - configManager.discountPercent) / 100.0
                val discountedVal = tier.valor * discountFactor
                if (Math.abs(valorRecebido - discountedVal) < 0.05) {
                    return@withContext tier
                }
            }
        }
        return@withContext null
    }

    fun formatDateToPt(dateStr: String): String {
        return try {
            val date = parseDateOrDateTime(dateStr)
            if (date != null) {
                val toSdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                toSdf.format(date)
            } else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    suspend fun checkAndExpirateLicenses() = withContext(Dispatchers.IO) {
        val allUsers = userDao.getAllUsers().first()
        val todayStr = getCurrentDateStr()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var anyExpired = false
        
        allUsers.forEach { user ->
            if (user.licencaValidade.isNotEmpty()) {
                try {
                    val expiryDate = sdf.parse(user.licencaValidade)
                    val todayDate = sdf.parse(todayStr)
                    
                    if (todayDate != null && expiryDate != null && todayDate.after(expiryDate)) {
                        if (user.status != "EXPIRADO" || user.licencaAtiva) {
                            val valorMinimo = configManager.valorMinimoAtivacao
                            if (user.creditoGuardado >= valorMinimo && valorMinimo > 0) {
                                // "se ouver salda da segunda via use"
                                val extraMonths = (user.creditoGuardado / valorMinimo) * configManager.validadeMeses
                                val newExpiryDate = extendExpiryDate(user.licencaValidade, extraMonths)
                                
                                val updatedUser = user.copy(
                                    creditoGuardado = 0.0,
                                    licencaValidade = newExpiryDate,
                                    licencaAtiva = true,
                                    status = "ATIVO",
                                    ultimaAtualizacao = getCurrentTimestampIso(),
                                    syncStatus = "PENDING",
                                    totalRenovacoes = user.totalRenovacoes + 1,
                                    ultimaRenovacao = getCurrentDateStr()
                                )
                                
                                val currentHistoryJson = try {
                                    org.json.JSONArray(user.historicoRenovacoes)
                                } catch (e: Exception) {
                                    org.json.JSONArray()
                                }
                                val newRenovation = org.json.JSONObject().apply {
                                    put("data", getCurrentDateStr())
                                    put("id_transacao", "SEGUNDA_VIA_AUTO_RENEW")
                                    put("valor", user.creditoGuardado)
                                }
                                currentHistoryJson.put(newRenovation)
                                val updatedUserWithHistory = updatedUser.copy(historicoRenovacoes = currentHistoryJson.toString())
                                
                                userDao.insertUser(updatedUserWithHistory)
                                anyExpired = true
                                
                                auditLogDao.insertLog(
                                    AuditLogEntity(
                                        sender = "SystemAutoRenew",
                                        messageText = "Auto-renovação de licença usando saldo de segunda via.",
                                        isMatched = true,
                                        extractedData = "{ \"saldo_consumido\": ${user.creditoGuardado}, \"nova_validade\": \"$newExpiryDate\" }",
                                        status = "AUTO_RENEWED",
                                        details = "Licença do usuário ${user.idUsuario} expiraria em ${user.licencaValidade}. Consumido saldo de segunda via de ${user.creditoGuardado} MT para estender a validade até $newExpiryDate."
                                    )
                                )
                                
                                if (configManager.autoSync) {
                                    syncUser(updatedUserWithHistory)
                                    buildAndSyncEaLicense(updatedUserWithHistory)
                                }
                            } else {
                                val expiredUser = user.copy(
                                    status = "EXPIRADO",
                                    licencaAtiva = false,
                                    ultimaAtualizacao = getCurrentTimestampIso(),
                                    syncStatus = "PENDING"
                                )
                                userDao.insertUser(expiredUser)
                                anyExpired = true
                                
                                auditLogDao.insertLog(
                                    AuditLogEntity(
                                        sender = "SystemLicenseCheck",
                                        messageText = "Licença expirada para ${user.telefone}",
                                        isMatched = true,
                                        extractedData = "{ \"usuario\": \"${user.idUsuario}\", \"validade\": \"${user.licencaValidade}\" }",
                                        status = "LICENSE_EXPIRED",
                                        details = "Licença do usuário ${user.idUsuario} expirou em ${user.licencaValidade} e o status foi alterado para EXPIRADO."
                                    )
                                )
                                
                                if (configManager.autoSendSms) {
                                    sendCustomSms(
                                        user.telefone,
                                        "Fimaster\n\nA sua licença expirou.\n\nPara continuar utilizando o EA Fimaster, efetue a renovação.\n\nApós a confirmação do pagamento, a validade será atualizada automaticamente.\n\nAcesse o site para mais informações."
                                    )
                                }
                                
                                if (configManager.autoSync) {
                                    syncUser(expiredUser)
                                    buildAndSyncEaLicense(expiredUser)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsGatewayRepository", "Erro ao verificar validade do usuário ${user.idUsuario}: ${e.message}")
                }
            }
        }
        if (anyExpired && configManager.autoSync) {
            try {
                buildAndSyncMt5Index()
                buildAndSyncTelefonesIndex()
            } catch (e: Exception) {
                Log.e("SmsGatewayRepository", "Erro ao sincronizar índices após expiração de licenças: ${e.message}")
            }
        }
    }

    suspend fun sendLicenseReminders() = withContext(Dispatchers.IO) {
        val allUsers = userDao.getAllUsers().first()
        val todayStr = getCurrentDateStr()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = try { sdf.parse(todayStr) } catch(e: Exception) { null } ?: return@withContext
        
        allUsers.forEach { user ->
            if (user.licencaValidade.isNotEmpty() && user.licencaAtiva) {
                try {
                    val expiryDate = sdf.parse(user.licencaValidade) ?: return@forEach
                    val diffMillis = expiryDate.time - today.time
                    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                    
                    if (diffDays == 30 || diffDays == 7 || diffDays == 3 || diffDays == 1 || diffDays == 0) {
                        val diasText = if (diffDays == 0) "hoje" else "em $diffDays ${if (diffDays == 1) "dia" else "dias"}"
                        val msg = "Fimaster\n\nA sua licença expirará $diasText.\n\nPara evitar interrupções, efetue a renovação antes da data de vencimento.\n\nApós a confirmação do pagamento, a licença será renovada automaticamente.\n\nObrigado por utilizar a Fimaster."
                        
                        auditLogDao.insertLog(
                            AuditLogEntity(
                                sender = "SystemLicenseReminder",
                                messageText = "Lembrete de expiração de $diffDays dias para ${user.telefone}",
                                isMatched = true,
                                extractedData = "{ \"usuario\": \"${user.idUsuario}\", \"dias_restantes\": $diffDays }",
                                status = "REMINDER_SENT",
                                details = "Envio de SMS automático de lembrete de expiração de licença ($diffDays dias restantes)."
                            )
                        )
                        
                        if (configManager.autoSendSms) {
                            sendCustomSms(user.telefone, msg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsGatewayRepository", "Erro ao calcular lembrete do usuário ${user.idUsuario}: ${e.message}")
                }
            }
        }
    }

    suspend fun buildAndSyncEaLicense(user: UserEntity) = withContext(Dispatchers.IO) {
        val eaJsonStr = """{
  "status": "${if (user.licencaAtiva) "ATIVO" else "EXPIRADO"}",
  "produto": "${user.licencaProduto}",
  "validade": "${user.licencaValidade}",
  "referencia": "${user.idTransacao}"
}"""
        try {
            syncRawFile("dados/licencas/${user.idUsuario}.json", eaJsonStr, "SMS Gateway: Consulta EA Licença ${user.idUsuario}")
            syncRawFile("dados/licencas/${user.telefone}.json", eaJsonStr, "SMS Gateway: Consulta EA Licença ${user.telefone}")
            if (user.mt5IdConta.isNotEmpty()) {
                syncRawFile("dados/licencas/${user.mt5IdConta}.json", eaJsonStr, "SMS Gateway: Consulta EA Licença MT5 ${user.mt5IdConta}")
            }
        } catch (e: Exception) {
            Log.e("SmsGatewayRepository", "Erro ao sincronizar consulta EA de licença: ${e.message}")
        }
    }

    fun getEaConfigFlow(mt5: String): Flow<EaConfigEntity?> = eaConfigDao.getEaConfigByMt5(mt5)
    
    suspend fun getEaConfig(mt5: String): EaConfigEntity? = eaConfigDao.getEaConfigByMt5Suspended(mt5)

    suspend fun saveAndSyncEaConfig(config: EaConfigEntity) = withContext(Dispatchers.IO) {
        // Save locally in Room database
        eaConfigDao.insertEaConfig(config)

        // 1. Build beautiful JSON parameters
        val json = """{
  "lJJ": "${config.lJJ}",
  "SENHA": "${config.senhaEa}",
  "aYY": "${config.aYY}",
  "ESQUEMA_CORES_ENUM": "${config.esquemaCoresEnum}",
  "cor_de_canal": "${config.corDeCanal}",
  "cor_de_linhas": "${config.corDeLinhas}",
  "corr_de_equador": "${config.corrDeEquador}",
  "sJJ": "${config.sJJ}",
  "LINHAS_DE_EQUADOR": ${config.linhasDeEquador},
  "TREND": "${config.tendenciaValue}",
  "TENDENCIA": "${config.tendenciaValue}",
  "M_equador_alta": ${config.mEquadorAlta},
  "M_equador_baixa": ${config.mEquadorBaixa},
  "xxx": "${config.xxx}",
  "TEMA": ${config.tema},
  "ESTRATÉGIA": "${config.estrategiaValue}",
  "ESTRATEGIA": "${config.estrategiaValue}",
  "virada_de_jogo": ${config.viradaDeJogo},
  "Nives": ${config.nives},
  "Costurar": ${config.costurar},
  "OperationalPeriod": "${config.periodoOperacional}",
  "PeriodoOperacional": "${config.periodoOperacional}",
  "lot": ${config.lot},
  "dS": "${config.dS}",
  "EA_ATIVO": ${config.eaAtivo},
  "EA_AUTO": ${config.eaAuto},
  "AUTO_PERIOD": "${config.periodoAuto}",
  "PERIODO_AUTO": "${config.periodoAuto}",
  "AUTO_SURFADA": ${config.autoSurfada},
  "SESSAO_ASIA_TOQUIO": ${config.sessaoAsiaToquio},
  "SESSAO_LONDRES": ${config.sessaoLondres},
  "SESSAO_NOVA_YORQUI": ${config.sessaoNovaYorqui},
  "EXPANSAO_MINIMA": ${config.expansaoMinima},
  "EXPANSAO_MAXIMA": ${config.expansaoMaxima},
  "dSS": "${config.dSS}",
  "compra": ${config.compra},
  "venda": ${config.venda},
  "santo": ${config.santo},
  "dedo": ${config.dedo},
  "posicaoTake": ${config.posicaoTake},
  "buy_take": ${config.buyTake},
  "sell_take": ${config.sellTake},
  "fDD": "${config.fDD}",
  "SALDO": ${config.saldoDemo},
  "GERENCIAMENTO_DE_RISCO_DIARIO": ${config.gerenciamentoDeRiscoDiario},
  "porcentos": ${config.porcentos},
  "poercentosg": ${config.porcentosg},
  "GERENCIAMENTO_DE_RISCO_SEMANAL": ${config.gerenciamentoDeRiscoSemanal},
  "PORCENTOO": ${config.porcentoo},
  "PORCENTOSS": ${config.porcentoss},
  "gG": "${config.gG}",
  "GMAIL": ${config.gmail},
  "notific": ${config.notific},
  "ativar_ou_desativar_venda": ${config.ativarOuDesativarVenda},
  "ativar_ou_desativar_compra": ${config.ativarOuDesativarCompra},
  "Modify_Sl_For_OxO": ${config.modificarSlParaOxO},
  "Modificar_Sl_Para_OxO": ${config.modificarSlParaOxO},
  "condicao_De_rompimento_c": ${config.condicaoDeRompimentoC},
  "condicao_De_rompimento_v": ${config.condicaoDeRompimentoV},
  "hFF": "${config.hFF}",
  "mony": "${config.mony}",
  "CAMBIO": ${config.cambio}
}"""

        // 2. Build .set standard MetaTrader formatted file for high compatibility
        val setFile = buildString {
            append("lJJ=${config.lJJ}\n")
            append("SENHA=${config.senhaEa}\n")
            append("aYY=${config.aYY}\n")
            append("cor_de_canal=${config.corDeCanal}\n")
            append("cor_de_linhas=${config.corDeLinhas}\n")
            append("corr_de_equador=${config.corrDeEquador}\n")
            append("sJJ=${config.sJJ}\n")
            append("LINHAS_DE_EQUADOR=${if (config.linhasDeEquador) "1" else "0"}\n")
            append("TENDENCIA=${config.tendenciaValue}\n")
            append("M_equador_alta=${config.mEquadorAlta}\n")
            append("M_equador_baixa=${config.mEquadorBaixa}\n")
            append("xxx=${config.xxx}\n")
            append("TEMA=${if (config.tema) "1" else "0"}\n")
            append("ESTRATEGIA=${config.estrategiaValue}\n")
            append("virada_de_jogo=${if (config.viradaDeJogo) "1" else "0"}\n")
            append("Nives=${config.nives}\n")
            append("Costurar=${if (config.costurar) "1" else "0"}\n")
            append("PeriodoOperacional=${config.periodoOperacional}\n")
            append("lot=${config.lot}\n")
            append("dS=${config.dS}\n")
            append("EA_AUTO=${if (config.eaAuto) "1" else "0"}\n")
            append("PERIODO_AUTO=${config.periodoAuto}\n")
            append("AUTO_SURFADA=${if (config.autoSurfada) "1" else "0"}\n")
            append("SESSAO_ASIA_TOQUIO=${if (config.sessaoAsiaToquio) "1" else "0"}\n")
            append("SESSAO_LONDRES=${if (config.sessaoLondres) "1" else "0"}\n")
            append("SESSAO_NOVA_YORQUI=${if (config.sessaoNovaYorqui) "1" else "0"}\n")
            append("EXPANSAO_MINIMA=${config.expansaoMinima}\n")
            append("EXPANSAO_MAXIMA=${config.expansaoMaxima}\n")
            append("dSS=${config.dSS}\n")
            append("compra=${config.compra}\n")
            append("venda=${config.venda}\n")
            append("santo=${config.santo}\n")
            append("dedo=${config.dedo}\n")
            append("posicaoTake=${if (config.posicaoTake) "1" else "0"}\n")
            append("buy_take=${config.buyTake}\n")
            append("sell_take=${config.sellTake}\n")
            append("fDD=${config.fDD}\n")
            append("SALDO=${config.saldoDemo}\n")
            append("GERENCIAMENTO_DE_RISCO_DIARIO=${if (config.gerenciamentoDeRiscoDiario) "1" else "0"}\n")
            append("porcentos=${config.porcentos}\n")
            append("poercentosg=${config.porcentosg}\n")
            append("GERENCIAMENTO_DE_RISCO_SEMANAL=${if (config.gerenciamentoDeRiscoSemanal) "1" else "0"}\n")
            append("PORCENTOO=${config.porcentoo}\n")
            append("PORCENTOSS=${config.porcentoss}\n")
            append("gG=${config.gG}\n")
            append("GMAIL=${if (config.gmail) "1" else "0"}\n")
            append("notific=${if (config.notific) "1" else "0"}\n")
            append("ativar_ou_desativar_venda=${if (config.ativarOuDesativarVenda) "1" else "0"}\n")
            append("ativar_ou_desativar_compra=${if (config.ativarOuDesativarCompra) "1" else "0"}\n")
            append("Modificar_Sl_Para_OxO=${if (config.modificarSlParaOxO) "1" else "0"}\n")
            append("condicao_De_rompimento_c=${if (config.condicaoDeRompimentoC) "1" else "0"}\n")
            append("condicao_De_rompimento_v=${if (config.condicaoDeRompimentoV) "1" else "0"}\n")
            append("hFF=${config.hFF}\n")
            append("mony=${config.mony}\n")
            append("CAMBIO=${config.cambio}\n")
        }

        try {
            // Upload JSON parameter file
            syncRawFile("dados/parametros/${config.mt5IdConta}.json", json, "Portal Fimaster: Sincronização de parâmetros do EA MT5 para ${config.mt5IdConta} (JSON)")
            // Upload .set parameter file
            syncRawFile("dados/parametros/${config.mt5IdConta}.set", setFile, "Portal Fimaster: Sincronização de parâmetros do EA MT5 para ${config.mt5IdConta} (.SET)")
            
            // Also upload a general template file if required for reading
            syncRawFile("dados/parametros/ea_params.txt", setFile, "Portal Fimaster: Sincronização geral de parâmetros do EA MT5")
        } catch (e: Exception) {
            Log.e("SmsGatewayRepository", "Erro ao sincronizar parâmetros do EA no GitHub: ${e.message}")
            throw e
        }
    }

    /**
     * Checks if the sender of the SMS is authorized/official.
     */
    fun isValidSender(sender: String): Boolean {
        if (!configManager.filterOfficialSenders) return true
        
        val normalizedSender = sender.trim().lowercase(Locale.ROOT)
        val allowedSenders = configManager.officialSendersList
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            
        return allowedSenders.contains(normalizedSender)
    }

    /**
     * Core SMS processing loop.
     */
    suspend fun processIncomingSms(sender: String, messageText: String) = withContext(Dispatchers.IO) {
        val cleanSender = SmsParser.cleanPhone(sender)
        val trimmedMsg = messageText.trim()

        // 1. Check for custom MT5 Account Binding SMS Command
        if (trimmedMsg.startsWith("fimaster#", ignoreCase = true)) {
            if (!configManager.smsBindingEnabled) {
                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = sender,
                        messageText = messageText,
                        isMatched = true,
                        extractedData = "{ \"sender_cleaned\": \"$cleanSender\" }",
                        status = "BINDING_DISABLED",
                        details = "Vinculação de conta MT5 por SMS ignorada porque a função está desabilitada."
                    )
                )
                return@withContext
            }
            val mt5Id = trimmedMsg.substring("fimaster#".length).trim()
            val existingUser = userDao.getUserByPhone(cleanSender)
            if (existingUser != null) {
                // Check duplicate MT5 accounts
                val duplicateUser = userDao.getUserByMt5(mt5Id)
                if (duplicateUser != null && duplicateUser.idUsuario != existingUser.idUsuario) {
                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = sender,
                            messageText = messageText,
                            isMatched = true,
                            extractedData = "{ \"mt5_id\": \"$mt5Id\", \"sender_cleaned\": \"$cleanSender\" }",
                            status = "BINDING_FAILED",
                            details = "A conta MT5 $mt5Id já está vinculada ao utilizador ${duplicateUser.idUsuario}."
                        )
                    )
                    return@withContext
                }
                
                val updatedUser = existingUser.copy(
                    mt5Registrado = mt5Id.isNotEmpty(),
                    mt5IdConta = mt5Id,
                    ultimaAtualizacao = getCurrentTimestampIso()
                )
                userDao.insertUser(updatedUser)
                if (configManager.autoSync) {
                    syncUser(updatedUser)
                    buildAndSyncMt5Index()
                    buildAndSyncTelefonesIndex()
                }
                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = sender,
                        messageText = messageText,
                        isMatched = true,
                        extractedData = "{ \"mt5_id\": \"$mt5Id\", \"usuario\": \"${existingUser.idUsuario}\" }",
                        status = "BINDING_SUCCESS",
                        details = "Conta MT5 $mt5Id vinculada com sucesso ao usuário ${existingUser.idUsuario} via SMS."
                    )
                )
            } else {
                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = sender,
                        messageText = messageText,
                        isMatched = true,
                        extractedData = "{ \"mt5_id\": \"$mt5Id\", \"sender_cleaned\": \"$cleanSender\" }",
                        status = "BINDING_USER_NOT_FOUND",
                        details = "Tentativa de vinculação da conta MT5 $mt5Id falhou: Remetente $cleanSender não está cadastrado."
                    )
                )
            }
            return@withContext
        }

        // 2. Check for custom Discount Code SMS Command
        if (configManager.discountEnabled && trimmedMsg.equals(configManager.discountText, ignoreCase = true)) {
            // Apply discount for this user
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = true,
                    extractedData = "{ \"sender_cleaned\": \"$cleanSender\", \"discount_code\": \"$trimmedMsg\" }",
                    status = "DISCOUNT_APPLIED",
                    details = "Código de desconto '$trimmedMsg' ativado com sucesso para o utilizador $cleanSender."
                )
            )
            return@withContext
        }

        if (!isValidSender(sender)) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = false,
                    extractedData = null,
                    status = "BLOCKED_SENDER",
                    details = "Remetente '$sender' não autorizado. Apenas mensagens de remetentes autorizados (ex: M-Pesa, e-Mola) são processadas para evitar fraudes."
                )
            )
            return@withContext
        }

        val normalizedMsg = SmsParser.normalizeText(messageText)
        val isEmola = normalizedMsg.contains("id da transacao", ignoreCase = true) || normalizedMsg.contains("saldo da tua conta", ignoreCase = true)
        val isMpesa = normalizedMsg.contains("m-pesa", ignoreCase = true) || normalizedMsg.contains("confirmado", ignoreCase = true)
        val matchesRules = isEmola || isMpesa

        if (!matchesRules) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = false,
                    extractedData = null,
                    status = "FILTERED",
                    details = "A mensagem não atende aos critérios do E-mola (ID da transação / saldo) ou do M-pesa (Confirmado / novo saldo M-Pesa)."
                )
            )
            return@withContext
        }

        val extracted = SmsParser.parseMessage(messageText, configManager.customRegex)
        if (extracted == null) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = true,
                    extractedData = null,
                    status = "FAILED_PARSING",
                    details = "Palavras-chave encontradas, mas a extração por regex falhou."
                )
            )
            return@withContext
        }

        // 1. Bloqueio de Transações Duplicadas
        val isDuplicateUserTx = userDao.getUserByTransactionId(extracted.idTransacao) != null
        val isDuplicatePendingTx = pendingPaymentDao.getAllPending().first().any { 
            it.transacoes.split(",").contains(extracted.idTransacao) 
        }
        val isDuplicateRefundTx = refundDao.getRefundByTransactionId(extracted.idTransacao) != null

        if (isDuplicateUserTx || isDuplicatePendingTx || isDuplicateRefundTx) {
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = true,
                    extractedData = null,
                    status = "DUPLICATED",
                    details = "Transação ${extracted.idTransacao} rejeitada por duplicidade."
                )
            )
            return@withContext
        }

        val customerPhone = if (extracted.conta.isNotEmpty()) extracted.conta else SmsParser.cleanPhone(sender)

        // 2. Identificação automática da Licença comprada com base no valor recebido
        val matchedTier = findMatchingLicenseTier(extracted.valorRecebido, customerPhone)

        if (matchedTier != null) {
            val tierName = matchedTier.nome
            val tierDays = matchedTier.diasValidade
            val produtoName = getFormattedProduto(tierName)
            val planoName = getFormattedPlano(tierName)
            val desc = buildLicenseDescription(tierName, tierDays)
            val nowDateTime = getCurrentDateTimeStr()

            // 2.1 Verificar se o usuário já existe (Renovação Automática)
            val existingUser = userDao.getUserByPhone(customerPhone)
            if (existingUser != null) {
                val todayStr = getCurrentDateStr()
                val isCurrentlyValid = existingUser.licencaAtiva && existingUser.licencaValidade.isNotEmpty() && !isDateExpired(existingUser.licencaValidade, todayStr)
                val extendedValidade = if (isCurrentlyValid) {
                    extendExpiryDateByDays(existingUser.licencaValidade, tierDays)
                } else {
                    calculateExpiryDateFromDays(tierDays)
                }

                // Histórico de renovações
                val currentHistoryJson = try {
                    org.json.JSONArray(existingUser.historicoRenovacoes)
                } catch (e: Exception) {
                    org.json.JSONArray()
                }
                val newRenovation = org.json.JSONObject().apply {
                    put("data", nowDateTime)
                    put("valor", extracted.valorRecebido)
                    put("descricao", desc)
                }
                currentHistoryJson.put(newRenovation)

                val updatedUser = existingUser.copy(
                    status = "ATIVO",
                    licencaAtiva = true,
                    licencaProduto = produtoName,
                    licencaPlano = planoName,
                    licencaValidade = extendedValidade,
                    idTransacao = extracted.idTransacao,
                    saldo = extracted.valorRecebido,
                    ultimaRenovacao = nowDateTime,
                    totalRenovacoes = existingUser.totalRenovacoes + 1,
                    historicoRenovacoes = currentHistoryJson.toString(),
                    creditoGuardado = existingUser.creditoGuardado + extracted.valorRecebido,
                    ultimaAtualizacao = getCurrentTimestampIso(),
                    syncStatus = "PENDING"
                )
                userDao.insertUser(updatedUser)

                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = sender,
                        messageText = messageText,
                        isMatched = true,
                        extractedData = "{ \"produto\": \"$produtoName\", \"plano\": \"$planoName\", \"valor_recebido\": ${extracted.valorRecebido}, \"dias_validade\": $tierDays, \"nova_validade\": \"$extendedValidade\", \"id_transacao\": \"${extracted.idTransacao}\" }",
                        status = "RENOVADO",
                        details = "Usuário ${existingUser.idUsuario} renovou licença $produtoName por ${extracted.valorRecebido} MT (+${tierDays} dias até $extendedValidade). Ref: ${extracted.idTransacao}."
                    )
                )

                if (configManager.autoSendSms) {
                    val formattedValidade = formatDateToPt(extendedValidade)
                    sendCustomSms(
                        customerPhone,
                        "Fimaster\n\nPagamento confirmado!\nSua licença $planoName ($produtoName) foi renovada com sucesso.\n\nNova validade: $formattedValidade ($tierDays dias)\nRef: ${extracted.idTransacao}\n\nObrigado por continuar utilizando a Fimaster."
                    )
                }

                if (configManager.autoSync) {
                    syncUser(updatedUser)
                    buildAndSyncMt5Index()
                    buildAndSyncTelefonesIndex()
                    buildAndSyncEaLicense(updatedUser)
                }
                return@withContext
            }

            // 2.2 Usuário Novo: Criar registro completo com licença ativa
            val rawPlainPassword = SecurityUtils.generateRandomPassword()
            val userSalt = SecurityUtils.generateSalt()
            val hashedPasswordWithSalt = SecurityUtils.hashSha256(rawPlainPassword, userSalt) + ":" + userSalt
            
            val count = userDao.getUserCountFlow().first()
            val idUsuario = "USR%06d".format(count + 1)
            val currentDateStr = getCurrentTimestampIso()
            val expiryDateStr = calculateExpiryDateFromDays(tierDays)

            val initialHistoryJson = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("data", nowDateTime)
                    put("valor", extracted.valorRecebido)
                    put("descricao", desc)
                })
            }

            val newUser = UserEntity(
                idUsuario = idUsuario,
                status = "ATIVO",
                origem = "sms_fimaster",
                telefone = customerPhone,
                nome = extracted.nome,
                idTransacao = extracted.idTransacao,
                saldo = extracted.valorRecebido,
                senhaHash = hashedPasswordWithSalt,
                salt = userSalt,
                tokenRecuperacao = "REC-${SecurityUtils.generateRandomString(4)}-${SecurityUtils.generateRandomString(4)}".uppercase(),
                nivelAutorizacao = "CLIENTE",
                dataRegistro = getCurrentDateStr(),
                ultimaAtualizacao = currentDateStr,
                licencaAtiva = true,
                licencaProduto = produtoName,
                licencaPlano = planoName,
                licencaValidade = expiryDateStr,
                ultimaRenovacao = nowDateTime,
                totalRenovacoes = 1,
                historicoRenovacoes = initialHistoryJson.toString(),
                creditoGuardado = 0.0,
                syncStatus = "PENDING"
            )

            userDao.insertUser(newUser)

            // Limpar pendências anteriores se existirem
            val existingPending = pendingPaymentDao.getPendingByPhone(customerPhone)
            if (existingPending != null) {
                pendingPaymentDao.deletePending(existingPending)
                if (configManager.autoSync) {
                    deleteRawFile("dados/pendentes/${existingPending.idPendente}.json")
                }
            }

            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = true,
                    extractedData = "{ \"produto\": \"$produtoName\", \"plano\": \"$planoName\", \"valor_recebido\": ${extracted.valorRecebido}, \"dias_validade\": $tierDays, \"validade\": \"$expiryDateStr\", \"id_transacao\": \"${extracted.idTransacao}\" }",
                    status = "LICENCA_ATIVADA",
                    details = "Licença $produtoName ativada com sucesso para $customerPhone (${extracted.valorRecebido} MT). Validade: $tierDays dias ($expiryDateStr). Usuário: $idUsuario. Ref: ${extracted.idTransacao}."
                )
            )

            if (configManager.autoSendSms) {
                val formattedValidade = formatDateToPt(expiryDateStr)
                sendCustomSms(
                    customerPhone,
                    "Fimaster\n\nPagamento confirmado!\nSua licença $planoName ($produtoName) foi ativada com sucesso.\n\nUsuário: $idUsuario\nSenha: $rawPlainPassword\nValidade: $formattedValidade ($tierDays dias)\nRef: ${extracted.idTransacao}\n\nAcesse: ${configManager.siteUrl}"
                )
            }

            if (configManager.autoSync) {
                syncUser(newUser)
                buildAndSyncMt5Index()
                buildAndSyncTelefonesIndex()
                buildAndSyncEaLicense(newUser)
            }
        } else {
            // 3. Valor NÃO corresponde a nenhuma licença cadastrada:
            // A licença NÃO deve ser ativada, mas o pagamento deve ser registrado para análise.
            val count = pendingPaymentDao.getPendingCount()
            val idPendente = "ANALISE_%06d".format(count + 1)

            val analysisPending = PendingPaymentEntity(
                idPendente = idPendente,
                telefone = customerPhone,
                nome = extracted.nome,
                valorAcumulado = extracted.valorRecebido,
                valorMinimo = 0.0,
                faltam = 0.0,
                transacoes = extracted.idTransacao,
                status = "AGUARDANDO_ANALISE",
                syncStatus = "PENDING"
            )
            pendingPaymentDao.insertPending(analysisPending)

            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = sender,
                    messageText = messageText,
                    isMatched = true,
                    extractedData = "{ \"valor_recebido\": ${extracted.valorRecebido}, \"id_transacao\": \"${extracted.idTransacao}\" }",
                    status = "VALOR_NAO_CORRESPONDENTE",
                    details = "Pagamento de ${extracted.valorRecebido} MT de $customerPhone (Ref: ${extracted.idTransacao}) não corresponde a nenhuma licença cadastrada (Starter, Pro, Master VIP, Trial). Licença NÃO ativada. Registrado para análise do administrador."
                )
            )

            if (configManager.autoSendSms) {
                sendCustomSms(
                    customerPhone,
                    "Fimaster\n\nPagamento de ${extracted.valorRecebido} MT recebido (Ref: ${extracted.idTransacao}).\n\nO valor recebido não corresponde a nenhum dos planos de licença ativos (Starter, Pro, Master VIP, Trial). O pagamento foi registrado e encaminhado para análise manual da administração."
                )
            }

            if (configManager.autoSync) {
                syncPendingPayment(analysisPending)
            }
        }

        // Reconstruir índices e enviar de forma assíncrona e segura
        if (configManager.autoSync) {
            try {
                buildAndSyncMt5Index()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice MT5: ${e.message}")
            }
            try {
                buildAndSyncTelefonesIndex()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice de Telefones: ${e.message}")
            }
            try {
                syncConfig()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao sincronizar configurações: ${e.message}")
            }
            try {
                syncAudit()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao sincronizar auditoria: ${e.message}")
            }
        }
    }

    /**
     * Outgoing SMS Dispatchers
     */
    suspend fun sendCustomSms(recipient: String, text: String) = withContext(Dispatchers.IO) {
        val trimmedRecipient = recipient.trim()
        val trimmedText = text.trim()

        // 1. Validate recipient phone number
        if (trimmedRecipient.isEmpty() || trimmedRecipient.length < 4) {
            val errorMsg = "Número de destino inválido ou vazio: '$recipient'"
            Log.e(TAG, errorMsg)
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "SmsGateway",
                    messageText = text,
                    isMatched = false,
                    extractedData = null,
                    status = "SMS_ERROR",
                    details = "Erro ao enviar SMS: $errorMsg"
                )
            )
            smsLogDao.insertSmsLog(
                SmsLogEntity(
                    recipient = recipient,
                    messageText = text,
                    type = "OUTGOING_CUSTOM",
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )
            return@withContext
        }

        // 2. Validate message text
        if (trimmedText.isEmpty()) {
            val errorMsg = "Mensagem SMS vazia."
            Log.e(TAG, errorMsg)
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "SmsGateway",
                    messageText = text,
                    isMatched = false,
                    extractedData = null,
                    status = "SMS_ERROR",
                    details = "Erro ao enviar SMS: $errorMsg"
                )
            )
            smsLogDao.insertSmsLog(
                SmsLogEntity(
                    recipient = recipient,
                    messageText = text,
                    type = "OUTGOING_CUSTOM",
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )
            return@withContext
        }

        // 3. Check runtime permission
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val errorMsg = "Permissão SEND_SMS não concedida pelo utilizador."
            Log.e(TAG, errorMsg)
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "SmsGateway",
                    messageText = text,
                    isMatched = false,
                    extractedData = null,
                    status = "SMS_ERROR",
                    details = "Erro ao enviar SMS: $errorMsg. Por favor, ative a permissão de SMS nas configurações do telemóvel."
                )
            )
            smsLogDao.insertSmsLog(
                SmsLogEntity(
                    recipient = recipient,
                    messageText = text,
                    type = "OUTGOING_CUSTOM",
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )
            return@withContext
        }

        try {
            // 4. Correctly initialize SmsManager
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: @Suppress("DEPRECATION") SmsManager.getDefault()

            if (smsManager == null) {
                throw IllegalStateException("SmsManager não inicializado ou indisponível no dispositivo.")
            }

            // 5. Divide and send SMS safely
            val parts = smsManager.divideMessage(trimmedText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(trimmedRecipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(trimmedRecipient, null, trimmedText, null, null)
            }

            Log.d(TAG, "SMS enviado com sucesso para $trimmedRecipient: $trimmedText")
            
            // Insert in SMS Log and Audit Log
            smsLogDao.insertSmsLog(
                SmsLogEntity(
                    recipient = trimmedRecipient,
                    messageText = trimmedText,
                    type = "OUTGOING_CUSTOM",
                    status = "SENT"
                )
            )
            
            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "SmsGateway",
                    messageText = trimmedText,
                    isMatched = true,
                    extractedData = "{ \"destinatario\": \"$trimmedRecipient\" }",
                    status = "SMS_SENT",
                    details = "SMS enviado com sucesso para $trimmedRecipient. [Admin UID: ${getCurrentAdminUid() ?: "Desconhecido"}]"
                )
            )
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            Log.e(TAG, "Falha ao enviar SMS para $trimmedRecipient: $errorMsg")
            
            smsLogDao.insertSmsLog(
                SmsLogEntity(
                    recipient = trimmedRecipient,
                    messageText = trimmedText,
                    type = "OUTGOING_CUSTOM",
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )

            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "SmsGateway",
                    messageText = trimmedText,
                    isMatched = false,
                    extractedData = null,
                    status = "SMS_ERROR",
                    details = "Falha no envio do SMS para $trimmedRecipient: $errorMsg. Verifique o chip ou se há saldo de SMS disponível."
                )
            )
        }
    }

    private suspend fun sendComplementSms(recipient: String, acumulado: Double, minimo: Double, faltam: Double) {
        val text = "Recebemos seu pagamento.\n\nValor acumulado: $acumulado MT\n\nValor necessário: $minimo MT\n\nFaltam: $faltam MT\n\nApós atingir o valor mínimo, o sistema enviará automaticamente os dados de ativação."
        sendCustomSms(recipient, text)
    }

    private suspend fun sendApprovalSms(recipient: String, idUsuario: String, rawPass: String) {
        val text = """
**Fimaster**

✅ Pagamento confirmado com sucesso!

A sua conta foi criada.

**ID do Utilizador:** $idUsuario

**Senha:** $rawPass

Para concluir a ativação da sua licença, acesse o site abaixo e registre a sua conta MT5:

${configManager.siteUrl}

Guarde a sua senha em um local seguro. Ela será necessária para acessar a sua conta e concluir a ativação.

Obrigado por escolher a Fimaster.
        """.trimIndent()
        sendCustomSms(recipient, text)
    }

    /**
     * Reimbursement Logic
     */
    suspend fun requestRefund(transactionId: String): String = withContext(Dispatchers.IO) {
        // Encontrar usuário com essa transação
        val user = userDao.getUserByTransactionId(transactionId)
            ?: return@withContext "Transação não encontrada nos registros ativos."

        // Verificar se já possui reembolso
        val existingRefund = refundDao.getRefundByTransactionId(transactionId)
        if (existingRefund != null) {
            return@withContext "Já existe uma solicitação de reembolso para esta transação. Status: ${existingRefund.status}."
        }

        // Verificar prazo limite de reembolso
        val maxDays = configManager.maxRefundDays
        val daysElapsed = getDaysDifference(user.dataRegistro)
        if (daysElapsed > maxDays) {
            val rejectionSms = """
**Fimaster**

❌ Solicitação de reembolso recusada.

O prazo limite de $maxDays dias para solicitar o reembolso de sua licença expirou (tempo decorrido: $daysElapsed dias).

Se você tiver alguma dúvida, entre em contato com o suporte técnico.
            """.trimIndent()
            sendCustomSms(user.telefone, rejectionSms)

            auditLogDao.insertLog(
                AuditLogEntity(
                    sender = user.telefone,
                    messageText = "Solicitação de reembolso recusada por ultrapassar limite de prazo.",
                    isMatched = true,
                    extractedData = null,
                    status = "REFUND_REJECTED_TIMEOUT",
                    details = "Solicitação rejeitada para usuário ${user.idUsuario}. Limite: $maxDays dias, decorrido: $daysElapsed dias."
                )
            )

            return@withContext "Prazo de reembolso expirado. Limite: $maxDays dias, decorrido: $daysElapsed dias. Solicitação recusada."
        }

        val count = refundDao.getRefundCount()
        val idRefund = "REF%06d".format(count + 1)
        val refund = RefundEntity(
            idReembolso = idRefund,
            idUsuario = user.idUsuario,
            idTransacao = transactionId,
            valor = user.saldo, // Utilizar saldo ou valor da transação
            status = "AGUARDANDO_APROVACAO",
            dataSolicitacao = getCurrentTimestampIso(),
            dataAprovacao = "",
            dataPagamento = "",
            syncStatus = "PENDING"
        )
        refundDao.insertRefund(refund)

        // Atualizar status no usuário
        val updatedUser = user.copy(reembolsoSolicitado = true, reembolsoStatus = "AGUARDANDO_APROVACAO")
        userDao.insertUser(updatedUser)

        // Enviar SMS informando recebimento dentro do prazo
        val approvalSms = """
**Fimaster**

✅ Solicitação de reembolso enviada com sucesso!

O seu pedido (ID: $idRefund) foi recebido dentro do prazo limite e está aguardando aprovação.

Você receberá uma nova notificação assim que o reembolso for processado.
        """.trimIndent()
        sendCustomSms(user.telefone, approvalSms)

        auditLogDao.insertLog(
            AuditLogEntity(
                sender = user.telefone,
                messageText = "Solicitação de reembolso manual para transação $transactionId",
                isMatched = true,
                extractedData = null,
                status = "REFUND_REQUESTED",
                details = "Solicitação $idRefund gerada para usuário ${user.idUsuario} de ${user.saldo} MT. Dentro do prazo ($daysElapsed/$maxDays dias)."
            )
        )

        if (configManager.autoSync) {
            syncRefund(refund)
            syncUser(updatedUser)
        }

        return@withContext "Solicitação de reembolso $idRefund criada com sucesso! Status: AGUARDANDO_APROVACAO."
    }

    suspend fun approveRefund(refundId: String, adminUser: String) = withContext(Dispatchers.IO) {
        val refund = refundDao.getRefundById(refundId) ?: return@withContext false
        val user = userDao.getUserById(refund.idUsuario)

        val updatedRefund = refund.copy(
            status = "AGUARDANDO_PAGAMENTO",
            dataAprovacao = getCurrentTimestampIso(),
            syncStatus = "PENDING"
        )
        refundDao.insertRefund(updatedRefund)

        if (user != null) {
            userDao.insertUser(user.copy(reembolsoStatus = "AGUARDANDO_PAGAMENTO"))
        }

        auditLogDao.insertLog(
            AuditLogEntity(
                sender = "SISTEMA",
                messageText = "Aprovação do reembolso $refundId",
                isMatched = true,
                extractedData = null,
                status = "REFUND_APPROVED",
                details = "Aprovado por $adminUser. Status alterado para AGUARDANDO_PAGAMENTO. [Admin UID: ${getCurrentAdminUid() ?: "Desconhecido"}]"
            )
        )

        if (configManager.autoSync) {
            syncRefund(updatedRefund)
            if (user != null) {
                syncUser(user.copy(reembolsoStatus = "AGUARDANDO_PAGAMENTO"))
            }
        }
        true
    }

    suspend fun confirmRefundPaid(refundId: String) = withContext(Dispatchers.IO) {
        val refund = refundDao.getRefundById(refundId) ?: return@withContext false
        val user = userDao.getUserById(refund.idUsuario)

        val updatedRefund = refund.copy(
            status = "PAGO",
            dataPagamento = getCurrentTimestampIso(),
            syncStatus = "PENDING"
        )
        refundDao.insertRefund(updatedRefund)

        if (user != null) {
            // Desativar licença ao reembolsar
            userDao.insertUser(user.copy(
                reembolsoStatus = "PAGO",
                licencaAtiva = false,
                status = "DESATIVADO",
                ultimaAtualizacao = getCurrentTimestampIso()
            ))
        }

        auditLogDao.insertLog(
            AuditLogEntity(
                sender = "SISTEMA",
                messageText = "Pagamento confirmado do reembolso $refundId",
                isMatched = true,
                extractedData = null,
                status = "REFUND_PAID",
                details = "Pagamento manual confirmado. Status alterado para PAGO. Licença desativada. [Admin UID: ${getCurrentAdminUid() ?: "Desconhecido"}]"
            )
        )

        if (configManager.autoSync) {
            syncRefund(updatedRefund)
            if (user != null) {
                syncUser(user.copy(
                    reembolsoStatus = "PAGO",
                    licencaAtiva = false,
                    status = "DESATIVADO",
                    ultimaAtualizacao = getCurrentTimestampIso()
                ))
            }
            try {
                buildAndSyncMt5Index()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice MT5 no reembolso: ${e.message}")
            }
            try {
                buildAndSyncTelefonesIndex()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice de Telefones no reembolso: ${e.message}")
            }
        }
        true
    }

    suspend fun rejectRefund(refundId: String) = withContext(Dispatchers.IO) {
        val refund = refundDao.getRefundById(refundId) ?: return@withContext false
        val user = userDao.getUserById(refund.idUsuario)

        val updatedRefund = refund.copy(
            status = "REJEITADO",
            syncStatus = "PENDING"
        )
        refundDao.insertRefund(updatedRefund)

        if (user != null) {
            userDao.insertUser(user.copy(
                reembolsoSolicitado = false,
                reembolsoStatus = "REJEITADO"
            ))
        }

        auditLogDao.insertLog(
            AuditLogEntity(
                sender = "SISTEMA",
                messageText = "Rejeição do reembolso $refundId",
                isMatched = true,
                extractedData = null,
                status = "REFUND_REJECTED",
                details = "Reembolso rejeitado pelo administrador. [Admin UID: ${getCurrentAdminUid() ?: "Desconhecido"}]"
            )
        )

        if (configManager.autoSync) {
            syncRefund(updatedRefund)
            if (user != null) {
                syncUser(user.copy(
                    reembolsoSolicitado = false,
                    reembolsoStatus = "REJEITADO"
                ))
            }
        }
        true
    }

    /**
     * Raw File Upload engine supporting both GitHub and FastAPI.
     */
    /**
     * Raw File Upload engine supporting both GitHub and FastAPI.
     */
    suspend fun syncRawFile(pathInRepo: String, fileContent: String, commitMsg: String) = withContext(Dispatchers.IO) {
        val mode = configManager.syncMode
        val startTime = System.currentTimeMillis()

        if (mode == ConfigManager.MODE_GITHUB) {
            val token = configManager.githubToken.trim()
            val repo = configManager.githubRepo.trim()
            val branch = configManager.githubBranch.trim().ifEmpty { "main" }

            val maskedToken = if (token.length > 6) {
                token.take(4) + "..." + token.takeLast(4)
            } else {
                "**********"
            }

            if (token.isEmpty() || repo.isEmpty()) {
                val errorMsg = "Configurações do GitHub incompletas (Token ou Repositório vazio)."
                Log.w(TAG, errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            val ownerRepo = repo.split("/")
            if (ownerRepo.size != 2) {
                val errorMsg = "Formato de repositório inválido: '$repo'. Deve ser 'usuario/repositorio'."
                Log.w(TAG, errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            val owner = ownerRepo[0].trim()
            val repoName = ownerRepo[1].trim()

            val gitHubService = getGitHubService()
            var existingSha: String? = null

            // 1. Procurar o arquivo no GitHub para obter o SHA se existir
            val getUrl = "https://api.github.com/repos/$owner/$repoName/contents/$pathInRepo?ref=$branch"
            val getHeadersDesc = """
                Authorization: Bearer $maskedToken
                Accept: application/vnd.github+json
                User-Agent: SMS-Gateway-Pro
                X-GitHub-Api-Version: 2022-11-28
            """.trimIndent()

            Log.d(TAG, "GET para consultar SHA de $pathInRepo...")
            try {
                val getResponse = gitHubService.getFileMetadata(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    filePath = pathInRepo,
                    ref = branch
                )

                if (getResponse.isSuccessful) {
                    existingSha = getResponse.body()?.sha
                    Log.d(TAG, "Arquivo encontrado no GitHub. SHA atual obtido: $existingSha")
                } else {
                    val getCode = getResponse.code()
                    if (getCode == 404) {
                        Log.d(TAG, "Arquivo não existe no GitHub (será criado).")
                    } else {
                        val errorBody = getResponse.errorBody()?.string() ?: ""
                        val exceptionMsg = "Erro ao consultar SHA do arquivo (Código $getCode): $errorBody"
                        
                        // Log to Audit Log
                        val diagnosticDetails = buildString {
                            append("DIAGNÓSTICO DE FALHA (GET SHA)\n")
                            append("Método: GET\n")
                            append("URL: $getUrl\n")
                            append("Headers:\n$getHeadersDesc\n")
                            append("Código HTTP: $getCode\n")
                            append("Mensagem do Servidor: $errorBody\n\n")
                            append("Causa Provável: ")
                            when (getCode) {
                                401 -> append("Token inválido ou expirado.")
                                403 -> append("Limite de requisições excedido ou falta de permissões.")
                                404 -> append("Repositório ou branch '$branch' não encontrado.")
                                else -> append("Falha na API do GitHub.")
                            }
                            append("\nSolução Recomendada: Verifique as credenciais, o nome do repositório/branch e tente novamente.")
                        }

                        auditLogDao.insertLog(
                            AuditLogEntity(
                                sender = "GitHubSync",
                                messageText = commitMsg,
                                isMatched = false,
                                extractedData = "{ \"url\": \"$getUrl\", \"http_code\": $getCode }",
                                status = "ERROR",
                                details = diagnosticDetails
                            )
                        )
                        throw Exception(exceptionMsg)
                    }
                }
            } catch (e: Exception) {
                if (e !is java.lang.Exception && e !is kotlin.Exception) throw e
                if (e.message?.contains("Código 404") == true || e.message?.contains("404 Not Found") == true) {
                    // Ignorar
                } else if (e is retrofit2.HttpException && e.code() == 404) {
                    // Ignorar
                } else {
                    // Propagar erros reais que não sejam 404 (arquivo inexistente)
                    Log.e(TAG, "Erro ao obter SHA: ${e.message}")
                    throw e
                }
            }

            // 2. Criar ou Atualizar utilizando PUT
            val putUrl = "https://api.github.com/repos/$owner/$repoName/contents/$pathInRepo"
            try {
                val base64 = Base64.encodeToString(fileContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val body = GitHubPutBody(
                    message = commitMsg,
                    content = base64,
                    branch = branch,
                    sha = existingSha
                )

                val putHeadersDesc = """
                    Authorization: Bearer $maskedToken
                    Accept: application/vnd.github+json
                    User-Agent: SMS-Gateway-Pro
                    X-GitHub-Api-Version: 2022-11-28
                    Content-Type: application/json
                """.trimIndent()

                val bodyDesc = """
                    {
                      "message": "$commitMsg",
                      "branch": "$branch",
                      "sha": ${if (existingSha != null) "\"$existingSha\"" else "null"}
                    }
                """.trimIndent()

                Log.d(TAG, "Enviando PUT para criar/atualizar arquivo...")
                val putResponse = gitHubService.createOrUpdateFile(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    filePath = pathInRepo,
                    body = body
                )

                val duration = System.currentTimeMillis() - startTime
                val putCode = putResponse.code()
                val putBodyString = if (putResponse.isSuccessful) {
                    putResponse.body()?.toString() ?: "Sucesso (Corpo vazio)"
                } else {
                    putResponse.errorBody()?.string() ?: ""
                }

                if (putResponse.isSuccessful) {
                    Log.d(TAG, "Sincronização com GitHub de $pathInRepo bem-sucedida! SHA gerado: ${putResponse.body()?.content?.sha}")
                    
                    // Registro de Sucesso no Painel de Auditoria
                    val successDetails = buildString {
                        append("Sincronização com GitHub Concluída com Sucesso!\n\n")
                        append("Método: PUT\n")
                        append("URL: $putUrl\n")
                        append("Tempo de Operação: ${duration}ms\n")
                        append("Código HTTP: $putCode\n")
                        append("Caminho do Arquivo: $pathInRepo\n")
                        append("Branch: $branch\n")
                        append("SHA do Arquivo: ${putResponse.body()?.content?.sha ?: "Criado novo"}\n\n")
                        append("Headers Enviados:\n$putHeadersDesc\n\n")
                        append("Corpo Enviado:\n$bodyDesc\n\n")
                        append("Resposta Recebida:\n$putBodyString")
                    }

                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "GitHubSync",
                            messageText = commitMsg,
                            isMatched = true,
                            extractedData = "{ \"url\": \"$putUrl\", \"http_code\": $putCode, \"sha\": \"${putResponse.body()?.content?.sha}\", \"duration_ms\": $duration }",
                            status = "SUCCESS",
                            details = successDetails
                        )
                    )
                } else {
                    // Tratar erro HTTP
                    val causeAndSolution = when (putCode) {
                        401 -> Pair(
                            "Token do GitHub inválido, expirado ou com escopos insuficientes.",
                            "Gerar um novo Personal Access Token (classic) com a permissão 'repo' ativa e atualizar nas configurações do aplicativo."
                        )
                        403 -> Pair(
                            "Limite de requisições excedido ou falta de permissão de escrita para este token no repositório especificado.",
                            "Verifique se o token tem permissões de escrita adequadas no repositório ou aguarde o reset da taxa horária do GitHub."
                        )
                        404 -> Pair(
                            "Repositório não encontrado, ramificação (branch) incorreta ou caminho inválido.",
                            "Confirme se o nome do repositório está exatamente no formato 'usuario/repositorio', se a branch '$branch' existe no repositório remoto, e se o token tem acesso para visualizá-lo."
                        )
                        409 -> Pair(
                            "Conflito de SHA na atualização. O arquivo foi modificado por outro processo antes de enviarmos o nosso PUT.",
                            "Aguarde a próxima tentativa automática. O aplicativo obterá o novo SHA atualizado e reenviará os dados da fila local."
                        )
                        else -> Pair(
                            "Erro interno no servidor do GitHub ou parâmetros incorretos na requisição.",
                            "Aguarde alguns minutos e tente novamente. O aplicativo salvou os dados localmente no banco de dados SQLite (Room) e tentará reenviar automaticamente na próxima verificação de rede."
                        )
                    }

                    val diagnosticDetails = buildString {
                        append("DIAGNÓSTICO DE FALHA DE UPLOAD (PUT)\n")
                        append("Método: PUT\n")
                        append("URL: $putUrl\n")
                        append("Caminho do Arquivo: $pathInRepo\n")
                        append("Branch: $branch\n")
                        append("Código HTTP: $putCode\n")
                        append("Tempo de Operação: ${duration}ms\n\n")
                        append("Headers Enviados:\n$putHeadersDesc\n\n")
                        append("Corpo Enviado:\n$bodyDesc\n\n")
                        append("Resposta do GitHub:\n$putBodyString\n\n")
                        append("Causa Provável:\n${causeAndSolution.first}\n\n")
                        append("Solução Recomendada:\n${causeAndSolution.second}")
                    }

                    Log.e(TAG, "Falha no upload para o GitHub (Código $putCode): $putBodyString")

                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "GitHubSync",
                            messageText = commitMsg,
                            isMatched = false,
                            extractedData = "{ \"url\": \"$putUrl\", \"http_code\": $putCode, \"duration_ms\": $duration }",
                            status = "ERROR",
                            details = diagnosticDetails
                        )
                    )
                    throw Exception("GitHub API erro $putCode: $putBodyString")
                }
            } catch (e: Exception) {
                if (e !is java.lang.Exception && e !is kotlin.Exception) throw e
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Exceção inesperada no upload para o GitHub: ${e.message}")

                val diagnosticDetails = buildString {
                    append("DIAGNÓSTICO DE FALHA (EXCEÇÃO DE CONEXÃO)\n")
                    append("Método: PUT\n")
                    append("URL: $putUrl\n")
                    append("Caminho do Arquivo: $pathInRepo\n")
                    append("Tempo de Operação: ${duration}ms\n\n")
                    append("Tipo da Exceção: ${e.javaClass.simpleName}\n")
                    append("Mensagem de Erro: ${e.message ?: e.toString()}\n\n")
                    append("Causa Provável:\nSem conexão com a Internet, rede restrita ou bloqueio por firewall.\n\n")
                    append("Solução Recomendada:\nVerifique a conexão de rede do dispositivo. O aplicativo salvou o arquivo localmente no banco de dados SQLite (Room) e reenviará de forma automática assim que a rede se reestabelecer.")
                }

                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = "GitHubSync",
                        messageText = commitMsg,
                        isMatched = false,
                        extractedData = "{ \"url\": \"$putUrl\", \"exception\": \"${e.javaClass.simpleName}\", \"duration_ms\": $duration }",
                        status = "ERROR",
                        details = diagnosticDetails
                    )
                )
                throw e
            }
        } else if (mode == ConfigManager.MODE_FIREBASE) {
            // Firebase Sync Mode (Realtime Database & Firestore)
            var rtdbSuccess = false
            var firestoreSuccess = false
            var rtdbErrorMsg = ""
            var firestoreErrorMsg = ""

            try {
                initFirebaseIfNeeded()

                val dbRefPath = pathInRepo.removeSuffix(".json").removeSuffix(".set").removeSuffix(".txt")
                val parsedObj = try {
                    org.json.JSONObject(fileContent)
                } catch (jsonEx: Throwable) {
                    null
                }

                // 1. Realtime Database
                try {
                    val rtdb = getFirebaseDatabaseInstance()
                    val ref = rtdb.getReference(dbRefPath)

                    if (parsedObj != null) {
                        val map = jsonToMap(parsedObj)
                        ref.setValue(map).await()
                    } else {
                        ref.setValue(fileContent).await()
                    }
                    rtdbSuccess = true
                    Log.d(TAG, "Sincronização com Firebase Realtime Database para $dbRefPath concluída com sucesso.")
                } catch (e: Throwable) {
                    rtdbErrorMsg = e.message ?: e.toString()
                    Log.e(TAG, "Falha na sincronização do Realtime Database: $rtdbErrorMsg")
                }

                // 2. Firestore
                try {
                    val colDoc = getFirestoreCollectionAndDoc(pathInRepo)
                    if (colDoc != null) {
                        val (col, doc) = colDoc
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val docRef = firestore.collection(col).document(doc)
                        
                        val data = if (parsedObj != null) {
                            jsonToMap(parsedObj)
                        } else {
                            mapOf("content" to fileContent)
                        }
                        docRef.set(data).await()
                        firestoreSuccess = true
                        Log.d(TAG, "Sincronização com Firebase Firestore para $col/$doc concluída com sucesso.")
                    } else {
                        firestoreErrorMsg = "Não foi possível extrair coleção e documento para o caminho: $pathInRepo"
                    }
                } catch (e: Throwable) {
                    firestoreErrorMsg = e.message ?: e.toString()
                    Log.e(TAG, "Falha na sincronização do Firestore: $firestoreErrorMsg")
                }

                val duration = System.currentTimeMillis() - startTime

                if (rtdbSuccess || firestoreSuccess) {
                    val details = buildString {
                        append("Sincronização via Firebase concluída em ${duration}ms.\n")
                        append("Realtime Database: ${if (rtdbSuccess) "SUCESSO" else "FALHA ($rtdbErrorMsg)"}\n")
                        append("Firestore: ${if (firestoreSuccess) "SUCESSO" else "FALHA ($firestoreErrorMsg)"}")
                    }
                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "FirebaseSync",
                            messageText = commitMsg,
                            isMatched = true,
                            extractedData = "{ \"path\": \"$pathInRepo\", \"rtdb_success\": $rtdbSuccess, \"firestore_success\": $firestoreSuccess, \"duration_ms\": $duration }",
                            status = "SUCCESS",
                            details = details
                        )
                    )
                } else {
                    var permissionHint = ""
                    if (rtdbErrorMsg.contains("Permission denied", ignoreCase = true) || firestoreErrorMsg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                        permissionHint = "\n\n💡 CAUSA PROVÁVEL: As Regras de Segurança (Security Rules) do Firebase Console estão a negar gravação sem permissão.\nSOLUÇÃO: No Firebase Console (console.firebase.com):\n1. Acesse Realtime Database > Regras e configure \".read\": true, \".write\": true (ou com auth != null)\n2. Acesse Firestore Database > Regras e defina 'allow read, write: if true;' (ou se autenticado)\n3. Em Authentication > Sign-in method, ative a opção 'Anônimo'."
                    }
                    val errorMsg = "Ambos os bancos de dados do Firebase falharam.\nRTDB: $rtdbErrorMsg\nFirestore: $firestoreErrorMsg$permissionHint"
                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "FirebaseSync",
                            messageText = commitMsg,
                            isMatched = false,
                            extractedData = "{ \"path\": \"$pathInRepo\", \"rtdb_error\": \"$rtdbErrorMsg\", \"firestore_error\": \"$firestoreErrorMsg\", \"duration_ms\": $duration }",
                            status = "ERROR",
                            details = errorMsg
                        )
                    )
                    throw Exception(errorMsg)
                }

            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Falha geral na sincronização com Firebase: ${e.message}")
                if (e.message?.contains("Ambos os bancos de dados do Firebase falharam") != true) {
                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "FirebaseSync",
                            messageText = commitMsg,
                            isMatched = false,
                            extractedData = "{ \"path\": \"$pathInRepo\", \"exception\": \"${e.javaClass.simpleName}\", \"duration_ms\": $duration }",
                            status = "ERROR",
                            details = "Erro de sincronização Firebase em $pathInRepo: ${e.message ?: e.toString()}. Fila mantida localmente."
                        )
                    )
                }
                if (e is Exception) throw e else throw Exception(e)
            }
        } else {
            // FastAPI Sync Mode
            val fastApiUrl = configManager.fastApiUrl.trim()
            val token = configManager.fastApiToken.trim()
            if (fastApiUrl.isEmpty()) {
                throw IllegalArgumentException("URL da FastAPI não configurada.")
            }

            try {
                val apiService = getFastApiService(fastApiUrl)
                val authHeader = if (token.isNotEmpty()) "Bearer $token" else null
                
                val bodyMap = mapOf(
                    "path" to pathInRepo,
                    "content" to fileContent,
                    "timestamp" to System.currentTimeMillis()
                )

                val response = apiService.uploadRawJson(
                    url = pathInRepo,
                    authorization = authHeader,
                    body = bodyMap
                )

                val duration = System.currentTimeMillis() - startTime
                val code = response.code()

                if (response.isSuccessful) {
                    Log.d(TAG, "Sincronização com FastAPI concluída com sucesso!")
                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "FastApiSync",
                            messageText = commitMsg,
                            isMatched = true,
                            extractedData = "{ \"url\": \"$fastApiUrl/$pathInRepo\", \"http_code\": $code, \"duration_ms\": $duration }",
                            status = "SUCCESS",
                            details = "Upload concluído via FastAPI em ${duration}ms. Código HTTP: $code."
                        )
                    )
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val errorMsg = "FastAPI respondeu com código $code: $errorBody"
                    Log.e(TAG, errorMsg)

                    auditLogDao.insertLog(
                        AuditLogEntity(
                            sender = "FastApiSync",
                            messageText = commitMsg,
                            isMatched = false,
                            extractedData = "{ \"url\": \"$fastApiUrl/$pathInRepo\", \"http_code\": $code, \"duration_ms\": $duration }",
                            status = "ERROR",
                            details = "Falha na FastAPI:\nURL: $fastApiUrl/$pathInRepo\nCódigo HTTP: $code\nErro: $errorBody"
                        )
                    )
                    throw Exception(errorMsg)
                }
            } catch (e: Exception) {
                if (e !is java.lang.Exception && e !is kotlin.Exception) throw e
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "Falha na conexão com FastAPI: ${e.message}")
                auditLogDao.insertLog(
                    AuditLogEntity(
                        sender = "FastApiSync",
                        messageText = commitMsg,
                        isMatched = false,
                        extractedData = "{ \"url\": \"$fastApiUrl/$pathInRepo\", \"exception\": \"${e.javaClass.simpleName}\" }",
                        status = "ERROR",
                        details = "Falha ao conectar na FastAPI: ${e.message}. O registro foi mantido na fila local (SQLite) para reenvio automático."
                    )
                )
                throw e
            }
        }
    }

    private suspend fun deleteRawFile(pathInRepo: String) {
        // Safe delete placeholder
        Log.d(TAG, "Removendo arquivo pendente do repositório: $pathInRepo")
    }

    /**
     * Formats entity states into exactly matching JSON payloads.
     */
    suspend fun syncUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.updateUser(user.copy(syncStatus = "SYNCING", syncAttempts = user.syncAttempts + 1))
        
        val mode = configManager.syncMode
        var isSuccess = false
        var exceptionMsg: String? = null

        val reembolsoJson = if (user.reembolsoSolicitado) {
            val refund = refundDao.getRefundByTransactionId(user.idTransacao)
            val refundId = refund?.idReembolso ?: "REF000001"
            val refundVal = refund?.valor ?: user.saldo
            val refundStatus = user.reembolsoStatus
            if (refundStatus == "PAGO") {
                """{
      "solicitado": true,
      "status": "PAGO",
      "id_reembolso": "$refundId",
      "valor": $refundVal,
      "data_pagamento": "${refund?.dataPagamento ?: user.ultimaAtualizacao}"
    }"""
            } else {
                """{
      "solicitado": true,
      "status": "$refundStatus",
      "id_reembolso": "$refundId",
      "valor": $refundVal
    }"""
            }
        } else {
            """{
      "solicitado": false,
      "status": "NENHUM",
      "id_reembolso": "",
      "data_solicitacao": "",
      "data_aprovacao": "",
      "data_pagamento": ""
    }"""
        }

        val userJsonString = """
{
  "${user.idUsuario}": {
    "senha_hash": "${user.senhaHash}",
    "validade": "${user.licencaValidade}",
    "numero": "${user.telefone}",
    "nome": "${user.nome}",
    "origem": "${user.origem}",
    "status": "${user.status}",
    "data_registro": "${user.dataRegistro}",
    "ultima_atualizacao": "${user.ultimaAtualizacao}",
    "id_transacao": "${user.idTransacao}",
    "saldo": ${user.saldo},
    "salt": "${user.salt}",
    "token_recuperacao": "${user.tokenRecuperacao}",
    "nivel_autorizacao": "${user.nivelAutorizacao}",
    "mt5": {
      "registrado": ${user.mt5Registrado},
      "id_conta": "${user.mt5IdConta}"
    },
    "licenca": {
      "ativa": ${user.licencaAtiva},
      "produto": "${user.licencaProduto}",
      "plano": "${user.licencaPlano}",
      "validade": "${user.licencaValidade}",
      "ultima_renovacao": "${user.ultimaRenovacao}",
      "total_renovacoes": ${user.totalRenovacoes},
      "historico": ${user.historicoRenovacoes}
    },
    "reembolso": $reembolsoJson,
    "auditoria": {
      "ultimo_login": "${user.ultimaAtualizacao}",
      "ultimo_dispositivo": "${user.deviceId}",
      "tentativas_login": 0
    },
    "autorizacao": {
      "status": "${user.autorizacaoStatus}",
      "aprovado_por": "${user.autorizacaoAprovadoPor}",
      "data_aprovacao": "${user.autorizacaoDataAprovacao}",
      "motivo": ""
    }
  }
}
        """.trimIndent()

        try {
            val path = "dados/usuarios/${user.idUsuario}.json"
            syncRawFile(path, userJsonString, "SMS Gateway: Atualizando usuário ${user.idUsuario}")
            isSuccess = true
        } catch (e: Exception) {
            exceptionMsg = e.message ?: e.toString()
        }

        val updatedUser = if (isSuccess) {
            user.copy(
                syncStatus = "SYNCED",
                lastSyncMessage = "Sincronizado via $mode em ${getCurrentTimestampIso()}"
            )
        } else {
            user.copy(
                syncStatus = "FAILED",
                lastSyncMessage = "Falha: $exceptionMsg"
            )
        }
        userDao.updateUser(updatedUser)

        if (isSuccess) {
            try {
                incrementAndSyncVersion()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar arquivo de versão após sincronização: ${e.message}")
            }
            try {
                buildAndSyncMt5Index()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice MT5 após sincronização do usuário: ${e.message}")
            }
            try {
                buildAndSyncTelefonesIndex()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconstruir índice de telefones após sincronização do usuário: ${e.message}")
            }
        }
    }

    suspend fun syncPendingPayment(pending: PendingPaymentEntity) = withContext(Dispatchers.IO) {
        pendingPaymentDao.updatePending(pending.copy(syncStatus = "SYNCING"))
        var isSuccess = false
        var exceptionMsg: String? = null

        val txListJson = pending.transacoes.split(",")
            .filter { it.isNotEmpty() }
            .joinToString(",") { "\"$it\"" }

        val pendingJsonString = """
{
  "telefone": "${pending.telefone}",
  "nome": "${pending.nome}",
  "valor_acumulado": ${pending.valorAcumulado},
  "valor_minimo": ${pending.valorMinimo},
  "faltam": ${pending.faltam},
  "transacoes": [$txListJson],
  "status": "${pending.status}"
}
        """.trimIndent()

        try {
            val path = "dados/pendentes/${pending.idPendente}.json"
            syncRawFile(path, pendingJsonString, "SMS Gateway: Atualizando acumulador ${pending.idPendente}")
            isSuccess = true
        } catch (e: Exception) {
            exceptionMsg = e.message ?: e.toString()
        }

        val updatedPending = if (isSuccess) {
            pending.copy(
                syncStatus = "SYNCED",
                lastSyncMessage = "Sincronizado em ${getCurrentTimestampIso()}"
            )
        } else {
            pending.copy(
                syncStatus = "FAILED",
                lastSyncMessage = "Falha: $exceptionMsg"
            )
        }
        pendingPaymentDao.insertPending(updatedPending)
    }

    suspend fun syncRefund(refund: RefundEntity) = withContext(Dispatchers.IO) {
        refundDao.insertRefund(refund.copy(syncStatus = "SYNCING"))
        var isSuccess = false
        var exceptionMsg: String? = null

        val refundJsonString = """
{
  "${refund.idReembolso}": {
    "usuario": "${refund.idUsuario}",
    "id_transacao": "${refund.idTransacao}",
    "valor": ${refund.valor},
    "status": "${refund.status}",
    "data_solicitacao": "${refund.dataSolicitacao}",
    "data_aprovacao": "${refund.dataAprovacao}",
    "data_pagamento": "${refund.dataPagamento}"
  }
}
        """.trimIndent()

        try {
            val path = "dados/reembolsos/${refund.idReembolso}.json"
            syncRawFile(path, refundJsonString, "SMS Gateway: Atualizando reembolso ${refund.idReembolso}")
            isSuccess = true
        } catch (e: Exception) {
            exceptionMsg = e.message ?: e.toString()
        }

        val updatedRefund = if (isSuccess) {
            refund.copy(
                syncStatus = "SYNCED",
                lastSyncMessage = "Sincronizado em ${getCurrentTimestampIso()}"
            )
        } else {
            refund.copy(
                syncStatus = "FAILED",
                lastSyncMessage = "Falha: $exceptionMsg"
            )
        }
        refundDao.insertRefund(updatedRefund)

        if (isSuccess) {
            try {
                incrementAndSyncVersion()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar arquivo de versão após sincronização de reembolso: ${e.message}")
            }
        }
    }

    suspend fun buildAndSyncMt5Index() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        try {
            val usersWithMt5 = userDao.getAllUsersList()
            val builder = StringBuilder()
            builder.append("{\n")
            val entries = usersWithMt5.filter { it.mt5IdConta.isNotEmpty() }.map { user ->
                """  "${user.mt5IdConta}": {
    "usuario": "${user.idUsuario}",
    "telefone": "${user.telefone}",
    "nome": "${user.nome}",
    "licenca_ativa": ${user.licencaAtiva},
    "validade": "${user.licencaValidade}",
    "status": "${if (user.licencaAtiva) "ativo" else "desativado"}"
  }"""
            }
            builder.append(entries.joinToString(",\n"))
            builder.append("\n}")
            syncRawFile("dados/indices/mt5.json", builder.toString(), "SMS Gateway: Reconstrução do índice MT5")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar índice MT5: ${e.message}")
        }
    }

    suspend fun buildAndSyncTelefonesIndex() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        try {
            val allUsersList = userDao.getAllUsersList()
            val builder = StringBuilder()
            builder.append("{\n")
            val entries = allUsersList.map { user ->
                """  "${user.telefone}": {
    "usuario": "${user.idUsuario}",
    "mt5": "${user.mt5IdConta}",
    "status": "${user.status}"
  }"""
            }
            builder.append(entries.joinToString(",\n"))
            builder.append("\n}")
            syncRawFile("dados/indices/telefones.json", builder.toString(), "SMS Gateway: Reconstrução do índice de Telefones")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar índice de Telefones: ${e.message}")
        }
    }

    suspend fun syncConfig() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        try {
            val configJson = """
{
  "valor_minimo_ativacao": ${configManager.valorMinimoAtivacao},
  "validade_meses": ${configManager.validadeMeses},
  "sync_mode": "${configManager.syncMode}",
  "auto_send_sms": ${configManager.autoSendSms},
  "auto_sync": ${configManager.autoSync},
  "custom_regex": "${configManager.customRegex.replace("\\", "\\\\").replace("\"", "\\\"")}"
}
            """.trimIndent()
            syncRawFile("dados/configuracao/config.json", configJson, "SMS Gateway: Atualizando configurações")
            syncLicenseTiers()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar configurações: ${e.message}")
        }
    }

    suspend fun syncAudit() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        try {
            val logs = auditLogDao.getAllLogs().first()
            val builder = StringBuilder()
            builder.append("[\n")
            val entries = logs.take(50).map { log ->
                """  {
    "id": ${log.id},
    "sender": "${log.sender}",
    "status": "${log.status}",
    "timestamp": ${log.timestamp},
    "details": "${log.details?.replace("\"", "\\\"") ?: ""}"
  }"""
            }
            builder.append(entries.joinToString(",\n"))
            builder.append("\n]")
            syncRawFile("dados/auditoria/audit_log.json", builder.toString(), "SMS Gateway: Atualizando registros de auditoria")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar registros de auditoria: ${e.message}")
        }
    }

    /**
     * Sync local queue retries.
     */
    suspend fun syncUnsyncedUsers() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) {
            Log.d(TAG, "Sincronização em segundo plano ignorada: credenciais não configuradas para o modo ${configManager.syncMode}")
            return@withContext
        }

        // Sync Users
        val unsyncedUsers = userDao.getUnsyncedUsers()
        for (user in unsyncedUsers) {
            syncUser(user)
        }

        // Sync Pending
        val unsyncedPendings = pendingPaymentDao.getUnsyncedPending()
        for (pending in unsyncedPendings) {
            syncPendingPayment(pending)
        }

        // Sync Refunds
        val unsyncedRefunds = refundDao.getUnsyncedRefunds()
        for (refund in unsyncedRefunds) {
            syncRefund(refund)
        }

        try {
            buildAndSyncMt5Index()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar índice MT5 na re-tentativa: ${e.message}")
        }
        try {
            buildAndSyncTelefonesIndex()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar índice de Telefones na re-tentativa: ${e.message}")
        }
        try {
            syncConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar configurações na re-tentativa: ${e.message}")
        }
        try {
            syncAudit()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar auditoria na re-tentativa: ${e.message}")
        }
    }

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        userDao.clearAllUsers()
        auditLogDao.clearAllLogs()
        smsLogDao.clearSmsLogs()
        pendingPaymentDao.clearAllPending()
        refundDao.clearAllRefunds()
    }

    fun exportUsersToCsv(): String {
        val usersList = run {
            var res: List<UserEntity> = emptyList()
            kotlinx.coroutines.runBlocking {
                try {
                    res = userDao.getAllUsers().first()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed reading flow: ${e.message}")
                }
            }
            res
        }

        val builder = StringBuilder()
        builder.append("ID Usuario,Telefone,Nome,ID Transacao,Saldo,Status,Data Registro,Sincronizacao,Licenca Validade,Credito Guardado\n")
        
        for (user in usersList) {
            val cleanNome = user.nome.replace(",", ";").trim()
            builder.append("${user.idUsuario},${user.telefone},$cleanNome,${user.idTransacao},")
            builder.append("${user.saldo},${user.status},${user.dataRegistro},${user.syncStatus},${user.licencaValidade},${user.creditoGuardado}\n")
        }
        return builder.toString()
    }

    // Firebase Tasks extension helper to enable suspend-await
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Firebase Task failed"))
            }
        }
    }

    private suspend fun initFirebaseIfNeeded() {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try {
                    val authResult = auth.signInAnonymously().await()
                    Log.i(TAG, "Autenticação anônima do Firebase efetuada com sucesso. UID: ${authResult.user?.uid}")
                } catch (authEx: Throwable) {
                    Log.w(TAG, "Tentativa de autenticação anônima no Firebase falhou ou pendente: ${authEx.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao inicializar FirebaseApp/Auth: ${e.message}")
        }
    }

    fun getCurrentAdminUid(): String? {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao obter UID de autenticação do Firebase: ${e.message}")
            return null
        }
    }

    private fun getFirebaseDatabaseInstance(): com.google.firebase.database.FirebaseDatabase {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Erro ao inicializar FirebaseApp: ${e.message}")
            }
        }
        return try {
            com.google.firebase.database.FirebaseDatabase.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Nao foi possivel obter instancia padrao de FirebaseDatabase. Usando URL base do projectId.")
            try {
                val app = com.google.firebase.FirebaseApp.getInstance()
                val projectId = app.options.projectId ?: "fimaster-sms-gateway"
                val url = "https://$projectId-default-rtdb.firebaseio.com"
                com.google.firebase.database.FirebaseDatabase.getInstance(url)
            } catch (inner: Throwable) {
                Log.e(TAG, "Erro fatal ao obter FirebaseDatabase com URL fallback: ${inner.message}")
                throw inner
            }
        }
    }

    private fun getFirestoreCollectionAndDoc(path: String): Pair<String, String>? {
        val cleanPath = path.removeSuffix(".json").removeSuffix(".set").removeSuffix(".txt")
        val segments = cleanPath.split("/")
        return when {
            segments.size >= 2 -> {
                val col = segments.dropLast(1).joinToString("_")
                val doc = segments.last()
                Pair(col, doc)
            }
            segments.size == 1 && segments[0].isNotEmpty() -> {
                Pair("metadata", segments[0])
            }
            else -> null
        }
    }

    private fun jsonToMap(json: org.json.JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = when (value) {
                is org.json.JSONObject -> jsonToMap(value)
                is org.json.JSONArray -> jsonToList(value)
                org.json.JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonToList(array: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            list.add(
                when (value) {
                    is org.json.JSONObject -> jsonToMap(value)
                    is org.json.JSONArray -> jsonToList(value)
                    org.json.JSONObject.NULL -> null
                    else -> value
                }
            )
        }
        return list
    }

    private fun mapToJsonObject(map: Map<String, Any?>): org.json.JSONObject {
        val json = org.json.JSONObject()
        for ((key, value) in map) {
            json.put(key, when (value) {
                is Map<*, *> -> mapToJsonObject(value as Map<String, Any?>)
                is List<*> -> listToJsonArray(value)
                null -> org.json.JSONObject.NULL
                else -> value
            })
        }
        return json
    }

    private fun listToJsonArray(list: List<*>): org.json.JSONArray {
        val array = org.json.JSONArray()
        for (value in list) {
            array.put(when (value) {
                is Map<*, *> -> mapToJsonObject(value as Map<String, Any?>)
                is List<*> -> listToJsonArray(value)
                null -> org.json.JSONObject.NULL
                else -> value
            })
        }
        return array
    }

    fun parseUserFromFirebaseMap(idUsuario: String, map: Map<String, Any?>): UserEntity? {
        try {
            val root = org.json.JSONObject()
            val userJson = mapToJsonObject(map)
            root.put(idUsuario, userJson)
            return parseUserFromJson(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parsear usuario do Firebase map: ${e.message}")
            return null
        }
    }

    private fun formatBaseUrl(url: String): String {
        return if (url.endsWith("/")) url.substring(0, url.length - 1) else url
    }

    fun parseUserFromJson(jsonStr: String): UserEntity? {
        try {
            val root = org.json.JSONObject(jsonStr)
            val keys = root.keys()
            if (!keys.hasNext()) return null
            val idUsuario = keys.next()
            val obj = root.getJSONObject(idUsuario)
            
            val status = obj.optString("status", "AGUARDANDO_ATIVACAO")
            val origem = obj.optString("origem", "sms_fimaster")
            val telefone = obj.optString("numero", "")
            val nome = obj.optString("nome", "")
            val idTransacao = obj.optString("id_transacao", "")
            val saldo = obj.optDouble("saldo", 0.0)
            val senhaHash = obj.optString("senha_hash", "")
            val salt = obj.optString("salt", "")
            val tokenRecuperacao = obj.optString("token_recuperacao", "")
            val nivelAutorizacao = obj.optString("nivel_autorizacao", "CLIENTE")
            val dataRegistro = obj.optString("data_registro", "")
            val ultimaAtualizacao = obj.optString("ultima_atualizacao", "")
            
            val mt5Obj = obj.optJSONObject("mt5")
            val mt5Registrado = mt5Obj?.optBoolean("registrado", false) ?: false
            val mt5IdConta = mt5Obj?.optString("id_conta", "") ?: ""
            
            val licencaObj = obj.optJSONObject("licenca")
            val licencaAtiva = licencaObj?.optBoolean("ativa", false) ?: false
            val licencaProduto = licencaObj?.optString("produto", "Fimaster") ?: "Fimaster"
            val licencaPlano = licencaObj?.optString("plano", "Anual") ?: "Anual"
            val licencaValidade = licencaObj?.optString("validade", "") ?: ""
            val ultimaRenovacao = licencaObj?.optString("ultima_renovacao", "") ?: ""
            val totalRenovacoes = licencaObj?.optInt("total_renovacoes", 0) ?: 0
            val historicoRenovacoes = licencaObj?.optJSONArray("historico")?.toString() ?: "[]"
            
            val reembolsoObj = obj.optJSONObject("reembolso")
            val reembolsoSolicitado = reembolsoObj?.optBoolean("solicitado", false) ?: false
            val reembolsoStatus = reembolsoObj?.optString("status", "NENHUM") ?: "NENHUM"
            
            val autorizacaoObj = obj.optJSONObject("autorizacao")
            val autorizacaoStatus = autorizacaoObj?.optString("status", "PENDENTE") ?: "PENDENTE"
            val autorizacaoAprovadoPor = autorizacaoObj?.optString("aprovado_por", "") ?: ""
            val autorizacaoDataAprovacao = autorizacaoObj?.optString("data_aprovacao", "") ?: ""
            
            val auditoriaObj = obj.optJSONObject("auditoria")
            val parsedDeviceId = auditoriaObj?.optString("ultimo_dispositivo", "") ?: ""
            
            val creditoGuardado = obj.optDouble("credito_guardado", 0.0)

            return UserEntity(
                idUsuario = idUsuario,
                status = status,
                origem = origem,
                telefone = telefone,
                nome = nome,
                idTransacao = idTransacao,
                saldo = saldo,
                senhaHash = senhaHash,
                salt = salt,
                tokenRecuperacao = tokenRecuperacao,
                nivelAutorizacao = nivelAutorizacao,
                dataRegistro = dataRegistro,
                ultimaAtualizacao = ultimaAtualizacao,
                mt5Registrado = mt5Registrado,
                mt5IdConta = mt5IdConta,
                licencaAtiva = licencaAtiva,
                licencaProduto = licencaProduto,
                licencaValidade = licencaValidade,
                licencaPlano = licencaPlano,
                ultimaRenovacao = ultimaRenovacao,
                totalRenovacoes = totalRenovacoes,
                historicoRenovacoes = historicoRenovacoes,
                deviceId = parsedDeviceId,
                reembolsoSolicitado = reembolsoSolicitado,
                reembolsoStatus = reembolsoStatus,
                autorizacaoStatus = autorizacaoStatus,
                autorizacaoAprovadoPor = autorizacaoAprovadoPor,
                autorizacaoDataAprovacao = autorizacaoDataAprovacao,
                creditoGuardado = if (creditoGuardado.isNaN()) 0.0 else creditoGuardado,
                syncStatus = "SYNCED"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parsear usuário JSON: ${e.message}")
            return null
        }
    }

    suspend fun fetchRemoteVersion(): Pair<Int, String>? = withContext(Dispatchers.IO) {
        val syncMode = configManager.syncMode
        if (syncMode == ConfigManager.MODE_GITHUB) {
            val token = configManager.githubToken.trim()
            val repo = configManager.githubRepo.trim()
            val branch = configManager.githubBranch.trim().ifEmpty { "main" }
            
            if (token.isEmpty() || repo.isEmpty()) return@withContext null
            
            val ownerRepo = repo.split("/")
            if (ownerRepo.size != 2) return@withContext null
            val owner = ownerRepo[0].trim()
            val repoName = ownerRepo[1].trim()
            
            try {
                val response = getGitHubService().getFileMetadata(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    filePath = "dados/versao.json",
                    ref = branch
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val base64Content = body?.content ?: return@withContext null
                    val decodedBytes = android.util.Base64.decode(base64Content.replace("\n", "").trim(), android.util.Base64.DEFAULT)
                    val jsonStr = String(decodedBytes, Charsets.UTF_8)
                    val root = org.json.JSONObject(jsonStr)
                    val version = root.optInt("versao_dados", 1)
                    val updated = root.optString("ultima_atualizacao", "")
                    return@withContext Pair(version, updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar versao remota do GitHub: ${e.message}")
            }
        } else if (syncMode == ConfigManager.MODE_FIREBASE) {
            try {
                val rtdb = getFirebaseDatabaseInstance()
                val ref = rtdb.getReference("dados/versao")
                val snapshot = ref.get().await()
                if (snapshot.exists()) {
                    val version = snapshot.child("versao_dados").getValue(Long::class.java)?.toInt() ?: 1
                    val updated = snapshot.child("ultima_atualizacao").getValue(String::class.java) ?: ""
                    return@withContext Pair(version, updated)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Erro ao buscar versao remota do Firebase: ${e.message ?: e.toString()}")
            }
        } else {
            val fastApiUrl = configManager.fastApiUrl.trim()
            val token = configManager.fastApiToken.trim()
            if (fastApiUrl.isNotEmpty()) {
                try {
                    val authHeader = if (token.isNotEmpty()) "Bearer $token" else null
                    val apiService = getFastApiService(fastApiUrl)
                    val getUrl = "${formatBaseUrl(fastApiUrl)}/dados/versao.json"
                    val response = apiService.getRawFile(getUrl, authHeader)
                    if (response.isSuccessful) {
                        val bodyStr = response.body()?.string() ?: ""
                        val root = org.json.JSONObject(bodyStr)
                        val version = root.optInt("versao_dados", 1)
                        val updated = root.optString("ultima_atualizacao", "")
                        return@withContext Pair(version, updated)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao buscar versao remota da FastAPI: ${e.message}")
                }
            }
        }
        return@withContext null
    }

    suspend fun performBackgroundAutoSync() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando verificação de sincronização periódica automática em segundo plano...")
        
        // Verificação automática de validade de licença (Point 1 & 9)
        try {
            checkAndExpirateLicenses()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar expirações na sincronização: ${e.message}")
        }

        // Envio automático de lembretes diários (Point 2)
        try {
            val todayStr = getCurrentDateStr()
            if (configManager.lastReminderDate != todayStr) {
                sendLicenseReminders()
                configManager.lastReminderDate = todayStr
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar lembretes na sincronização: ${e.message}")
        }

        val remoteVersionInfo = fetchRemoteVersion()
        if (remoteVersionInfo == null) {
            Log.w(TAG, "Não foi possível obter a versão remota de dados. Sincronização em segundo plano abortada.")
            return@withContext
        }

        val remoteVersion = remoteVersionInfo.first
        val remoteTimestamp = remoteVersionInfo.second
        val localVersion = configManager.dadosVersion

        if (remoteVersion == localVersion) {
            Log.d(TAG, "Nenhuma alteração detectada. Versão local ($localVersion) está atualizada com a remota ($remoteVersion).")
            return@withContext
        }

        Log.d(TAG, "Nova versão de dados detectada remotamente: $remoteVersion (Local: $localVersion). Baixando atualizações...")

        val syncMode = configManager.syncMode
        if (syncMode == ConfigManager.MODE_GITHUB) {
            val token = configManager.githubToken.trim()
            val repo = configManager.githubRepo.trim()
            val branch = configManager.githubBranch.trim().ifEmpty { "main" }
            
            if (token.isEmpty() || repo.isEmpty()) return@withContext
            val ownerRepo = repo.split("/")
            if (ownerRepo.size != 2) return@withContext
            val owner = ownerRepo[0].trim()
            val repoName = ownerRepo[1].trim()

            try {
                val response = getGitHubService().getDirectoryContents(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    dirPath = "dados/usuarios",
                    ref = branch
                )

                if (response.isSuccessful) {
                    val filesList = response.body() ?: emptyList()
                    Log.d(TAG, "Total de arquivos de usuários encontrados no repositório: ${filesList.size}")

                    for (fileMeta in filesList) {
                        if (!fileMeta.name.endsWith(".json")) continue
                        
                        val fileResponse = getGitHubService().getFileMetadata(
                            authorization = "Bearer $token",
                            owner = owner,
                            repo = repoName,
                            filePath = fileMeta.path,
                            ref = branch
                        )

                        if (fileResponse.isSuccessful) {
                            val detailBody = fileResponse.body()
                            val base64Content = detailBody?.content ?: continue
                            val decodedBytes = android.util.Base64.decode(base64Content.replace("\n", "").trim(), android.util.Base64.DEFAULT)
                            val jsonStr = String(decodedBytes, Charsets.UTF_8)

                            val remoteUser = parseUserFromJson(jsonStr)
                            if (remoteUser != null) {
                                val localUser = userDao.getUserById(remoteUser.idUsuario)
                                if (localUser == null) {
                                    userDao.insertUser(remoteUser)
                                    Log.d(TAG, "Novo usuário adicionado via sincronização: ${remoteUser.idUsuario}")
                                    if (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO") {
                                        sendActivationNotificationSms(remoteUser)
                                    }
                                } else if (localUser.ultimaAtualizacao != remoteUser.ultimaAtualizacao || localUser.status != remoteUser.status) {
                                    userDao.insertUser(remoteUser)
                                    Log.d(TAG, "Usuário atualizado via sincronização: ${remoteUser.idUsuario}")
                                    if (localUser.status != remoteUser.status && (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO")) {
                                        sendActivationNotificationSms(remoteUser)
                                    }
                                }
                            }
                        }
                    }

                    configManager.dadosVersion = remoteVersion
                    configManager.ultimaAtualizacaoDados = remoteTimestamp
                    Log.i(TAG, "Sincronização de segundo plano concluída com sucesso para a versão $remoteVersion!")
                } else {
                    Log.e(TAG, "Erro ao listar diretório de usuários no GitHub: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao executar auto-sync do GitHub: ${e.message}")
            }
        } else if (syncMode == ConfigManager.MODE_FIREBASE) {
            try {
                initFirebaseIfNeeded()
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val querySnapshot = firestore.collection("dados_usuarios").get().await()
                
                for (doc in querySnapshot.documents) {
                    val userMap = doc.data
                    if (userMap != null) {
                        val remoteUser = parseUserFromFirebaseMap(doc.id, userMap)
                        if (remoteUser != null) {
                            val localUser = userDao.getUserById(remoteUser.idUsuario)
                            if (localUser == null) {
                                userDao.insertUser(remoteUser)
                                Log.d(TAG, "Novo usuário adicionado via Firebase: ${remoteUser.idUsuario}")
                                if (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO") {
                                    sendActivationNotificationSms(remoteUser)
                                }
                            } else if (localUser.ultimaAtualizacao != remoteUser.ultimaAtualizacao || localUser.status != remoteUser.status) {
                                userDao.insertUser(remoteUser)
                                Log.d(TAG, "Usuário atualizado via Firebase: ${remoteUser.idUsuario}")
                                if (localUser.status != remoteUser.status && (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO")) {
                                    sendActivationNotificationSms(remoteUser)
                                }
                            }
                        }
                    }
                }
                configManager.dadosVersion = remoteVersion
                configManager.ultimaAtualizacaoDados = remoteTimestamp
                Log.i(TAG, "Sincronização de segundo plano concluída via Firebase para a versão $remoteVersion!")
            } catch (e: Throwable) {
                Log.e(TAG, "Exceção ao executar auto-sync do Firebase: ${e.message ?: e.toString()}")
            }
        } else {
            val fastApiUrl = configManager.fastApiUrl.trim()
            val token = configManager.fastApiToken.trim()
            if (fastApiUrl.isNotEmpty()) {
                try {
                    val authHeader = if (token.isNotEmpty()) "Bearer $token" else null
                    val apiService = getFastApiService(fastApiUrl)
                    val getUrl = "${formatBaseUrl(fastApiUrl)}/dados/usuarios"
                    val response = apiService.getRawFile(getUrl, authHeader)
                    if (response.isSuccessful) {
                        val bodyStr = response.body()?.string() ?: ""
                        val rootArray = org.json.JSONArray(bodyStr)
                        for (i in 0 until rootArray.length()) {
                            val userJson = rootArray.getJSONObject(i).toString()
                            val remoteUser = parseUserFromJson(userJson)
                            if (remoteUser != null) {
                                val localUser = userDao.getUserById(remoteUser.idUsuario)
                                if (localUser == null) {
                                    userDao.insertUser(remoteUser)
                                    if (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO") {
                                        sendActivationNotificationSms(remoteUser)
                                    }
                                } else if (localUser.ultimaAtualizacao != remoteUser.ultimaAtualizacao || localUser.status != remoteUser.status) {
                                    userDao.insertUser(remoteUser)
                                    if (localUser.status != remoteUser.status && (remoteUser.status == "ATIVO" || remoteUser.status == "APROVADO")) {
                                        sendActivationNotificationSms(remoteUser)
                                    }
                                }
                            }
                        }
                        configManager.dadosVersion = remoteVersion
                        configManager.ultimaAtualizacaoDados = remoteTimestamp
                        Log.i(TAG, "Sincronização de segundo plano concluída via FastAPI para a versão $remoteVersion!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exceção ao executar auto-sync da FastAPI: ${e.message}")
                }
            }
        }
    }

    suspend fun forceSyncAllUsers() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Forçando sincronização de todos os usuários do servidor remoto...")
        val syncMode = configManager.syncMode
        if (syncMode == ConfigManager.MODE_GITHUB) {
            val token = configManager.githubToken.trim()
            val repo = configManager.githubRepo.trim()
            val branch = configManager.githubBranch.trim().ifEmpty { "main" }
            
            if (token.isEmpty() || repo.isEmpty()) return@withContext
            val ownerRepo = repo.split("/")
            if (ownerRepo.size != 2) return@withContext
            val owner = ownerRepo[0].trim()
            val repoName = ownerRepo[1].trim()

            try {
                val response = getGitHubService().getDirectoryContents(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    dirPath = "dados/usuarios",
                    ref = branch
                )

                if (response.isSuccessful) {
                    val filesList = response.body() ?: emptyList()
                    Log.d(TAG, "forceSyncAllUsers: Total de arquivos de usuários encontrados no repositório: ${filesList.size}")

                    for (fileMeta in filesList) {
                        if (!fileMeta.name.endsWith(".json")) continue
                        
                        val fileResponse = getGitHubService().getFileMetadata(
                            authorization = "Bearer $token",
                            owner = owner,
                            repo = repoName,
                            filePath = fileMeta.path,
                            ref = branch
                        )

                        if (fileResponse.isSuccessful) {
                            val detailBody = fileResponse.body()
                            val base64Content = detailBody?.content ?: continue
                            val decodedBytes = android.util.Base64.decode(base64Content.replace("\n", "").trim(), android.util.Base64.DEFAULT)
                            val jsonStr = String(decodedBytes, Charsets.UTF_8)

                            val remoteUser = parseUserFromJson(jsonStr)
                            if (remoteUser != null) {
                                userDao.insertUser(remoteUser)
                                Log.d(TAG, "forceSyncAllUsers: Usuário adicionado/atualizado: ${remoteUser.idUsuario}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao executar forceSyncAllUsers do GitHub: ${e.message}")
            }
        } else if (syncMode == ConfigManager.MODE_FIREBASE) {
            try {
                initFirebaseIfNeeded()
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val querySnapshot = firestore.collection("dados_usuarios").get().await()
                
                for (doc in querySnapshot.documents) {
                    val userMap = doc.data
                    if (userMap != null) {
                        val remoteUser = parseUserFromFirebaseMap(doc.id, userMap)
                        if (remoteUser != null) {
                            userDao.insertUser(remoteUser)
                            Log.d(TAG, "forceSyncAllUsers: Usuário adicionado/atualizado via Firebase: ${remoteUser.idUsuario}")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exceção ao executar forceSyncAllUsers do Firebase: ${e.message ?: e.toString()}")
            }
        } else {
            val fastApiUrl = configManager.fastApiUrl.trim()
            val token = configManager.fastApiToken.trim()
            if (fastApiUrl.isNotEmpty()) {
                try {
                    val authHeader = if (token.isNotEmpty()) "Bearer $token" else null
                    val apiService = getFastApiService(fastApiUrl)
                    val getUrl = "${formatBaseUrl(fastApiUrl)}/dados/usuarios"
                    val response = apiService.getRawFile(getUrl, authHeader)
                    if (response.isSuccessful) {
                        val bodyStr = response.body()?.string() ?: ""
                        val rootArray = org.json.JSONArray(bodyStr)
                        for (i in 0 until rootArray.length()) {
                            val userJson = rootArray.getJSONObject(i).toString()
                            val remoteUser = parseUserFromJson(userJson)
                            if (remoteUser != null) {
                                userDao.insertUser(remoteUser)
                                Log.d(TAG, "forceSyncAllUsers (FastAPI): Usuário adicionado/atualizado: ${remoteUser.idUsuario}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exceção ao executar forceSyncAllUsers da FastAPI: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendActivationNotificationSms(user: UserEntity) {
        val text = """
**Fimaster**

✅ A sua conta e licença foram ativadas com sucesso!

**ID do Utilizador:** ${user.idUsuario}

Para concluir a ativação da sua licença, acesse o site abaixo e registre a sua conta MT5:

${configManager.siteUrl}

Obrigado por escolher a Fimaster.
        """.trimIndent()
        sendCustomSms(user.telefone, text)
    }

    suspend fun incrementAndSyncVersion() = withContext(Dispatchers.IO) {
        if (!isSyncConfigured()) return@withContext
        val nextVersion = configManager.dadosVersion + 1
        configManager.dadosVersion = nextVersion
        val currentTimestamp = getCurrentTimestampIso()
        configManager.ultimaAtualizacaoDados = currentTimestamp

        val versionJson = """{
  "versao_dados": $nextVersion,
  "ultima_atualizacao": "$currentTimestamp"
}"""

        val path = "dados/versao.json"
        try {
            syncRawFile(path, versionJson, "SMS Gateway: Incrementando versao para $nextVersion")
            Log.d(TAG, "Versão de dados incrementada e sincronizada: $nextVersion")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao sincronizar arquivo de versão: ${e.message}")
        }
    }

    suspend fun fetchRemoteJsonContent(pathInRepo: String): String? = withContext(Dispatchers.IO) {
        val mode = configManager.syncMode
        if (mode == ConfigManager.MODE_GITHUB) {
            val token = configManager.githubToken.trim()
            val repo = configManager.githubRepo.trim()
            val branch = configManager.githubBranch.trim().ifEmpty { "main" }
            if (token.isEmpty() || repo.isEmpty()) return@withContext null
            
            val ownerRepo = repo.split("/")
            if (ownerRepo.size != 2) return@withContext null
            val owner = ownerRepo[0].trim()
            val repoName = ownerRepo[1].trim()
            
            try {
                val response = getGitHubService().getFileMetadata(
                    authorization = "Bearer $token",
                    owner = owner,
                    repo = repoName,
                    filePath = pathInRepo,
                    ref = branch
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val base64Content = body?.content ?: return@withContext null
                    val decodedBytes = android.util.Base64.decode(base64Content.replace("\n", "").trim(), android.util.Base64.DEFAULT)
                    return@withContext String(decodedBytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar arquivo remoto do GitHub ($pathInRepo): ${e.message}")
            }
        } else if (mode == ConfigManager.MODE_FIREBASE) {
            try {
                initFirebaseIfNeeded()
                
                // Try Firestore first since indices and dados_usuarios can be mapped
                val colDoc = getFirestoreCollectionAndDoc(pathInRepo)
                if (colDoc != null) {
                    val (col, doc) = colDoc
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val docRef = firestore.collection(col).document(doc)
                    val docSnap = docRef.get().await()
                    if (docSnap.exists()) {
                        val data = docSnap.data
                        if (data != null) {
                            return@withContext org.json.JSONObject(data).toString()
                        }
                    }
                }
                
                // Fallback to Realtime Database
                val dbRefPath = pathInRepo.removeSuffix(".json").removeSuffix(".set").removeSuffix(".txt")
                val rtdb = getFirebaseDatabaseInstance()
                val ref = rtdb.getReference(dbRefPath)
                val snapshot = ref.get().await()
                if (snapshot.exists()) {
                    val value = snapshot.value
                    if (value is Map<*, *>) {
                        return@withContext org.json.JSONObject(value as Map<String, Any?>).toString()
                    } else if (value != null) {
                        return@withContext value.toString()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Erro ao buscar arquivo remoto do Firebase ($pathInRepo): ${e.message ?: e.toString()}")
            }
        } else {
            val fastApiUrl = configManager.fastApiUrl.trim()
            val token = configManager.fastApiToken.trim()
            if (fastApiUrl.isNotEmpty()) {
                try {
                    val authHeader = if (token.isNotEmpty()) "Bearer $token" else null
                    val apiService = getFastApiService(fastApiUrl)
                    val getUrl = "${formatBaseUrl(fastApiUrl)}/$pathInRepo"
                    val response = apiService.getRawFile(getUrl, authHeader)
                    if (response.isSuccessful) {
                        return@withContext response.body()?.string()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao buscar arquivo remoto da FastAPI ($pathInRepo): ${e.message}")
                }
            }
        }
        return@withContext null
    }

    suspend fun loginEaMql5(mt5Id: String, passwordSent: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedMt5 = mt5Id.trim()
        val trimmedPass = passwordSent.trim()
        if (trimmedMt5.isEmpty() || trimmedPass.isEmpty()) {
            return@withContext Pair(false, "Preencha a conta MT5 e a senha.")
        }

        var userId: String? = null
        
        // Attempt remote index lookup
        val remoteIndexJson = fetchRemoteJsonContent("dados/indices/mt5.json")
        if (remoteIndexJson != null) {
            try {
                val root = org.json.JSONObject(remoteIndexJson)
                if (root.has(trimmedMt5)) {
                    val mt5Entry = root.getJSONObject(trimmedMt5)
                    userId = mt5Entry.optString("usuario", "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao decodificar índice MT5 remoto: ${e.message}")
            }
        }
        
        // Local database index lookup fallback
        if (userId.isNullOrEmpty()) {
            val localUser = userDao.getUserByMt5(trimmedMt5)
            if (localUser != null) {
                userId = localUser.idUsuario
            }
        }

        if (userId.isNullOrEmpty()) {
            return@withContext Pair(false, "Conta MT5 não registada no índice.")
        }

        var user: UserEntity? = null
        
        // Attempt remote "dados_usuarios" lookup
        val remoteUserJson = fetchRemoteJsonContent("dados/usuarios/$userId.json")
        if (remoteUserJson != null) {
            try {
                user = parseUserFromJson(remoteUserJson)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao decodificar dados do usuário remoto: ${e.message}")
            }
        }
        
        // Fallback to local
        if (user == null) {
            user = userDao.getUserById(userId)
        }

        if (user == null) {
            return@withContext Pair(false, "Utilizador associado à conta MT5 não encontrado.")
        }

        // Verify password hash
        val storedHashParts = user.senhaHash.split(":")
        val actualStoredHash = storedHashParts.firstOrNull() ?: ""
        val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
        val enteredHash = com.example.util.SecurityUtils.hashSha256(trimmedPass, saltToUse)

        if (enteredHash == actualStoredHash) {
            return@withContext Pair(true, "Login do EA efetuado com sucesso!")
        } else {
            return@withContext Pair(false, "Senha incorreta para a conta MT5.")
        }
    }

    suspend fun loginPortalFimaster(phone: String, passwordSent: String): Pair<Boolean, UserEntity?> = withContext(Dispatchers.IO) {
        val trimmedPhone = phone.trim()
        val trimmedPass = passwordSent.trim()
        if (trimmedPhone.isEmpty() || trimmedPass.isEmpty()) {
            return@withContext Pair(false, null)
        }

        var userId: String? = null
        
        // Attempt remote index lookup
        val remoteIndexJson = fetchRemoteJsonContent("dados/indices/telefones.json")
        if (remoteIndexJson != null) {
            try {
                val root = org.json.JSONObject(remoteIndexJson)
                val possibleKeys = listOf(trimmedPhone, "+$trimmedPhone", trimmedPhone.removePrefix("+"))
                for (key in possibleKeys) {
                    if (root.has(key)) {
                        val entry = root.getJSONObject(key)
                        userId = entry.optString("usuario", "")
                        if (!userId.isNullOrEmpty()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao decodificar índice de telefones remoto: ${e.message}")
            }
        }
        
        // Local database index lookup fallback
        if (userId.isNullOrEmpty()) {
            val localUser = userDao.getUserByPhone(trimmedPhone) ?: userDao.getUserByPhone("+$trimmedPhone") ?: userDao.getUserByPhone(trimmedPhone.removePrefix("+"))
            if (localUser != null) {
                userId = localUser.idUsuario
            }
        }

        if (userId.isNullOrEmpty()) {
            return@withContext Pair(false, null)
        }

        var user: UserEntity? = null
        
        // Attempt remote "dados_usuarios" lookup
        val remoteUserJson = fetchRemoteJsonContent("dados/usuarios/$userId.json")
        if (remoteUserJson != null) {
            try {
                user = parseUserFromJson(remoteUserJson)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao decodificar dados do usuário remoto: ${e.message}")
            }
        }
        
        // Fallback to local
        if (user == null) {
            user = userDao.getUserById(userId)
        }

        if (user == null) {
            return@withContext Pair(false, null)
        }

        // Verify password hash
        val storedHashParts = user.senhaHash.split(":")
        val actualStoredHash = storedHashParts.firstOrNull() ?: ""
        val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
        val enteredHash = com.example.util.SecurityUtils.hashSha256(trimmedPass, saltToUse)

        if (enteredHash == actualStoredHash) {
            val silentUid = try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            } catch (e: Exception) { null } ?: android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)

            val updatedUser = if (user.deviceId.isEmpty() || user.deviceId != silentUid) {
                user.copy(
                    deviceId = silentUid,
                    ultimaAtualizacao = getCurrentTimestampIso()
                ).also {
                    userDao.insertUser(it)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            syncUser(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao sincronizar dispositivo silencioso do usuário: ${e.message}")
                        }
                    }
                }
            } else {
                user
            }
            return@withContext Pair(true, updatedUser)
        } else {
            return@withContext Pair(false, null)
        }
    }

    fun parseEaConfigFromJson(jsonStr: String, mt5Id: String): EaConfigEntity {
        val root = org.json.JSONObject(jsonStr)
        return EaConfigEntity(
            mt5IdConta = mt5Id,
            lJJ = root.optString("lJJ", "⬛⬛⬛⬛⬛⬛⬛[ AUTENTICAÇÃO ]⬛⬛⬛⬛⬛⬛⬛"),
            senhaEa = root.optString("SENHA", "123456"),
            aYY = root.optString("aYY", "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛[ COR ]⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛"),
            esquemaCoresEnum = root.optString("ESQUEMA_CORES_ENUM", "CYAN_NEON"),
            corDeCanal = root.optString("cor_de_canal", "#22D3EE"),
            corDeLinhas = root.optString("cor_de_linhas", "#FF00E5"),
            corrDeEquador = root.optString("corr_de_equador", "#FFFF00"),
            sJJ = root.optString("sJJ", "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ TENDÊNCIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛"),
            linhasDeEquador = if (root.has("LINHAS_DE_EQUADOR")) root.optBoolean("LINHAS_DE_EQUADOR") else false,
            tendenciaValue = if (root.has("TREND")) root.optString("TREND") else root.optString("TENDENCIA", "TENDENCIA_DE_ALTA"),
            mEquadorAlta = root.optDouble("M_equador_alta", 1.2500),
            mEquadorBaixa = root.optDouble("M_equador_baixa", 1.2400),
            xxx = root.optString("xxx", "⬛⬛⬛⬛⬛⬛⬛⬛[ ESTRATÉGIA ]⬛⬛⬛⬛⬛⬛⬛⬛⬛"),
            tema = if (root.has("TEMA")) root.optBoolean("TEMA") else false,
            estrategiaValue = if (root.has("ESTRATÉGIA")) root.optString("ESTRATÉGIA") else root.optString("ESTRATEGIA", "FIMATHE"),
            viradaDeJogo = if (root.has("virada_de_jogo")) root.optBoolean("virada_de_jogo") else false,
            nives = root.optDouble("Nives", 1.0),
            costurar = if (root.has("Costurar")) root.optBoolean("Costurar") else true,
            periodoOperacional = if (root.has("OperationalPeriod")) root.optString("OperationalPeriod") else root.optString("PeriodoOperacional", "PERIOD_M15"),
            lot = root.optDouble("lot", 0.00),
            dS = root.optString("dS", "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ AUTOMATICO ]⬛⬛⬛⬛⬛⬛⬛⬛"),
            eaAtivo = if (root.has("EA_ATIVO")) root.optBoolean("EA_ATIVO") else true,
            eaAuto = if (root.has("EA_AUTO")) root.optBoolean("EA_AUTO") else false,
            periodoAuto = if (root.has("AUTO_PERIOD")) root.optString("AUTO_PERIOD") else root.optString("PERIODO_AUTO", "HORA_1"),
            autoSurfada = if (root.has("AUTO_SURFADA")) root.optBoolean("AUTO_SURFADA") else false,
            sessaoAsiaToquio = if (root.has("SESSAO_ASIA_TOQUIO")) root.optBoolean("SESSAO_ASIA_TOQUIO") else false,
            sessaoLondres = if (root.has("SESSAO_LONDRES")) root.optBoolean("SESSAO_LONDRES") else true,
            sessaoNovaYorqui = if (root.has("SESSAO_NOVA_YORQUI")) root.optBoolean("SESSAO_NOVA_YORQUI") else true,
            expansaoMinima = root.optInt("EXPANSAO_MINIMA", 10),
            expansaoMaxima = root.optInt("EXPANSAO_MAXIMA", 30),
            dSS = root.optString("dSS", "⬛⬛⬛⬛⬛⬛⬛[ POSIC: DE ORDEM ]⬛⬛⬛⬛⬛⬛⬛"),
            compra = root.optDouble("compra", 1.2550),
            venda = root.optDouble("venda", 1.2500),
            santo = root.optDouble("santo", 20.0),
            dedo = root.optInt("dedo", 10),
            posicaoTake = if (root.has("posicaoTake")) root.optBoolean("posicaoTake") else false,
            buyTake = root.optDouble("buy_take", 0.0),
            sellTake = root.optDouble("sell_take", 0.0),
            fDD = root.optString("fDD", "⬛⬛⬛⬛⬛⬛[ GERENC: DE CAPITAL ]⬛⬛⬛⬛⬛⬛"),
            saldoDemo = root.optDouble("SALDO", 1000.0),
            gerenciamentoDeRiscoDiario = if (root.has("GERENCIAMENTO_DE_RISCO_DIARIO")) root.optBoolean("GERENCIAMENTO_DE_RISCO_DIARIO") else true,
            porcentos = root.optDouble("porcentos", 1.0),
            porcentosg = root.optDouble("poercentosg", 1.5),
            gerenciamentoDeRiscoSemanal = if (root.has("GERENCIAMENTO_DE_RISCO_SEMANAL")) root.optBoolean("GERENCIAMENTO_DE_RISCO_SEMANAL") else false,
            porcentoo = root.optDouble("PORCENTOO", 2.0),
            porcentoss = root.optDouble("PORCENTOSS", 2.0),
            gG = root.optString("gG", "⬛⬛⬛⬛⬛[ PARÂM: OPERACIONAIS ]⬛⬛⬛⬛⬛"),
            gmail = if (root.has("GMAIL")) root.optBoolean("GMAIL") else true,
            notific = if (root.has("notific")) root.optBoolean("notific") else true,
            ativarOuDesativarVenda = if (root.has("ativar_ou_desativar_venda")) root.optBoolean("ativar_ou_desativar_venda") else true,
            ativarOuDesativarCompra = if (root.has("ativar_ou_desativar_compra")) root.optBoolean("ativar_ou_desativar_compra") else true,
            modificarSlParaOxO = if (root.has("Modify_Sl_For_OxO")) root.optBoolean("Modify_Sl_For_OxO") else root.optBoolean("Modificar_Sl_Para_OxO", true),
            condicaoDeRompimentoC = if (root.has("condicao_De_rompimento_c")) root.optBoolean("condicao_De_rompimento_c") else true,
            condicaoDeRompimentoV = if (root.has("condicao_De_rompimento_v")) root.optBoolean("condicao_De_rompimento_v") else true,
            hFF = root.optString("hFF", "⬛⬛⬛⬛⬛⬛⬛⬛⬛[ RESULTADO ]⬛⬛⬛⬛⬛⬛⬛⬛⬛"),
            mony = root.optString("mony", " Meticais "),
            cambio = root.optDouble("CAMBIO", 64.0)
        )
    }

    fun generateEaConfigJson(config: EaConfigEntity): String {
        val json = org.json.JSONObject()
        json.put("mt5AccountId", config.mt5IdConta)
        json.put("lJJ", config.lJJ)
        json.put("SENHA", config.senhaEa)
        json.put("aYY", config.aYY)
        json.put("ESQUEMA_CORES_ENUM", config.esquemaCoresEnum)
        json.put("cor_de_canal", config.corDeCanal)
        json.put("cor_de_linhas", config.corDeLinhas)
        json.put("corr_de_equador", config.corrDeEquador)
        json.put("sJJ", config.sJJ)
        json.put("LINHAS_DE_EQUADOR", config.linhasDeEquador)
        json.put("TREND", config.tendenciaValue)
        json.put("M_equador_alta", config.mEquadorAlta)
        json.put("M_equador_baixa", config.mEquadorBaixa)
        json.put("xxx", config.xxx)
        json.put("TEMA", config.tema)
        json.put("ESTRATÉGIA", config.estrategiaValue)
        json.put("virada_de_jogo", config.viradaDeJogo)
        json.put("Nives", config.nives)
        json.put("Costurar", config.costurar)
        json.put("OperationalPeriod", config.periodoOperacional)
        json.put("lot", config.lot)
        json.put("dS", config.dS)
        json.put("EA_ATIVO", config.eaAtivo)
        json.put("EA_AUTO", config.eaAuto)
        json.put("AUTO_PERIOD", config.periodoAuto)
        json.put("AUTO_SURFADA", config.autoSurfada)
        json.put("SESSAO_ASIA_TOQUIO", config.sessaoAsiaToquio)
        json.put("SESSAO_LONDRES", config.sessaoLondres)
        json.put("SESSAO_NOVA_YORQUI", config.sessaoNovaYorqui)
        json.put("EXPANSAO_MINIMA", config.expansaoMinima)
        json.put("EXPANSAO_MAXIMA", config.expansaoMaxima)
        json.put("dSS", config.dSS)
        json.put("compra", config.compra)
        json.put("venda", config.venda)
        json.put("santo", config.santo)
        json.put("dedo", config.dedo)
        json.put("posicaoTake", config.posicaoTake)
        json.put("buy_take", config.buyTake)
        json.put("sell_take", config.sellTake)
        json.put("fDD", config.fDD)
        json.put("SALDO", config.saldoDemo)
        json.put("GERENCIAMENTO_DE_RISCO_DIARIO", config.gerenciamentoDeRiscoDiario)
        json.put("porcentos", config.porcentos)
        json.put("poercentosg", config.porcentosg)
        json.put("GERENCIAMENTO_DE_RISCO_SEMANAL", config.gerenciamentoDeRiscoSemanal)
        json.put("PORCENTOO", config.porcentoo)
        json.put("PORCENTOSS", config.porcentoss)
        json.put("gG", config.gG)
        json.put("GMAIL", config.gmail)
        json.put("notific", config.notific)
        json.put("ativar_ou_desativar_compra", config.ativarOuDesativarCompra)
        json.put("ativar_ou_desativar_venda", config.ativarOuDesativarVenda)
        json.put("Modify_Sl_For_OxO", config.modificarSlParaOxO)
        json.put("condicao_De_rompimento_c", config.condicaoDeRompimentoC)
        json.put("condicao_De_rompimento_v", config.condicaoDeRompimentoV)
        json.put("hFF", config.hFF)
        json.put("mony", config.mony)
        json.put("CAMBIO", config.cambio)
        return json.toString(2)
    }

    suspend fun syncAdminTemplatesFile(jsonContent: String) = withContext(Dispatchers.IO) {
        configManager.adminTemplatesJson = jsonContent
        val pathIndices = "dados/indices/instrucoes_admin_templates.json"
        val pathSub = "dados/instrucoes/instrucoes_admin_templates.json"
        val pathRoot = "dados/instrucoes_admin_templates.json"
        
        try {
            syncRawFile(pathIndices, jsonContent, "Portal Fimaster: Publicação de Templates do Administrador Master")
            try {
                syncRawFile(pathSub, jsonContent, "Portal Fimaster: Espelho em instrucoes")
            } catch (ignored: Throwable) {
                Log.w(TAG, "Aviso ao atualizar espelho em $pathSub: ${ignored.message}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Tentativa em $pathIndices falhou: ${e.message}. Tentando caminhos secundários...")
            try {
                syncRawFile(pathSub, jsonContent, "Portal Fimaster: Publicação de Templates do Administrador Master")
            } catch (inner: Throwable) {
                try {
                    syncRawFile(pathRoot, jsonContent, "Portal Fimaster: Publicação de Templates do Administrador Master")
                } catch (lastErr: Throwable) {
                    Log.e(TAG, "Erro ao publicar templates no servidor remoto: ${lastErr.message}")
                    throw lastErr
                }
            }
        }
    }

    suspend fun readAdminTemplatesFromServer(): String? = withContext(Dispatchers.IO) {
        val pathIndices = "dados/indices/instrucoes_admin_templates.json"
        val pathSub = "dados/instrucoes/instrucoes_admin_templates.json"
        val pathRoot = "dados/instrucoes_admin_templates.json"
        val remoteContent = fetchRemoteJsonContent(pathIndices)
            ?: fetchRemoteJsonContent(pathSub)
            ?: fetchRemoteJsonContent(pathRoot)
        if (!remoteContent.isNullOrEmpty()) {
            configManager.adminTemplatesJson = remoteContent
            return@withContext remoteContent
        }
        val localContent = configManager.adminTemplatesJson
        if (localContent.isNotEmpty()) {
            return@withContext localContent
        }
        return@withContext null
    }

    suspend fun readEaConfigFromServer(mt5Id: String): EaConfigEntity? = withContext(Dispatchers.IO) {
        val path = "dados/parametros/$mt5Id.json"
        val json = fetchRemoteJsonContent(path)
        if (json != null) {
            try {
                val config = parseEaConfigFromJson(json, mt5Id)
                eaConfigDao.insertEaConfig(config)
                return@withContext config
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao analisar configuração do EA a partir do JSON remoto: ${e.message}")
            }
        }
        return@withContext null
    }
}
