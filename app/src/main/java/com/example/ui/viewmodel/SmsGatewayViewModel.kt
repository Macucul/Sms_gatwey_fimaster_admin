package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.SmsGatewayRepository
import com.example.service.GatewayService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class SmsGatewayViewModel(application: Application) : AndroidViewModel(application) {
    val repo = SmsGatewayRepository(application.applicationContext)

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (repo.isSyncConfigured()) {
                    Log.d("SmsGatewayViewModel", "Internet disponível! Sincronizando fila local...")
                    viewModelScope.launch {
                        try {
                            repo.syncUnsyncedUsers()
                        } catch (e: Exception) {
                            Log.e("SmsGatewayViewModel", "Falha ao sincronizar: ${e.message}")
                        }
                    }
                } else {
                    Log.d("SmsGatewayViewModel", "Internet disponível. Sincronização remota pendente de configuração de credenciais.")
                }
            }
        }
        try {
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            connectivityManager?.registerNetworkCallback(builder.build(), networkCallback!!)
        } catch (e: Exception) {
            Log.e("SmsGatewayViewModel", "Erro ao registrar callback de rede: ${e.message}")
        }

        // Verificação automática de validade de licença e lembretes diários no arranque (Point 1 & 2)
        viewModelScope.launch {
            try {
                repo.ensureDefaultLicenseTiers()
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao inicializar licenças padrão: ${e.message}")
            }
            try {
                repo.checkAndExpirateLicenses()
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao executar verificação automática de expiração no arranque: ${e.message}")
            }
            try {
                val todayStr = repo.getCurrentDateStr()
                if (repo.configManager.lastReminderDate != todayStr) {
                    repo.sendLicenseReminders()
                    repo.configManager.lastReminderDate = todayStr
                }
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao verificar lembretes no arranque: ${e.message}")
            }
        }
    }

    // License Tiers State
    val licenseTiers: StateFlow<List<LicenseTierEntity>> = repo.allLicenseTiers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveLicenseTier(tier: LicenseTierEntity, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repo.saveLicenseTier(tier)
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao salvar licença: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun updateLicenseTier(
        id: String,
        nome: String,
        valor: Double,
        diasValidade: Int,
        descricao: String,
        templates: Boolean = false,
        capturaTela: Boolean = false,
        graficoPatrimonio: Boolean = false,
        audio: Boolean = false,
        vincularConta: Int = 1,
        sala: Boolean = false,
        whatsappLink: String = "",
        telegramLink: String = "",
        qrCodeBytes: ByteArray? = null,
        qrCodeLink: String = "",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val tier = LicenseTierEntity(
                    id = id,
                    nome = nome,
                    valor = valor,
                    diasValidade = diasValidade,
                    descricao = descricao,
                    templates = templates,
                    capturaTela = capturaTela,
                    graficoPatrimonio = graficoPatrimonio,
                    audio = audio,
                    vincularConta = vincularConta,
                    sala = sala,
                    whatsappLink = whatsappLink,
                    telegramLink = telegramLink,
                    qrCodeBytes = qrCodeBytes,
                    qrCodeLink = qrCodeLink,
                    updatedAt = System.currentTimeMillis()
                )
                repo.saveLicenseTier(tier)
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao atualizar licença: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun syncAllLicenseTiers(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repo.syncLicenseTiers()
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao sincronizar todas as licenças: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("TODOS") // TODOS, HOJE, 7_DIAS, 30_DIAS, EXPIRADAS, RENOVADAS
    val selectedFilter = _selectedFilter.asStateFlow()
    
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    // Users Flow mapped to search query and selected filter
    val usersList: StateFlow<List<UserEntity>> = combine(
        repo.allUsers,
        _searchQuery,
        _selectedFilter
    ) { users, query, filter ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = repo.getCurrentDateStr()
        val today = try { sdf.parse(todayStr) } catch(e: Exception) { null }
        
        users.filter { user ->
            // Apply Search Query first
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                user.nome.contains(query, ignoreCase = true) || 
                user.telefone.contains(query, ignoreCase = true) || 
                user.idUsuario.contains(query, ignoreCase = true) || 
                user.mt5IdConta.contains(query, ignoreCase = true)
            }
            if (!matchesQuery) return@filter false
            
            // Apply License Filter
            when (filter) {
                "HOJE" -> {
                    user.licencaValidade.startsWith(todayStr)
                }
                "7_DIAS" -> {
                    if (user.licencaValidade.isEmpty() || today == null) false else {
                        val exp = repo.parseDateOrDateTime(user.licencaValidade)
                        if (exp == null) false else {
                            val diffDays = ((exp.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                            diffDays in 0..7
                        }
                    }
                }
                "30_DIAS" -> {
                    if (user.licencaValidade.isEmpty() || today == null) false else {
                        val exp = repo.parseDateOrDateTime(user.licencaValidade)
                        if (exp == null) false else {
                            val diffDays = ((exp.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                            diffDays in 0..30
                        }
                    }
                }
                "EXPIRADAS" -> {
                    user.status == "EXPIRADO" || (!user.licencaAtiva && user.licencaValidade.isNotEmpty())
                }
                "RENOVADAS" -> {
                    user.totalRenovacoes > 0
                }
                else -> true // TODOS
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class LicenseDashboardMetrics(
        val activeCount: Int = 0,
        val expiredCount: Int = 0,
        val nearExpirationCount: Int = 0,
        val renewalsThisMonthCount: Int = 0
    )

    val licenseMetrics: StateFlow<LicenseDashboardMetrics> = repo.allUsers.map { users ->
        val todayStr = repo.getCurrentDateStr()
        val today = repo.parseDateOrDateTime(todayStr)
        
        var active = 0
        var expired = 0
        var nearExp = 0
        var renewalsMonth = 0
        
        val currentMonthPrefix = if (todayStr.length >= 7) todayStr.substring(0, 7) else ""
        
        users.forEach { user ->
            val hasValidade = user.licencaValidade.isNotEmpty()
            val exp = if (hasValidade) {
                repo.parseDateOrDateTime(user.licencaValidade)
            } else null
            
            val isExpired = user.status == "EXPIRADO" || (today != null && exp != null && today.after(exp))
            
            if (user.licencaAtiva && !isExpired) {
                active++
            }
            if (isExpired && hasValidade) {
                expired++
            }
            
            if (user.licencaAtiva && !isExpired && today != null && exp != null) {
                val diffDays = ((exp.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                if (diffDays in 0..30) {
                    nearExp++
                }
            }
            
            if (user.totalRenovacoes > 0 && user.ultimaRenovacao.isNotEmpty()) {
                if (user.ultimaRenovacao.startsWith(currentMonthPrefix)) {
                    renewalsMonth++
                }
            }
        }
        
        LicenseDashboardMetrics(
            activeCount = active,
            expiredCount = expired,
            nearExpirationCount = nearExp,
            renewalsThisMonthCount = renewalsMonth
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LicenseDashboardMetrics())

    // New Accumulator (Pending) & Refund lists
    val pendingPaymentsList: StateFlow<List<PendingPaymentEntity>> = repo.allPending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val refundsList: StateFlow<List<RefundEntity>> = repo.allRefunds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Statistics
    val totalUsersCount: StateFlow<Int> = repo.totalUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val approvedUsersCount: StateFlow<Int> = repo.approvedUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingUsersCount: StateFlow<Int> = repo.pendingUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val rejectedUsersCount: StateFlow<Int> = repo.rejectedUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastUserReceived: StateFlow<UserEntity?> = repo.lastUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalSentSms: StateFlow<Int> = repo.sentSmsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalFailedSms: StateFlow<Int> = repo.failedSmsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRefundsCount: StateFlow<Int> = repo.totalRefundsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingRefundsCount: StateFlow<Int> = repo.pendingRefundsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Unsynced count states
    val unsyncedUsersCount: StateFlow<Int> = repo.unsyncedUsersCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedPendingCount: StateFlow<Int> = repo.unsyncedPendingCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedRefundsCount: StateFlow<Int> = repo.unsyncedRefundsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPendingSyncCount: StateFlow<Int> = combine(
        repo.unsyncedUsersCountFlow,
        repo.unsyncedPendingCountFlow,
        repo.unsyncedRefundsCountFlow
    ) { users, pendings, refunds ->
        users + pendings + refunds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Audit Logs & Outgoing Logs Flows
    val auditLogs: StateFlow<List<AuditLogEntity>> = repo.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smsLogs: StateFlow<List<SmsLogEntity>> = repo.allSmsLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminUid: StateFlow<String?> = flow {
        var uid = repo.getCurrentAdminUid()
        emit(uid)
        while (uid == null) {
            kotlinx.coroutines.delay(1500)
            uid = repo.getCurrentAdminUid()
            if (uid != null) {
                emit(uid)
                break
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Settings States backed by ConfigManager
    val configManager = repo.configManager

    private val _saldoMinimo = MutableStateFlow(configManager.saldoMinimo)
    val saldoMinimo = _saldoMinimo.asStateFlow()

    private val _valorMinimoAtivacao = MutableStateFlow(configManager.valorMinimoAtivacao)
    val valorMinimoAtivacao = _valorMinimoAtivacao.asStateFlow()

    private val _validadeMeses = MutableStateFlow(configManager.validadeMeses)
    val validadeMeses = _validadeMeses.asStateFlow()

    private val _syncMode = MutableStateFlow(configManager.syncMode)
    val syncMode = _syncMode.asStateFlow()

    private val _githubToken = MutableStateFlow(configManager.githubToken)
    val githubToken = _githubToken.asStateFlow()

    private val _githubRepo = MutableStateFlow(configManager.githubRepo)
    val githubRepo = _githubRepo.asStateFlow()

    private val _githubBranch = MutableStateFlow(configManager.githubBranch)
    val githubBranch = _githubBranch.asStateFlow()

    private val _githubPath = MutableStateFlow(configManager.githubPath)
    val githubPath = _githubPath.asStateFlow()

    private val _fastApiUrl = MutableStateFlow(configManager.fastApiUrl)
    val fastApiUrl = _fastApiUrl.asStateFlow()

    private val _fastApiToken = MutableStateFlow(configManager.fastApiToken)
    val fastApiToken = _fastApiToken.asStateFlow()

    private val _autoSendSms = MutableStateFlow(configManager.autoSendSms)
    val autoSendSms = _autoSendSms.asStateFlow()

    private val _autoSync = MutableStateFlow(configManager.autoSync)
    val autoSync = _autoSync.asStateFlow()

    private val _customRegex = MutableStateFlow(configManager.customRegex)
    val customRegex = _customRegex.asStateFlow()

    private val _filterOfficialSenders = MutableStateFlow(configManager.filterOfficialSenders)
    val filterOfficialSenders = _filterOfficialSenders.asStateFlow()

    private val _officialSendersList = MutableStateFlow(configManager.officialSendersList)
    val officialSendersList = _officialSendersList.asStateFlow()

    private val _maxRefundDays = MutableStateFlow(configManager.maxRefundDays)
    val maxRefundDays = _maxRefundDays.asStateFlow()

    private val _backgroundSyncEnabled = MutableStateFlow(configManager.backgroundSyncEnabled)
    val backgroundSyncEnabled = _backgroundSyncEnabled.asStateFlow()

    private val _syncIntervalMinutes = MutableStateFlow(configManager.syncIntervalMinutes)
    val syncIntervalMinutes = _syncIntervalMinutes.asStateFlow()

    private val _dadosVersion = MutableStateFlow(configManager.dadosVersion)
    val dadosVersion = _dadosVersion.asStateFlow()

    private val _ultimaAtualizacaoDados = MutableStateFlow(configManager.ultimaAtualizacaoDados)
    val ultimaAtualizacaoDados = _ultimaAtualizacaoDados.asStateFlow()

    private val _smsBindingEnabled = MutableStateFlow(configManager.smsBindingEnabled)
    val smsBindingEnabled = _smsBindingEnabled.asStateFlow()

    private val _discountEnabled = MutableStateFlow(configManager.discountEnabled)
    val discountEnabled = _discountEnabled.asStateFlow()

    private val _discountText = MutableStateFlow(configManager.discountText)
    val discountText = _discountText.asStateFlow()

    private val _discountPercent = MutableStateFlow(configManager.discountPercent)
    val discountPercent = _discountPercent.asStateFlow()

    private val _settingsPassword = MutableStateFlow(configManager.settingsPassword)
    val settingsPassword = _settingsPassword.asStateFlow()

    // Foreground service monitor state
    private val _isServiceActive = MutableStateFlow(GatewayService.isServiceRunning)
    val isServiceActive = _isServiceActive.asStateFlow()

    // Sync Indicator Active Status Flow
    val isSyncing: StateFlow<Boolean> = combine(
        usersList,
        pendingPaymentsList,
        refundsList
    ) { users, pendings, refunds ->
        users.any { it.syncStatus == "SYNCING" } ||
        pendings.any { it.syncStatus == "SYNCING" } ||
        refunds.any { it.syncStatus == "SYNCING" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun refreshServiceState() {
        _isServiceActive.value = GatewayService.isServiceRunning
    }

    fun toggleBackgroundService(context: Context) {
        if (GatewayService.isServiceRunning) {
            GatewayService.stopService(context)
        } else {
            GatewayService.startService(context)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            _isServiceActive.value = GatewayService.isServiceRunning
        }
    }

    fun simulateSmsReceived(sender: String, messageText: String) {
        viewModelScope.launch {
            try {
                repo.processIncomingSms(sender, messageText)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao simular SMS: ${e.message}")
            }
        }
    }

    fun retryUnsyncedQueue() {
        viewModelScope.launch {
            repo.syncUnsyncedUsers()
            try {
                repo.buildAndSyncMt5Index()
                repo.buildAndSyncTelefonesIndex()
            } catch (e: Exception) {
                // Logged in repo
            }
        }
    }

    fun rebuildAndSyncIndices(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.buildAndSyncMt5Index()
                repo.buildAndSyncTelefonesIndex()
                onResult(true, "Índices MT5 e Telefones reconstruídos e sincronizados com sucesso!")
            } catch (e: Exception) {
                onResult(false, "Erro ao reconstruir índices: ${e.message}")
            }
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            repo.clearDatabase()
        }
    }

    fun exportCsvData(): String {
        return repo.exportUsersToCsv()
    }

    /**
     * Updates and persists admin settings into SharedPreferences config storage.
     */
    fun updateSettings(
        mMinimo: Double,
        mMinimoAtivacao: Double,
        mValidadeMeses: Int,
        mMode: String,
        ghToken: String,
        ghRepo: String,
        ghBranch: String,
        ghPath: String,
        fUrl: String,
        fToken: String,
        aSms: Boolean,
        aSync: Boolean,
        cRegex: String,
        filterOfficial: Boolean,
        officialSenders: String,
        maxRefundDaysVal: Int,
        bgSyncEnabled: Boolean,
        syncIntervalMin: Int,
        smsBindingEnabledVal: Boolean = true,
        discountEnabledVal: Boolean = false,
        discountTextVal: String = "DESCONTO",
        discountPercentVal: Double = 10.0,
        settingsPasswordVal: String = "1234"
    ) {
        configManager.saldoMinimo = mMinimo
        configManager.valorMinimoAtivacao = mMinimoAtivacao
        configManager.validadeMeses = mValidadeMeses
        configManager.syncMode = mMode
        configManager.githubToken = ghToken
        configManager.githubRepo = ghRepo
        configManager.githubBranch = ghBranch
        configManager.githubPath = ghPath
        configManager.fastApiUrl = fUrl
        configManager.fastApiToken = fToken
        configManager.autoSendSms = aSms
        configManager.autoSync = aSync
        configManager.customRegex = cRegex
        configManager.filterOfficialSenders = filterOfficial
        configManager.officialSendersList = officialSenders
        configManager.maxRefundDays = maxRefundDaysVal
        configManager.backgroundSyncEnabled = bgSyncEnabled
        configManager.syncIntervalMinutes = syncIntervalMin
        configManager.smsBindingEnabled = smsBindingEnabledVal
        configManager.discountEnabled = discountEnabledVal
        configManager.discountText = discountTextVal
        configManager.discountPercent = discountPercentVal
        configManager.settingsPassword = settingsPasswordVal

        // Push values to live flows
        _saldoMinimo.value = mMinimo
        _valorMinimoAtivacao.value = mMinimoAtivacao
        _validadeMeses.value = mValidadeMeses
        _syncMode.value = mMode
        _githubToken.value = ghToken
        _githubRepo.value = ghRepo
        _githubBranch.value = ghBranch
        _githubPath.value = ghPath
        _fastApiUrl.value = fUrl
        _fastApiToken.value = fToken
        _autoSendSms.value = aSms
        _autoSync.value = aSync
        _customRegex.value = cRegex
        _filterOfficialSenders.value = filterOfficial
        _officialSendersList.value = officialSenders
        _maxRefundDays.value = maxRefundDaysVal
        _backgroundSyncEnabled.value = bgSyncEnabled
        _syncIntervalMinutes.value = syncIntervalMin
        _smsBindingEnabled.value = smsBindingEnabledVal
        _discountEnabled.value = discountEnabledVal
        _discountText.value = discountTextVal
        _discountPercent.value = discountPercentVal
        _settingsPassword.value = settingsPasswordVal
        _dadosVersion.value = configManager.dadosVersion
        _ultimaAtualizacaoDados.value = configManager.ultimaAtualizacaoDados

        // Trigger loop updates in active service instance immediately
        try {
            GatewayService.instance?.startPeriodicSync()
        } catch (e: Exception) {
            Log.e("SmsGatewayViewModel", "Falha ao atualizar agendamento do serviço: ${e.message}")
        }

        viewModelScope.launch {
            val currentUid = repo.getCurrentAdminUid() ?: "Desconhecido"
            repo.auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "ADMIN_CONFIG",
                    messageText = "Atualização de Configurações",
                    isMatched = true,
                    extractedData = null,
                    status = "SUCCESS",
                    details = "Administrador atualizou as configurações do sistema. [Admin UID: $currentUid]"
                )
            )
            repo.syncConfig()
        }
    }

    /**
     * Reimbursements operations
     */
    fun submitRefundRequest(txId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = repo.requestRefund(txId)
                onResult(result)
            } catch (e: Exception) {
                onResult("Erro: ${e.message}")
            }
        }
    }

    fun approveRefund(refundId: String, adminUser: String) {
        viewModelScope.launch {
            try {
                repo.approveRefund(refundId, adminUser)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao aprovar reembolso: ${e.message}")
            }
        }
    }

    fun confirmRefundPaid(refundId: String) {
        viewModelScope.launch {
            try {
                repo.confirmRefundPaid(refundId)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao confirmar pagamento de reembolso: ${e.message}")
            }
        }
    }

    fun rejectRefund(refundId: String) {
        viewModelScope.launch {
            try {
                repo.rejectRefund(refundId)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao rejeitar reembolso: ${e.message}")
            }
        }
    }

    /**
     * Updates user MT5 account info & License while enforcing unique constraints (MT5 duplicates blocked).
     */
    fun updateUserMt5(
        user: UserEntity,
        mt5Id: String,
        licencaAtiva: Boolean,
        validadeStr: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (mt5Id.isNotEmpty()) {
                val duplicateUser = repo.userDao.getUserByMt5(mt5Id)
                if (duplicateUser != null && duplicateUser.idUsuario != user.idUsuario) {
                    onResult(false, "Falha: Conta MT5 já está vinculada ao utilizador ${duplicateUser.idUsuario}.")
                    return@launch
                }
            }
            
            val updatedUser = user.copy(
                mt5Registrado = mt5Id.isNotEmpty(),
                mt5IdConta = mt5Id,
                licencaAtiva = licencaAtiva,
                licencaValidade = validadeStr,
                status = if (licencaAtiva) "ATIVO" else "AGUARDANDO_ATIVACAO",
                ultimaAtualizacao = repo.getCurrentTimestampIso()
            )
            
            repo.userDao.insertUser(updatedUser)

            val currentUid = repo.getCurrentAdminUid() ?: "Desconhecido"
            repo.auditLogDao.insertLog(
                AuditLogEntity(
                    sender = "ADMIN_MANUAL",
                    messageText = "Alteração manual do utilizador ${user.idUsuario}",
                    isMatched = true,
                    extractedData = "{ \"idUsuario\": \"${user.idUsuario}\", \"mt5IdConta\": \"$mt5Id\", \"licencaAtiva\": $licencaAtiva }",
                    status = "SUCCESS",
                    details = "Administrador atualizou a conta MT5 para '$mt5Id' e licença ativa para '$licencaAtiva' (Validade: $validadeStr). [Admin UID: $currentUid]"
                )
            )
            
            if (repo.configManager.autoSync) {
                repo.syncUser(updatedUser)
                repo.buildAndSyncMt5Index()
                repo.buildAndSyncTelefonesIndex()
            }
            
            onResult(true, "Informações da conta MT5 atualizadas com sucesso!")
        }
    }

    // ==========================================
    // CLIENT PORTAL (PORTAL FIMASTER) METHODS
    // ==========================================
    private val _loggedClient = MutableStateFlow<UserEntity?>(null)
    val loggedClient = _loggedClient.asStateFlow()

    private val _clientPendingPayment = MutableStateFlow<PendingPaymentEntity?>(null)
    val clientPendingPayment = _clientPendingPayment.asStateFlow()

    private val _clientRefund = MutableStateFlow<RefundEntity?>(null)
    val clientRefund = _clientRefund.asStateFlow()

    private val _clientEaConfig = MutableStateFlow<EaConfigEntity?>(null)
    val clientEaConfig = _clientEaConfig.asStateFlow()

    fun loginClient(phone: String, rawPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repo.loginPortalFimaster(phone, rawPass)
            if (res.first && res.second != null) {
                _loggedClient.value = res.second
                refreshClientData()
                onResult(true, "Login efetuado com sucesso!")
            } else {
                // If it fails, let's try a local fallback just in case or return the final result
                var user = repo.userDao.getUserByPhone(phone.trim()) ?: repo.userDao.getUserByPhone("+" + phone.trim())
                if (user == null) {
                    onResult(false, "Usuário não registrado no índice ou senha inválida.")
                    return@launch
                }
                val storedHashParts = user.senhaHash.split(":")
                val actualStoredHash = storedHashParts.firstOrNull() ?: ""
                val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
                val enteredHash = com.example.util.SecurityUtils.hashSha256(rawPass, saltToUse)
                if (enteredHash == actualStoredHash) {
                    _loggedClient.value = user
                    refreshClientData()
                    onResult(true, "Login efetuado com sucesso!")
                } else {
                    onResult(false, "Senha incorreta. Por favor, tente novamente.")
                }
            }
        }
    }

    fun loginEaMql5(mt5Id: String, rawPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repo.loginEaMql5(mt5Id, rawPass)
            onResult(res.first, res.second)
        }
    }

    fun logoutClient() {
        _loggedClient.value = null
        _clientPendingPayment.value = null
        _clientRefund.value = null
        _clientEaConfig.value = null
    }

    fun refreshClientData() {
        val user = _loggedClient.value ?: return
        viewModelScope.launch {
            // Refresh user from local database to get most up-to-date values
            val freshUser = repo.userDao.getUserById(user.idUsuario)
            if (freshUser != null) {
                _loggedClient.value = freshUser
                
                // Get partial payments
                _clientPendingPayment.value = repo.pendingPaymentDao.getPendingByPhone(freshUser.telefone)
                
                // Get refund details
                _clientRefund.value = repo.refundDao.getRefundByTransactionId(freshUser.idTransacao)

                // Load or initialize EA configuration
                if (freshUser.mt5IdConta.isNotEmpty()) {
                    val config = repo.getEaConfig(freshUser.mt5IdConta)
                    if (config != null) {
                        _clientEaConfig.value = config
                    } else {
                        val defaultConfig = EaConfigEntity(mt5IdConta = freshUser.mt5IdConta)
                        repo.eaConfigDao.insertEaConfig(defaultConfig)
                        _clientEaConfig.value = defaultConfig
                    }
                } else {
                    _clientEaConfig.value = null
                }
            }
        }
    }

    fun saveAndSyncClientEaConfig(config: EaConfigEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.saveAndSyncEaConfig(config)
                _clientEaConfig.value = config
                onResult(true, "Parâmetros do EA salvos e sincronizados com sucesso!")
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao salvar e sincronizar parâmetros do EA: ${e.message}")
                onResult(false, "Erro ao sincronizar com o GitHub: ${e.message ?: "Conexão falhou"}")
            }
        }
    }

    fun publishAdminTemplateJson(templateJson: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.syncAdminTemplatesFile(templateJson)
                onResult(true, "Templates do Administrador Master publicados com sucesso no nó /dados/indices/instrucoes_admin_templates.json!")
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao publicar templates do Administrador Master: ${e.message}")
                onResult(false, "Erro ao publicar templates: ${e.message ?: "Falha na conexão"}")
            }
        }
    }

    fun updateClientMt5Id(newMt5Id: String, onResult: (Boolean, String) -> Unit) {
        val user = _loggedClient.value ?: return
        viewModelScope.launch {
            val trimmedMt5 = newMt5Id.trim()
            if (trimmedMt5.isNotEmpty()) {
                val duplicateUser = repo.userDao.getUserByMt5(trimmedMt5)
                if (duplicateUser != null && duplicateUser.idUsuario != user.idUsuario) {
                    onResult(false, "Falha: Conta MT5 já está vinculada a outro utilizador.")
                    return@launch
                }
            }
            
            val updatedUser = user.copy(
                mt5Registrado = trimmedMt5.isNotEmpty(),
                mt5IdConta = trimmedMt5,
                ultimaAtualizacao = repo.getCurrentTimestampIso()
            )
            
            repo.userDao.insertUser(updatedUser)
            _loggedClient.value = updatedUser

            refreshClientData()
            
            if (repo.configManager.autoSync) {
                repo.syncUser(updatedUser)
                repo.buildAndSyncMt5Index()
            }
            onResult(true, "Conta MT5 vinculada com sucesso!")
        }
    }

    fun changeClientPassword(currentPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        val user = _loggedClient.value ?: return
        viewModelScope.launch {
            val storedHashParts = user.senhaHash.split(":")
            val actualStoredHash = storedHashParts.firstOrNull() ?: ""
            val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
            val enteredHash = com.example.util.SecurityUtils.hashSha256(currentPass, saltToUse)
            
            if (enteredHash != actualStoredHash) {
                onResult(false, "Senha atual incorreta.")
                return@launch
            }
            
            val newSalt = com.example.util.SecurityUtils.generateSalt()
            val newHashedPassword = com.example.util.SecurityUtils.hashSha256(newPass.trim(), newSalt) + ":" + newSalt
            
            val updatedUser = user.copy(
                senhaHash = newHashedPassword,
                salt = newSalt,
                ultimaAtualizacao = repo.getCurrentTimestampIso()
            )
            
            repo.userDao.insertUser(updatedUser)
            _loggedClient.value = updatedUser
            
            if (repo.configManager.autoSync) {
                repo.syncUser(updatedUser)
            }
            onResult(true, "Senha alterada com sucesso!")
        }
    }

    fun submitClientRefundRequest(onResult: (String) -> Unit) {
        val user = _loggedClient.value ?: return
        viewModelScope.launch {
            val result = repo.requestRefund(user.idTransacao)
            refreshClientData()
            onResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e("SmsGatewayViewModel", "Erro ao desregistrar callback de rede: ${e.message}")
            }
        }
    }
}
