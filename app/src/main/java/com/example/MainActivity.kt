package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SmsGatewayViewModel
import com.example.util.QrCodeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: SmsGatewayViewModel = viewModel()
    
    // Check background service state on launch/view binding
    LaunchedEffect(Unit) {
        viewModel.refreshServiceState()
    }

    // State bindings
    val activeTab = remember { mutableIntStateOf(0) }
    val showLockDialog = remember { mutableStateOf(false) }
    val passwordInput = remember { mutableStateOf("") }
    val passwordError = remember { mutableStateOf(false) }
    val isUnlocked = remember { mutableStateOf(false) }
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val totalPendingSyncCount by viewModel.totalPendingSyncCount.collectAsStateWithLifecycle()

    // Permissions Handling
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedCount = results.values.count { it }
        if (grantedCount == results.size) {
            Toast.makeText(context, "Todas as permissões essenciais foram concedidas!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Algumas permissões de SMS foram negadas. O aplicativo pode não interceptar mensagens corretamente.", Toast.LENGTH_LONG).show()
        }
    }

    // Checking permissions on layout entering
    LaunchedEffect(Unit) {
        val hasPermissions = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            permissionsLauncher.launch(permissionsToRequest)
        }
    }

    if (showLockDialog.value) {
        AlertDialog(
            onDismissRequest = { 
                showLockDialog.value = false
                passwordInput.value = ""
                passwordError.value = false
            },
            title = {
                Text("Acesso Restrito", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Digite a senha de segurança para acessar as definições:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput.value,
                        onValueChange = { 
                            passwordInput.value = it
                            passwordError.value = false
                        },
                        label = { Text("Senha") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        isError = passwordError.value,
                        modifier = Modifier.fillMaxWidth().testTag("security_password_input")
                    )
                    if (passwordError.value) {
                        Text(
                            "Senha incorreta!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val correctPassword = viewModel.repo.configManager.settingsPassword
                        if (passwordInput.value == correctPassword) {
                            isUnlocked.value = true
                            showLockDialog.value = false
                            activeTab.intValue = 3
                            passwordInput.value = ""
                        } else {
                            passwordError.value = true
                        }
                    },
                    modifier = Modifier.testTag("security_confirm_button")
                ) {
                    Text("Entrar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLockDialog.value = false
                        passwordInput.value = ""
                        passwordError.value = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SMS Gateway Pro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceActive) Color(0xFF4CAF50) else Color(0xFFF44336))
                            )
                            Text(
                                text = if (isServiceActive) "Monitoramento Ativo" else "Monitoramento Inativo",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // Visual Synchronization status indicator icon
                    if (isSyncing) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sync")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Sincronizando...",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp)
                                .align(Alignment.CenterVertically)
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.retryUnsyncedQueue()
                                Toast.makeText(context, "A forçar sincronização da fila de reenvio...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("force_sync_btn")
                        ) {
                            Box {
                                Icon(
                                    imageVector = if (totalPendingSyncCount > 0) Icons.Outlined.CloudOff else Icons.Outlined.CloudQueue,
                                    contentDescription = "Sincronização",
                                    tint = if (totalPendingSyncCount > 0) Color(0xFFEF6C00) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (totalPendingSyncCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF6C00))
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeTab.intValue == 0,
                    onClick = { 
                        activeTab.intValue = 0 
                        isUnlocked.value = false
                    },
                    icon = { Icon(if (activeTab.intValue == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = activeTab.intValue == 1,
                    onClick = { 
                        activeTab.intValue = 1 
                        isUnlocked.value = false
                    },
                    icon = { Icon(if (activeTab.intValue == 1) Icons.Filled.People else Icons.Outlined.People, contentDescription = "Utilizadores") },
                    label = { Text("Utilizadores", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_utilizadores")
                )
                NavigationBarItem(
                    selected = activeTab.intValue == 2,
                    onClick = { 
                        activeTab.intValue = 2 
                        isUnlocked.value = false
                    },
                    icon = { Icon(if (activeTab.intValue == 2) Icons.Filled.Undo else Icons.Outlined.Undo, contentDescription = "Reembolsos") },
                    label = { Text("Reembolsos", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_reembolsos")
                )
                NavigationBarItem(
                    selected = activeTab.intValue == 3,
                    onClick = { 
                        if (isUnlocked.value) {
                            activeTab.intValue = 3
                        } else {
                            showLockDialog.value = true
                        }
                    },
                    icon = { Icon(if (activeTab.intValue == 3) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Configurações") },
                    label = { Text("Config", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_configuracoes")
                )
                NavigationBarItem(
                    selected = activeTab.intValue == 4,
                    onClick = { 
                        activeTab.intValue = 4 
                        isUnlocked.value = false
                    },
                    icon = { Icon(if (activeTab.intValue == 4) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong, contentDescription = "Logs") },
                    label = { Text("Logs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_logs")
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeTab.intValue,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "screens",
            modifier = Modifier.padding(innerPadding)
        ) { targetViewIndex ->
            when (targetViewIndex) {
                0 -> DashboardScreen(viewModel)
                1 -> UsersScreen(viewModel)
                2 -> RefundsScreen(viewModel)
                3 -> SettingsScreen(viewModel)
                4 -> LogsScreen(viewModel)
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    
    // Connect stats flows
    val totalCount by viewModel.totalUsersCount.collectAsStateWithLifecycle()
    val approvedCount by viewModel.approvedUsersCount.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingUsersCount.collectAsStateWithLifecycle()
    val rejectedCount by viewModel.rejectedUsersCount.collectAsStateWithLifecycle()
    val lastUser by viewModel.lastUserReceived.collectAsStateWithLifecycle()
    val licenseMetrics by viewModel.licenseMetrics.collectAsStateWithLifecycle()
    
    val totalSmsSent by viewModel.totalSentSms.collectAsStateWithLifecycle()
    val totalSmsFailed by viewModel.totalFailedSms.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val adminUidState by viewModel.adminUid.collectAsStateWithLifecycle()

    val pendingList by viewModel.pendingPaymentsList.collectAsStateWithLifecycle()
    val partialPaymentsCount = pendingList.size

    val refundsList by viewModel.refundsList.collectAsStateWithLifecycle()
    val totalRefundsCount = refundsList.size
    val pendingRefundsCount by viewModel.pendingRefundsCount.collectAsStateWithLifecycle()

    val totalPendingSyncCount by viewModel.totalPendingSyncCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    // Breathing pulse transition for the monitoring status
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    // Test operator SMS template simulator helper state
    val simSenderPhone = remember { mutableStateOf("M-Pesa") }
    val simMsgText = remember {
        mutableStateOf(
            "ID da transacao: PP260616.0500.S17516. Recebeste 1,250.00MT de conta 876971842, nome: NICOLAU AFONSO MAGUMANE DADO as 05:00:50 de 16/06/2026. Conteudo: 1250. O saldo da tua conta e 1,253.00MT."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Technical Banner Illustration with polished visual overlays
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.sms_banner),
                    contentDescription = "SMS Gateway Illustration Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Premium Ambient gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 60f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Automatização SMS em Tempo Real",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Processamento e provisionamento local de utilizadores financeiros",
                        color = Color.LightGray.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Active Listening Status Banner Card (Polished with Breathing Pulse)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                }
            ),
            border = BorderStroke(
                1.dp, 
                if (isServiceActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) 
                else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isServiceActive) Color(0xFF10B981).copy(alpha = breathingAlpha) 
                            else Color(0xFFEF4444).copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isServiceActive) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isServiceActive) "Serviço Ativo em Segundo Plano" else "Serviço SMS Desativado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isServiceActive) "Pronto para receber transações e realizar o auto-registro." else "Ative o serviço para monitorar as mensagens financeiras.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { viewModel.toggleBackgroundService(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceActive) Color(0xFFEF4444) else Color(0xFF10B981)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 80.dp, minHeight = 36.dp)
                ) {
                    Text(
                        text = if (isServiceActive) "Parar" else "Iniciar",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Card do ID do Administrador (Firebase UID)
        val adminUidVal = adminUidState
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Card(
            modifier = Modifier.fillMaxWidth().testTag("admin_uid_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = "Ícone do Administrador",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ID do Administrador (UID)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = adminUidVal ?: "A obter ID do Administrador...",
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (adminUidVal != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (adminUidVal != null) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.buildAnnotatedString { append(adminUidVal) })
                            Toast.makeText(context, "ID do Administrador copiado!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar ID do Administrador",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // SECTION 1: VISÃO GERAL DE UTILIZADORES
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Gestão de Clientes",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Total Geral",
                    count = totalCount,
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.People,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Aprovados",
                    count = approvedCount,
                    color = Color(0xFF10B981),
                    icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Pendentes",
                    count = pendingCount,
                    color = Color(0xFFF59E0B),
                    icon = Icons.Filled.Pending,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Rejeitados",
                    count = rejectedCount,
                    color = Color(0xFFEF4444),
                    icon = Icons.Filled.Cancel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // SECTION 2: PROCESSAMENTO FINANCEIRO
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Serviços Financeiros & Reembolsos",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 0.5.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Acumuladores Parciais",
                    count = partialPaymentsCount,
                    color = Color(0xFF8B5CF6),
                    icon = Icons.Filled.LockClock,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Reembolsos Totais",
                    count = totalRefundsCount,
                    color = Color(0xFF14B8A6),
                    icon = Icons.Filled.AssignmentReturn,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Reembolsos Pendentes",
                    count = pendingRefundsCount,
                    color = Color(0xFFEC4899),
                    icon = Icons.Filled.HourglassEmpty,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.weight(1f)) // Balancer for 2-column structure
            }
        }

        // SECTION 3: LICENCIAMENTO EA
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ativações de Licenças (MT5 EA)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Licenças Ativas",
                    count = licenseMetrics.activeCount,
                    color = Color(0xFF10B981),
                    icon = Icons.Filled.Check,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Licenças Expiradas",
                    count = licenseMetrics.expiredCount,
                    color = Color(0xFFEF4444),
                    icon = Icons.Filled.Error,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    title = "Vencimento 30 dias",
                    count = licenseMetrics.nearExpirationCount,
                    color = Color(0xFFF59E0B),
                    icon = Icons.Filled.Warning,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Renovadas este Mês",
                    count = licenseMetrics.renewalsThisMonthCount,
                    color = Color(0xFF3B82F6),
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Fila de Sincronização Local (Offline) Card (Modernized & Adaptive color themes)
        val isWarning = totalPendingSyncCount > 0
        Card(
            modifier = Modifier.fillMaxWidth().testTag("sync_queue_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                                 else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            ),
            border = BorderStroke(
                1.dp, 
                if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) 
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isWarning) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                            contentDescription = "Sync Icon",
                            tint = if (isWarning) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                        )
                        Text(
                            text = "Fila de Sincronização Local",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isWarning) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                        )
                    }
                    if (isWarning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$totalPendingSyncCount Pendentes",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Sincronizado",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isWarning) {
                        "Dispositivo offline ou com transferências pendentes. O app continua registando SMS e salvando no Room local. A sincronização com o GitHub ou FastAPI ocorrerá automaticamente assim que a internet voltar!"
                    } else {
                        "Todos os registros locais (utilizadores, pagamentos acumuladores e reembolsos) foram sincronizados com sucesso na nuvem."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isWarning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.retryUnsyncedQueue()
                            Toast.makeText(context, "A forçar sincronização da fila de reenvio...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End).testTag("sync_queue_retry_btn"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizando...", fontSize = 12.sp, color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Sync Now",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizar Agora", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // SMS Output Statistics (Sent / Failed) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Estatísticas de Respostas SMS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val totalSms = (totalSmsSent + totalSmsFailed).coerceAtLeast(1)
                    val successRate = (totalSmsSent.toFloat() / totalSms.toFloat() * 100).toInt()
                    
                    Box(
                        modifier = Modifier.size(70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { if (totalSmsSent + totalSmsFailed == 0) 1f else totalSmsSent.toFloat() / totalSms.toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFFEF4444),
                            strokeWidth = 6.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (totalSmsSent + totalSmsFailed == 0) "100%" else "$successRate%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(text = "Sucesso", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Text("Enviadas com Sucesso", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$totalSmsSent", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                Text("Falhas de Envio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$totalSmsFailed", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Total de Respostas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${totalSmsSent + totalSmsFailed}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Last received transaction overview widget block
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Último Utilizador Processado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (lastUser != null) {
                    val user = lastUser!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (user.status == "APROVADO" || user.status == "ATIVO") Color(0xFF10B981).copy(alpha = 0.15f) 
                                    else Color(0xFFEF4444).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.status.take(1),
                                color = if (user.status == "APROVADO" || user.status == "ATIVO") Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.nome, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("ID: ${user.idUsuario} • Telef: ${user.telefone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${user.saldo} MT", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                            Text(user.dataRegistro.take(10), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (user.lastSyncMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = user.lastSyncMessage ?: "",
                            fontSize = 11.sp,
                            color = if (user.syncStatus == "SYNCED") Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum utilizador processado ainda.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // SIMULATOR WIDGET CARD (Polished Admin sandbox)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Code, contentDescription = "Simulador", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Simulador Integrado de SMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Permite testar a extração, acumulação e auto-registro sem enviar um SMS real.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = simSenderPhone.value,
                    onValueChange = { simSenderPhone.value = it },
                    label = { Text("Número/Nome Remetente SMS") },
                    textStyle = TextStyle(fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_phone_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Text(
                    text = "Dica: Use 'M-Pesa' ou 'e-Mola' para passar pelo filtro anti-fraude.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = simMsgText.value,
                    onValueChange = { simMsgText.value = it },
                    label = { Text("Mensagem de Transação SMS") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("sim_text_input"),
                    maxLines = 8,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            simSenderPhone.value = "e-Mola"
                            simMsgText.value = "ID da transacao: PP260621.1034.a71088. Recebeste 100.00MT de conta 870836077, nome: NAZIR ALY CASSIMO as 10:34:41 de 21/06/2026. Conteudo: 100. O saldo da tua conta e 100.00MT. Em caso de duvida, liga 100. Obrigado!"
                        },
                        label = { Text("Simular E-mola", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = {
                            simSenderPhone.value = "M-Pesa"
                            simMsgText.value = "Confirmado DFK0KJKFX2Q. Recebeste 15.00MT de 258848548488 - JOSSIAS RAUL aos 20/6/26 as 6:23 AM. O teu novo saldo M-Pesa e de 15.50MT. Em caso de duvida, liga 100. M-Pesa e facil! "
                        },
                        label = { Text("Simular M-pesa", fontSize = 11.sp) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (simSenderPhone.value.isBlank() || simMsgText.value.isBlank()) {
                            Toast.makeText(context, "Insira um número e texto de mensagem válidos.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.simulateSmsReceived(simSenderPhone.value, simMsgText.value)
                            Toast.makeText(context, "SMS simulado enviado ao processador!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_simulation_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Simulate", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    ToUpperCaseText("Simular Recebimento SMS")
                }
            }
        }

        // CARD: AUTHENTICATION FLOW SIMULATOR (MQL5 EA & PORTAL FIMASTER)
        val selectedAuthSimTab = remember { mutableStateOf(0) } // 0 = EA MQL5, 1 = Portal FiMaster
        val simMt5Id = remember { mutableStateOf("") }
        val simEaPassword = remember { mutableStateOf("") }
        val simPhoneNum = remember { mutableStateOf("") }
        val simPortalPassword = remember { mutableStateOf("") }
        val authLogs = remember { mutableStateListOf<String>() }
        val isAuthSuccess = remember { mutableStateOf<Boolean?>(null) }
        val scope = rememberCoroutineScope()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("auth_simulator_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Simulador Auth", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Simulador de Autenticação",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Teste e valide o fluxo de login sequencial em tempo real conforme as regras do sistema.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sub-tabs for switching simulator types
                TabRow(
                    selectedTabIndex = selectedAuthSimTab.value,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedAuthSimTab.value == 0,
                        onClick = { 
                            selectedAuthSimTab.value = 0 
                            isAuthSuccess.value = null
                            authLogs.clear()
                        },
                        text = { Text("EA MQL5 Robot", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedAuthSimTab.value == 1,
                        onClick = { 
                            selectedAuthSimTab.value = 1 
                            isAuthSuccess.value = null
                            authLogs.clear()
                        },
                        text = { Text("Portal FiMaster", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedAuthSimTab.value == 0) {
                    // EA MQL5 Form
                    OutlinedTextField(
                        value = simMt5Id.value,
                        onValueChange = { simMt5Id.value = it },
                        label = { Text("Conta MT5 ID (Ex: 123456)") },
                        textStyle = TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().testTag("sim_mt5_id_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = simEaPassword.value,
                        onValueChange = { simEaPassword.value = it },
                        label = { Text("Senha enviado do MQL5") },
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().testTag("sim_ea_password_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                } else {
                    // Portal FiMaster Form
                    OutlinedTextField(
                        value = simPhoneNum.value,
                        onValueChange = { simPhoneNum.value = it },
                        label = { Text("Telefone do Cliente") },
                        textStyle = TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().testTag("sim_phone_num_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    OutlinedTextField(
                        value = simPortalPassword.value,
                        onValueChange = { simPortalPassword.value = it },
                        label = { Text("Senha do Portal") },
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().testTag("sim_portal_password_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                Button(
                    onClick = {
                        authLogs.clear()
                        isAuthSuccess.value = null
                        val mt5Id = simMt5Id.value.trim()
                        val eaPass = simEaPassword.value.trim()
                        val phone = simPhoneNum.value.trim()
                        val portalPass = simPortalPassword.value.trim()

                        if (selectedAuthSimTab.value == 0) {
                            if (mt5Id.isEmpty() || eaPass.isEmpty()) {
                                Toast.makeText(context, "Por favor preencha todos os campos do robô.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                authLogs.add("▶️ [INÍCIO] Iniciando fluxo de login para o EA MQL5...")
                                kotlinx.coroutines.delay(400)
                                authLogs.add("🔍 [PASSO 1] Acedendo aos dados do índice MT5 (dados/indices/mt5.json)...")
                                kotlinx.coroutines.delay(400)
                                
                                val repo = viewModel.repo
                                var userId: String? = null
                                
                                // Fetch index remotely
                                val remoteIndexJson = repo.fetchRemoteJsonContent("dados/indices/mt5.json")
                                if (remoteIndexJson != null) {
                                    authLogs.add("📡 [ÍNDICE] Arquivo de índice MT5 remoto carregado.")
                                    try {
                                        val root = org.json.JSONObject(remoteIndexJson)
                                        if (root.has(mt5Id)) {
                                            userId = root.getJSONObject(mt5Id).optString("usuario", "")
                                            authLogs.add("✅ [ÍNDICE] Conta MT5 '$mt5Id' localizada no índice remoto. ID do usuário: '$userId'")
                                        } else {
                                            authLogs.add("⚠️ [ÍNDICE] Conta MT5 não encontrada no índice remoto.")
                                        }
                                    } catch (e: Exception) {
                                        authLogs.add("❌ [ÍNDICE] Erro ao analisar índice remoto: ${e.message}")
                                    }
                                } else {
                                    authLogs.add("ℹ️ [ÍNDICE] Índice remoto indisponível. Tentando busca local...")
                                }
                                
                                if (userId.isNullOrEmpty()) {
                                    val localUser = repo.userDao.getUserByMt5(mt5Id)
                                    if (localUser != null) {
                                        userId = localUser.idUsuario
                                        authLogs.add("💾 [ÍNDICE] Conta MT5 '$mt5Id' localizada no banco de dados local. ID do usuário: '$userId'")
                                    }
                                }
                                
                                if (userId.isNullOrEmpty()) {
                                    authLogs.add("❌ [FALHA] Conta MT5 não registada em nenhum índice.")
                                    isAuthSuccess.value = false
                                    return@launch
                                }
                                
                                kotlinx.coroutines.delay(400)
                                authLogs.add("👤 [PASSO 2] Buscando utilizador em 'dados_usuarios' para o ID '$userId'...")
                                kotlinx.coroutines.delay(400)
                                
                                var user: UserEntity? = null
                                val remoteUserJson = repo.fetchRemoteJsonContent("dados/usuarios/$userId.json")
                                if (remoteUserJson != null) {
                                    try {
                                        user = repo.parseUserFromJson(remoteUserJson)
                                        if (user != null) {
                                            authLogs.add("📡 [USUÁRIO] Registro do utilizador carregado do servidor.")
                                        }
                                    } catch (e: Exception) {
                                        authLogs.add("❌ [USUÁRIO] Erro ao decodificar utilizador remoto: ${e.message}")
                                    }
                                }
                                
                                if (user == null) {
                                    user = repo.userDao.getUserById(userId)
                                    if (user != null) {
                                        authLogs.add("💾 [USUÁRIO] Registro do utilizador recuperado localmente.")
                                    }
                                }
                                
                                if (user == null) {
                                    authLogs.add("❌ [FALHA] Utilizador não encontrado.")
                                    isAuthSuccess.value = false
                                    return@launch
                                }
                                
                                kotlinx.coroutines.delay(400)
                                authLogs.add("🔐 [PASSO 3] Verificando credenciais...")
                                authLogs.add("ℹ️ [SEGURANÇA] Salt extraído: '${user.salt}'")
                                kotlinx.coroutines.delay(400)
                                
                                val storedHashParts = user.senhaHash.split(":")
                                val actualStoredHash = storedHashParts.firstOrNull() ?: ""
                                val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
                                val enteredHash = com.example.util.SecurityUtils.hashSha256(eaPass, saltToUse)
                                
                                authLogs.add("🔑 [SEGURANÇA] Hash gerado com o salt: '$enteredHash'")
                                authLogs.add("🔒 [SEGURANÇA] Hash esperado: '$actualStoredHash'")
                                
                                kotlinx.coroutines.delay(400)
                                if (enteredHash == actualStoredHash) {
                                    authLogs.add("🎉 [SUCESSO] Senha válida! EA MT5 conectado com permissões de Leitura e Escrita.")
                                    isAuthSuccess.value = true
                                } else {
                                    authLogs.add("❌ [FALHA] Senha inválida para a conta MT5.")
                                    isAuthSuccess.value = false
                                }
                            }
                        } else {
                            // FiMaster Portal Flow
                            if (phone.isEmpty() || portalPass.isEmpty()) {
                                Toast.makeText(context, "Por favor preencha todos os campos do portal.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                authLogs.add("▶️ [INÍCIO] Iniciando fluxo de login para o Portal FiMaster...")
                                kotlinx.coroutines.delay(400)
                                authLogs.add("🔍 [PASSO 1] Acedendo aos dados do índice Telefones (dados/indices/telefones.json)...")
                                kotlinx.coroutines.delay(400)
                                
                                val repo = viewModel.repo
                                var userId: String? = null
                                
                                // Fetch index remotely
                                val remoteIndexJson = repo.fetchRemoteJsonContent("dados/indices/telefones.json")
                                if (remoteIndexJson != null) {
                                    authLogs.add("📡 [ÍNDICE] Arquivo de índice de Telefones remoto carregado.")
                                    try {
                                        val root = org.json.JSONObject(remoteIndexJson)
                                        val possibleKeys = listOf(phone, "+$phone", phone.removePrefix("+"))
                                        for (key in possibleKeys) {
                                            if (root.has(key)) {
                                                userId = root.getJSONObject(key).optString("usuario", "")
                                                authLogs.add("✅ [ÍNDICE] Telefone '$key' localizado no índice remoto. ID do usuário: '$userId'")
                                                break
                                            }
                                        }
                                        if (userId == null) {
                                            authLogs.add("⚠️ [ÍNDICE] Telefone não encontrado no índice remoto.")
                                        }
                                    } catch (e: Exception) {
                                        authLogs.add("❌ [ÍNDICE] Erro ao analisar índice remoto: ${e.message}")
                                    }
                                } else {
                                    authLogs.add("ℹ️ [ÍNDICE] Índice remoto indisponível. Tentando busca local...")
                                }
                                
                                if (userId.isNullOrEmpty()) {
                                    val localUser = repo.userDao.getUserByPhone(phone) ?: repo.userDao.getUserByPhone("+$phone")
                                    if (localUser != null) {
                                        userId = localUser.idUsuario
                                        authLogs.add("💾 [ÍNDICE] Telefone localizado no banco de dados local. ID do usuário: '$userId'")
                                    }
                                }
                                
                                if (userId.isNullOrEmpty()) {
                                    authLogs.add("❌ [FALHA] Número de telefone não registado em nenhum índice.")
                                    isAuthSuccess.value = false
                                    return@launch
                                }
                                
                                kotlinx.coroutines.delay(400)
                                authLogs.add("👤 [PASSO 2] Buscando utilizador em 'dados_usuarios' para o ID '$userId'...")
                                kotlinx.coroutines.delay(400)
                                
                                var user: UserEntity? = null
                                val remoteUserJson = repo.fetchRemoteJsonContent("dados/usuarios/$userId.json")
                                if (remoteUserJson != null) {
                                    try {
                                        user = repo.parseUserFromJson(remoteUserJson)
                                        if (user != null) {
                                            authLogs.add("📡 [USUÁRIO] Registro do utilizador carregado do servidor.")
                                        }
                                    } catch (e: Exception) {
                                        authLogs.add("❌ [USUÁRIO] Erro ao decodificar utilizador remoto: ${e.message}")
                                    }
                                }
                                
                                if (user == null) {
                                    user = repo.userDao.getUserById(userId)
                                    if (user != null) {
                                        authLogs.add("💾 [USUÁRIO] Registro do utilizador recuperado localmente.")
                                    }
                                }
                                
                                if (user == null) {
                                    authLogs.add("❌ [FALHA] Utilizador não encontrado.")
                                    isAuthSuccess.value = false
                                    return@launch
                                }
                                
                                kotlinx.coroutines.delay(400)
                                authLogs.add("🔐 [PASSO 3] Verificando credenciais...")
                                authLogs.add("ℹ️ [SEGURANÇA] Salt extraído: '${user.salt}'")
                                kotlinx.coroutines.delay(400)
                                
                                val storedHashParts = user.senhaHash.split(":")
                                val actualStoredHash = storedHashParts.firstOrNull() ?: ""
                                val saltToUse = if (user.salt.isNotEmpty()) user.salt else (storedHashParts.getOrNull(1) ?: "")
                                val enteredHash = com.example.util.SecurityUtils.hashSha256(portalPass, saltToUse)
                                
                                authLogs.add("🔑 [SEGURANÇA] Hash gerado com o salt: '$enteredHash'")
                                authLogs.add("🔒 [SEGURANÇA] Hash esperado: '$actualStoredHash'")
                                
                                kotlinx.coroutines.delay(400)
                                if (enteredHash == actualStoredHash) {
                                    authLogs.add("🎉 [SUCESSO] Senha válida! Conectado com sucesso ao Portal FiMaster.")
                                    isAuthSuccess.value = true
                                } else {
                                    authLogs.add("❌ [FALHA] Senha inválida para o Portal.")
                                    isAuthSuccess.value = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("run_auth_simulation_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAuthSuccess.value == true) Color(0xFF10B981)
                                         else if (isAuthSuccess.value == false) Color(0xFFEF4444)
                                         else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isAuthSuccess.value == true) Icons.Filled.CheckCircle
                                     else if (isAuthSuccess.value == false) Icons.Filled.Error
                                     else Icons.Filled.PlayArrow,
                        contentDescription = "Executar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ToUpperCaseText("Executar Login Sequencial")
                }

                // Logs Output Console Box
                if (authLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Console de Autenticação",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                        HorizontalDivider(color = Color(0xFF1E293B))
                        
                        authLogs.forEach { log ->
                            Text(
                                text = log,
                                fontSize = 11.sp,
                                color = if (log.contains("🎉 [SUCESSO]") || log.contains("✅")) Color(0xFF10B981)
                                        else if (log.contains("❌") || log.contains("⚠️")) Color(0xFFF87171)
                                        else Color(0xFFF1F5F9),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardMetricCard(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title, 
                fontSize = 11.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = color
            )
        }
    }
}

// ==========================================
// SCREEN 2: USERS TAB (LIST & SEARCH & EXPORT & ACCUMULATORS)
// ==========================================
@Composable
fun UsersScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val users by viewModel.usersList.collectAsStateWithLifecycle()
    val pendingPayments by viewModel.pendingPaymentsList.collectAsStateWithLifecycle()

    val selectedUser = remember { mutableStateOf<UserEntity?>(null) }
    val showCsvDialog = remember { mutableStateOf(false) }
    val selectedSubTab = remember { mutableIntStateOf(0) } // 0 = Registered, 1 = Accumulators

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Pesquisar utilizador ou telefone...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("search_users_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Scrollable filter selection chips (Point 6)
            val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
            val filters = listOf(
                "TODOS" to "Todos",
                "HOJE" to "Vence Hoje",
                "7_DIAS" to "Vence em 7d",
                "30_DIAS" to "Vence em 30d",
                "EXPIRADAS" to "Expiradas",
                "RENOVADAS" to "Renovadas"
            )
            
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(filters) { (filterKey, filterLabel) ->
                    val isSelected = selectedFilter == filterKey
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    
                    Surface(
                        modifier = Modifier
                            .clickable { viewModel.setFilter(filterKey) }
                            .testTag("filter_chip_$filterKey"),
                        shape = RoundedCornerShape(20.dp),
                        color = bgColor,
                        border = if (isSelected) BorderStroke(1.dp, borderColor) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = filterLabel,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sub tabs inside Users section
            TabRow(
                selectedTabIndex = selectedSubTab.intValue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedSubTab.intValue == 0,
                    onClick = { selectedSubTab.intValue = 0 },
                    text = { Text("Utilizadores (${users.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedSubTab.intValue == 1,
                    onClick = { selectedSubTab.intValue = 1 },
                    text = { Text("Acumuladores Parciais (${pendingPayments.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedSubTab.intValue == 0) {
                // Table Header Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Utilizadores Encontrados",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(text = "Toque para Detalhes / MT5", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // ListView Users
                if (users.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Outlined.People,
                                contentDescription = "Empty",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nenhum utilizador encontrado.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it.idUsuario }) { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUser.value = user },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (user.status == "EXPIRADO") Color(0xFFEF4444).copy(alpha = 0.12f)
                                                else if (user.licencaAtiva) Color(0xFF10B981).copy(alpha = 0.12f) 
                                                else Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (user.status == "EXPIRADO") Icons.Filled.ErrorOutline
                                                          else if (user.licencaAtiva) Icons.Filled.VerifiedUser 
                                                          else Icons.Filled.HourglassEmpty,
                                            tint = if (user.status == "EXPIRADO") Color(0xFFEF4444)
                                                   else if (user.licencaAtiva) Color(0xFF10B981) 
                                                   else Color(0xFFF59E0B),
                                            contentDescription = "Status",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            text = user.nome,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(user.idUsuario, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                            Text(user.telefone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (user.mt5IdConta.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "MT5: ${user.mt5IdConta}", 
                                                    fontSize = 10.sp, 
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer, 
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${user.saldo} MT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF10B981))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when (user.syncStatus) {
                                                            "SYNCED" -> Color(0xFF10B981)
                                                            "SYNCING" -> Color(0xFF3B82F6)
                                                            else -> Color(0xFFF59E0B)
                                                        }
                                                    )
                                            )
                                            Text(
                                                text = when (user.syncStatus) {
                                                    "SYNCED" -> "Sinc."
                                                    "SYNCING" -> "Sinc..."
                                                    else -> "Pendente"
                                                },
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            } else {
                // ListView Accumulators (Pending payments)
                if (pendingPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Nenhum acumulador de saldo pendente no momento.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pendingPayments, key = { it.idPendente }) { pend ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(pend.idPendente, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFFF3E0))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(pend.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                        }
                                    }

                                    Text("Nome: ${pend.nome}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Telefone: ${pend.telefone}", fontSize = 12.sp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Acumulado", fontSize = 11.sp, color = Color.Gray)
                                            Text("${pend.valorAcumulado} MT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4CAF50))
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Faltam", fontSize = 11.sp, color = Color.Gray)
                                            Text("${pend.faltam} MT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF44336))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Mínimo", fontSize = 11.sp, color = Color.Gray)
                                            Text("${pend.valorMinimo} MT", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                    }

                                    // Graphical Progress Bar
                                    val progress = (pend.valorAcumulado / pend.valorMinimo).toFloat().coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFF4CAF50),
                                        trackColor = Color.Black.copy(alpha = 0.05f)
                                    )

                                    Text("Transações associadas: ${pend.transacoes}", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text("Status Sinc: ${pend.lastSyncMessage ?: "Sem registro"}", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // CSV Export Floating Action Button
        FloatingActionButton(
            onClick = {
                if (users.isEmpty()) {
                    Toast.makeText(context, "Nenhum utilizador para exportar.", Toast.LENGTH_SHORT).show()
                } else {
                    showCsvDialog.value = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("export_csv_fab"),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV")
        }
    }

    // Detail Pop-up modal containing MT5 and licensing administration editing controls!
    if (selectedUser.value != null) {
        val user = selectedUser.value!!
        
        // Editable state bindings for administrative licensing & MT5 mapping
        val editedMt5 = remember(user.idUsuario) { mutableStateOf(user.mt5IdConta) }
        val editedLicencaAtiva = remember(user.idUsuario) { mutableStateOf(user.licencaAtiva) }
        val editedValidade = remember(user.idUsuario) { mutableStateOf(user.licencaValidade) }

        AlertDialog(
            onDismissRequest = { selectedUser.value = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (user.licencaAtiva) Icons.Filled.VerifiedUser else Icons.Filled.Cancel,
                        tint = if (user.licencaAtiva) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        contentDescription = "Status"
                    )
                    Text("Gestão de Licença e Perfil")
                }
            },
            text = {
                val minAtivacao = viewModel.valorMinimoAtivacao.collectAsStateWithLifecycle().value
                val mesesValidade = viewModel.validadeMeses.collectAsStateWithLifecycle().value
                val mesesSegundaVia = if (minAtivacao > 0) {
                    (user.creditoGuardado / minAtivacao) * mesesValidade
                } else 0.0

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    userDetailRow("Nome Completo", user.nome)
                    userDetailRow("Identificador", user.idUsuario)
                    userDetailRow("Telefone", user.telefone)
                    userDetailRow("ID da Transação", user.idTransacao)
                    userDetailRow("Valor Recebido / Saldo", "${user.saldo} MT")
                    userDetailRow("Crédito de Segunda Via", "${user.creditoGuardado} MT (Segunda via de uso igual a ${String.format(Locale.getDefault(), "%.1f", mesesSegundaVia)} meses)")
                    userDetailRow("Plano de Licença", user.licencaPlano)
                    userDetailRow("Última Renovação", if (user.ultimaRenovacao.isEmpty()) "Nenhuma" else user.ultimaRenovacao)
                    userDetailRow("Total de Renovações", "${user.totalRenovacoes}")
                    
                    if (user.historicoRenovacoes.isNotEmpty() && user.historicoRenovacoes != "[]") {
                        Text("Histórico de Renovações", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        val historyList = remember(user.historicoRenovacoes) {
                            val list = mutableListOf<Triple<String, Double, String>>()
                            try {
                                val array = org.json.JSONArray(user.historicoRenovacoes)
                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)
                                    val hData = item.optString("data", "")
                                    val hValor = item.optDouble("valor", 0.0)
                                    val hDesc = item.optString("descricao", item.optString("id_transacao", ""))
                                    list.add(Triple(hData, hValor, hDesc))
                                }
                            } catch (e: Exception) {
                                // Ignore parse errors
                            }
                            list
                        }
                        historyList.forEach { (hData, hValor, hDesc) ->
                            val descText = if (hDesc.isNotEmpty()) " | $hDesc" else ""
                            Text("• $hData | ${hValor} MT$descText", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    userDetailRow("Data Registro", user.dataRegistro)
                    userDetailRow("Sync Status", user.syncStatus)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Vincular Conta MetaTrader 5 & Licença", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = editedMt5.value,
                        onValueChange = { editedMt5.value = it },
                        label = { Text("ID da Conta MT5") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedValidade.value,
                        onValueChange = { editedValidade.value = it },
                        label = { Text("Validade (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ativar Licença", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Switch(
                            checked = editedLicencaAtiva.value,
                            onCheckedChange = { editedLicencaAtiva.value = it }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.updateUserMt5(
                                user = user,
                                mt5Id = editedMt5.value.trim(),
                                licencaAtiva = editedLicencaAtiva.value,
                                validadeStr = editedValidade.value.trim()
                            ) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    selectedUser.value = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Salvar")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Conta MT5 & Licença")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedUser.value = null }) {
                    Text("Voltar")
                }
            }
        )
    }

    // CSV viewer dialog
    if (showCsvDialog.value) {
        val csvText = viewModel.exportCsvData()
        AlertDialog(
            onDismissRequest = { showCsvDialog.value = false },
            title = { Text("Exportador de Dados CSV") },
            text = {
                Column {
                    Text("Relatório gerado nos padrões de auditoria:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.Black.copy(alpha = 0.05f))
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(text = csvText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("SMS Gateway CSV", csvText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV copiada com sucesso para a área de transferência!", Toast.LENGTH_SHORT).show()
                        showCsvDialog.value = false
                    }
                ) {
                    Text("Copiar CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvDialog.value = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
fun userDetailRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// SCREEN 3: REEMBOLSOS SCREEN
// ==========================================
@Composable
fun RefundsScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    val refunds by viewModel.refundsList.collectAsStateWithLifecycle()
    val txIdInput = remember { mutableStateOf("") }
    val selectedStatusFilter = remember { mutableStateOf("Todos") }

    val statusFilters = listOf("Todos", "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO", "PAGO", "REJEITADO")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sistema de Reembolsos", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

        // Request Form Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solicitar Reembolso Manual", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Cria uma solicitação de reembolso vinculada ao identificador da transação de SMS.", fontSize = 11.sp, color = Color.Gray)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = txIdInput.value,
                        onValueChange = { txIdInput.value = it },
                        label = { Text("ID da Transação") },
                        modifier = Modifier.weight(1f).testTag("refund_tx_input"),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp)
                    )
                    Button(
                        onClick = {
                            if (txIdInput.value.isBlank()) {
                                Toast.makeText(context, "Por favor insira um ID de transação válido.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.submitRefundRequest(txIdInput.value.trim()) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    txIdInput.value = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_refund_btn")
                    ) {
                        Text("Solicitar", fontSize = 12.sp)
                    }
                }
            }
        }

        // Status filter tabs
        ScrollableTabRow(
            selectedTabIndex = statusFilters.indexOf(selectedStatusFilter.value).coerceAtLeast(0),
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            statusFilters.forEach { status ->
                Tab(
                    selected = selectedStatusFilter.value == status,
                    onClick = { selectedStatusFilter.value = status },
                    text = { 
                        Text(
                            text = when(status) {
                                "Todos" -> "Todos"
                                "AGUARDANDO_APROVACAO" -> "Aguard. Aprov."
                                "AGUARDANDO_PAGAMENTO" -> "Aguard. Pag."
                                "PAGO" -> "Pagos"
                                "REJEITADO" -> "Rejeitados"
                                else -> status
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        // List of Refunds filtered
        val filteredRefunds = remember(refunds, selectedStatusFilter.value) {
            if (selectedStatusFilter.value == "Todos") {
                refunds
            } else {
                refunds.filter { it.status == selectedStatusFilter.value }
            }
        }

        if (filteredRefunds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma solicitação de reembolso nesta categoria.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRefunds, key = { it.idReembolso }) { ref ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ref.idReembolso, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                
                                // Status badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when(ref.status) {
                                                "PAGO" -> Color(0xFF10B981).copy(alpha = 0.12f)
                                                "REJEITADO" -> Color(0xFFEF4444).copy(alpha = 0.12f)
                                                "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO" -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                                else -> Color(0xFF3B82F6).copy(alpha = 0.12f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = ref.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when(ref.status) {
                                            "PAGO" -> Color(0xFF10B981)
                                            "REJEITADO" -> Color(0xFFEF4444)
                                            "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF3B82F6)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.alpha(0.5f))

                            Text("Utilizador: ${ref.idUsuario}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("ID da Transação: ${ref.idTransacao}", fontSize = 12.sp)
                            Text("Valor de Reembolso: ${ref.valor} MT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("Solicitado em: ${ref.dataSolicitacao.take(16).replace("T", " ")}", fontSize = 11.sp, color = Color.Gray)

                            if (ref.dataAprovacao.isNotEmpty()) {
                                Text("Aprovado em: ${ref.dataAprovacao.take(16).replace("T", " ")}", fontSize = 11.sp, color = Color.Gray)
                            }
                            if (ref.dataPagamento.isNotEmpty()) {
                                Text("Pago em: ${ref.dataPagamento.take(16).replace("T", " ")}", fontSize = 11.sp, color = Color.Gray)
                            }

                            // Dynamic Admin Actions inside card
                            if (ref.status == "AGUARDANDO_APROVACAO" || ref.status == "AGUARDANDO_PAGAMENTO") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.rejectRefund(ref.idReembolso) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                                    ) {
                                        Text("Rejeitar", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (ref.status == "AGUARDANDO_APROVACAO") {
                                        Button(
                                            onClick = { viewModel.approveRefund(ref.idReembolso, "Administrador") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Aprovar", fontSize = 11.sp, color = Color.White)
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.confirmRefundPaid(ref.idReembolso) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Pagar", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: CONFIGURATIONS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current

    // Bind configuration states
    val mMinimo by viewModel.saldoMinimo.collectAsStateWithLifecycle()
    val mMinimoAtivacao by viewModel.valorMinimoAtivacao.collectAsStateWithLifecycle()
    val mValidadeMeses by viewModel.validadeMeses.collectAsStateWithLifecycle()
    
    val mMode by viewModel.syncMode.collectAsStateWithLifecycle()
    val ghToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val ghRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val ghBranch by viewModel.githubBranch.collectAsStateWithLifecycle()
    val ghPath by viewModel.githubPath.collectAsStateWithLifecycle()
    val fUrl by viewModel.fastApiUrl.collectAsStateWithLifecycle()
    val fToken by viewModel.fastApiToken.collectAsStateWithLifecycle()
    val aSms by viewModel.autoSendSms.collectAsStateWithLifecycle()
    val aSync by viewModel.autoSync.collectAsStateWithLifecycle()
    val cRegex by viewModel.customRegex.collectAsStateWithLifecycle()
    val filterOfficialSenders by viewModel.filterOfficialSenders.collectAsStateWithLifecycle()
    val officialSendersList by viewModel.officialSendersList.collectAsStateWithLifecycle()
    val maxDaysLimit by viewModel.maxRefundDays.collectAsStateWithLifecycle()
    val bgSyncEnabledState by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
    val syncIntervalMinState by viewModel.syncIntervalMinutes.collectAsStateWithLifecycle()
    val curDadosVersion by viewModel.dadosVersion.collectAsStateWithLifecycle()
    val curUltimaAtualizacaoDados by viewModel.ultimaAtualizacaoDados.collectAsStateWithLifecycle()
    
    val mSmsBinding by viewModel.smsBindingEnabled.collectAsStateWithLifecycle()
    val mDiscountEnabled by viewModel.discountEnabled.collectAsStateWithLifecycle()
    val mDiscountText by viewModel.discountText.collectAsStateWithLifecycle()
    val mDiscountPercent by viewModel.discountPercent.collectAsStateWithLifecycle()
    val mSettingsPassword by viewModel.settingsPassword.collectAsStateWithLifecycle()
    val mFirebaseAuthEmail by viewModel.firebaseAuthEmail.collectAsStateWithLifecycle()
    val mFirebaseAuthPassword by viewModel.firebaseAuthPassword.collectAsStateWithLifecycle()
    val mFirebaseDbTarget by viewModel.firebaseDbTarget.collectAsStateWithLifecycle()
    val licenseTiers by viewModel.licenseTiers.collectAsStateWithLifecycle()

    // Temporary values for forms
    val formMinimo = remember(mMinimo) { mutableStateOf(mMinimo.toString()) }
    val formMinimoAtivacao = remember(mMinimoAtivacao) { mutableStateOf(mMinimoAtivacao.toString()) }
    val formValidadeMeses = remember(mValidadeMeses) { mutableStateOf(mValidadeMeses.toString()) }
    
    val formMode = remember(mMode) { mutableStateOf(mMode) }
    val formGhToken = remember(ghToken) { mutableStateOf(ghToken) }
    val formGhRepo = remember(ghRepo) { mutableStateOf(ghRepo) }
    val formGhBranch = remember(ghBranch) { mutableStateOf(ghBranch) }
    val formGhPath = remember(ghPath) { mutableStateOf(ghPath) }
    val formFUrl = remember(fUrl) { mutableStateOf(fUrl) }
    val formFToken = remember(fToken) { mutableStateOf(fToken) }
    val formFirebaseAuthEmail = remember(mFirebaseAuthEmail) { mutableStateOf(mFirebaseAuthEmail) }
    val formFirebaseAuthPassword = remember(mFirebaseAuthPassword) { mutableStateOf(mFirebaseAuthPassword) }
    val formFirebaseDbTarget = remember(mFirebaseDbTarget) { mutableStateOf(mFirebaseDbTarget) }
    val formASms = remember(aSms) { mutableStateOf(aSms) }
    val formASync = remember(aSync) { mutableStateOf(aSync) }
    val formCRegex = remember(cRegex) { mutableStateOf(cRegex) }
    val formFilterOfficial = remember(filterOfficialSenders) { mutableStateOf(filterOfficialSenders) }
    val formOfficialSenders = remember(officialSendersList) { mutableStateOf(officialSendersList) }
    val formMaxRefundDays = remember(maxDaysLimit) { mutableStateOf(maxDaysLimit.toString()) }
    val formBgSyncEnabled = remember(bgSyncEnabledState) { mutableStateOf(bgSyncEnabledState) }
    val formSyncIntervalMin = remember(syncIntervalMinState) { mutableStateOf(syncIntervalMinState.toString()) }
    
    val formSmsBinding = remember(mSmsBinding) { mutableStateOf(mSmsBinding) }
    val formDiscountEnabled = remember(mDiscountEnabled) { mutableStateOf(mDiscountEnabled) }
    val formDiscountText = remember(mDiscountText) { mutableStateOf(mDiscountText) }
    val formDiscountPercent = remember(mDiscountPercent) { mutableStateOf(mDiscountPercent.toString()) }
    val formSettingsPassword = remember(mSettingsPassword) { mutableStateOf(mSettingsPassword) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configurações Administrativas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

        // ==========================================
        // SECTION: LICENÇAS (Starter, Pro, Master VIP, Trial)
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth().testTag("licenses_section_container"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Licenças",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Licenças & Recursos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Defina valores, prazos e parâmetros de recursos para cada plano de licença",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.syncAllLicenseTiers { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Todas as licenças foram sincronizadas com sucesso!" else "Falha ao sincronizar licenças.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("sync_all_licenses_btn")
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync Licencas", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sincronizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 4 Independent Cards: Starter, Pro, Master VIP, Trial
                val defaultOrderedIds = listOf("starter", "pro", "master_vip", "trial")
                val tiersMap = licenseTiers.associateBy { it.id.lowercase() }

                defaultOrderedIds.forEach { tierId ->
                    val currentTier = tiersMap[tierId] ?: when (tierId) {
                        "starter" -> LicenseTierEntity("starter", "Starter", 500.0, 30, "Acesso inicial ao robô MT5 com 1 conta vinculada, gerenciamento de risco padrão e suporte comunitário.", templates = true, capturaTela = false, graficoPatrimonio = true, audio = true, vincularConta = 1, sala = false)
                        "pro" -> LicenseTierEntity("pro", "Pro", 1500.0, 90, "Acesso profissional com até 2 contas MT5, estratégias de alta frequência, trailing stop avançado e suporte prioritário.", templates = true, capturaTela = true, graficoPatrimonio = true, audio = true, vincularConta = 2, sala = true)
                        "master_vip" -> LicenseTierEntity("master_vip", "Master VIP", 3000.0, 365, "Licença VIP Anual ilimitada: Multi-contas MT5, assessoria direta de setup, parâmetros exclusivos e suporte 24/7.", templates = true, capturaTela = true, graficoPatrimonio = true, audio = true, vincularConta = 5, sala = true)
                        else -> LicenseTierEntity("trial", "Trial", 50.0, 7, "Período de avaliação de 7 dias para testes em ambiente real ou demo com parâmetros pré-configurados.", templates = false, capturaTela = false, graficoPatrimonio = false, audio = true, vincularConta = 1, sala = false)
                    }

                    LicenseTierCard(
                        tier = currentTier,
                        onSave = { updatedTier ->
                            viewModel.saveLicenseTier(updatedTier)
                        }
                    )
                }
            }
        }

        // CARD 1: Rules & Thresholds
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Regras de Conta", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                OutlinedTextField(
                    value = formMinimoAtivacao.value,
                    onValueChange = { formMinimoAtivacao.value = it },
                    label = { Text("VALOR_MINIMO_ATIVACAO da Conta (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = formValidadeMeses.value,
                    onValueChange = { formValidadeMeses.value = it },
                    label = { Text("Meses de Validade Padrão da Licença") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = formMinimo.value,
                    onValueChange = { formMinimo.value = it },
                    label = { Text("Saldo Mínimo Alerta (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Responder com SMS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Enviar SMS automático de Boas-vindas ou Rejeição ao utilizador.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formASms.value,
                        onCheckedChange = { formASms.value = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sincronização Ativa", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Sincronizar novos utilizadores imediatamente após o recebimento.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formASync.value,
                        onCheckedChange = { formASync.value = it }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Filtro de Remetente Oficial", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Validar apenas M-Pesa e e-Mola, rejeitando fraudes de outros números.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formFilterOfficial.value,
                        onCheckedChange = { formFilterOfficial.value = it },
                        modifier = Modifier.testTag("filter_official_switch")
                    )
                }

                if (formFilterOfficial.value) {
                    OutlinedTextField(
                        value = formOfficialSenders.value,
                        onValueChange = { formOfficialSenders.value = it },
                        label = { Text("Remetentes Autorizados (separados por vírgula)") },
                        placeholder = { Text("M-Pesa, e-Mola") },
                        modifier = Modifier.fillMaxWidth().testTag("official_senders_input"),
                        singleLine = true
                    )
                }
            }
        }

        // CARD 1.5: Background Sync & Refund Configuration
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sincronização de Segundo Plano e Prazos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sincronização Automática em Background", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Verificar se houve alteração no GitHub ou FastAPI periodicamente.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formBgSyncEnabled.value,
                        onCheckedChange = { formBgSyncEnabled.value = it },
                        modifier = Modifier.testTag("bg_sync_switch")
                    )
                }

                if (formBgSyncEnabled.value) {
                    OutlinedTextField(
                        value = formSyncIntervalMin.value,
                        onValueChange = { formSyncIntervalMin.value = it },
                        label = { Text("Intervalo de Verificação (Minutos)") },
                        placeholder = { Text("Recomendado: 30 minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("bg_sync_interval_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = formMaxRefundDays.value,
                    onValueChange = { formMaxRefundDays.value = it },
                    label = { Text("Prazo Máximo para Pedido de Reembolso (Dias)") },
                    placeholder = { Text("Exemplo: 7 dias") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("max_refund_days_input"),
                    singleLine = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Versão de Dados Local:", fontSize = 12.sp, color = Color.Gray)
                    Text("v$curDadosVersion", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Última Sincronização:", fontSize = 12.sp, color = Color.Gray)
                    Text(curUltimaAtualizacaoDados.ifEmpty { "Nenhuma" }, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // CARD 1.8: Custom Binding, Discounts & Security
        Card(modifier = Modifier.fillMaxWidth().testTag("binding_discount_security_card")) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Vinculação, Descontos & Segurança", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                // Binding Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vinculação de Conta por SMS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Permite vincular a conta MT5 automaticamente se receber uma mensagem 'fimaster#id_conta'.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formSmsBinding.value,
                        onCheckedChange = { formSmsBinding.value = it },
                        modifier = Modifier.testTag("sms_binding_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Discount Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ativar Desconto por SMS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Permite reduzir o valor mínimo se o cliente enviar um SMS com a palavra-chave de desconto configurada.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = formDiscountEnabled.value,
                        onCheckedChange = { formDiscountEnabled.value = it },
                        modifier = Modifier.testTag("sms_discount_switch")
                    )
                }

                if (formDiscountEnabled.value) {
                    OutlinedTextField(
                        value = formDiscountText.value,
                        onValueChange = { formDiscountText.value = it },
                        label = { Text("Palavra-chave de Desconto") },
                        placeholder = { Text("exemplo: DESCONTO") },
                        modifier = Modifier.fillMaxWidth().testTag("discount_text_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formDiscountPercent.value,
                        onValueChange = { formDiscountPercent.value = it },
                        label = { Text("Percentual de Desconto (%)") },
                        placeholder = { Text("exemplo: 10.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("discount_percent_input"),
                        singleLine = true
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Settings Security Password
                Text("Segurança de Acesso", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Defina a senha necessária para acessar estas configurações nas definições administrativas.", fontSize = 11.sp, color = Color.Gray)
                
                OutlinedTextField(
                    value = formSettingsPassword.value,
                    onValueChange = { formSettingsPassword.value = it },
                    label = { Text("Senha das Definições") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("settings_password_input"),
                    singleLine = true
                )
            }
        }

        // CARD 2: Sync Mode Selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Destino de Sincronização", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { formMode.value = ConfigManager.MODE_GITHUB },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (formMode.value == ConfigManager.MODE_GITHUB) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (formMode.value == ConfigManager.MODE_GITHUB) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("1: GitHub", fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { formMode.value = ConfigManager.MODE_FASTAPI },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (formMode.value == ConfigManager.MODE_FASTAPI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (formMode.value == ConfigManager.MODE_FASTAPI) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("2: FastAPI", fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { formMode.value = ConfigManager.MODE_FIREBASE },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (formMode.value == ConfigManager.MODE_FIREBASE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (formMode.value == ConfigManager.MODE_FIREBASE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("3: Firebase", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Nested dynamic parameters display
                if (formMode.value == ConfigManager.MODE_GITHUB) {
                    OutlinedTextField(
                        value = formGhToken.value,
                        onValueChange = { formGhToken.value = it },
                        label = { Text("Token de Acesso GitHub") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = formGhRepo.value,
                        onValueChange = { formGhRepo.value = it },
                        label = { Text("Repositório (autor/repo-nome)") },
                        placeholder = { Text("exemplo: meuautor/sms-gateway-pro") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = formGhBranch.value,
                        onValueChange = { formGhBranch.value = it },
                        label = { Text("Branch") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = formGhPath.value,
                        onValueChange = { formGhPath.value = it },
                        label = { Text("Diretório de Destino (Path)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (formMode.value == ConfigManager.MODE_FIREBASE) {
                    var authStatusMsg by remember { mutableStateOf<String?>(null) }
                    var isTestingAuth by remember { mutableStateOf(false) }
                    var showRulesDialog by remember { mutableStateOf(false) }
                    val currentAuthStatus = remember(mFirebaseAuthEmail) { viewModel.getFirebaseUserDisplay() }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Sincronização Firebase Ativa", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "Os dados serão sincronizados em tempo real no Firestore e Realtime Database usando a configuração do google-services.json.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("Status Auth: $currentAuthStatus", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("Banco de Dados Firebase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { formFirebaseDbTarget.value = ConfigManager.FIREBASE_TARGET_RTDB },
                                    modifier = Modifier.weight(1f).testTag("firebase_target_rtdb_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_RTDB) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_RTDB) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Realtime DB", fontSize = 11.sp, maxLines = 1)
                                }
                                Button(
                                    onClick = { formFirebaseDbTarget.value = ConfigManager.FIREBASE_TARGET_FIRESTORE },
                                    modifier = Modifier.weight(1f).testTag("firebase_target_firestore_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_FIRESTORE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_FIRESTORE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Firestore", fontSize = 11.sp, maxLines = 1)
                                }
                                Button(
                                    onClick = { formFirebaseDbTarget.value = ConfigManager.FIREBASE_TARGET_BOTH },
                                    modifier = Modifier.weight(1f).testTag("firebase_target_both_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_BOTH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (formFirebaseDbTarget.value == ConfigManager.FIREBASE_TARGET_BOTH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Ambos", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("Autenticação Admin Firebase (Opcional)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Caso suas Security Rules exijam autenticação de administrador, preencha o email e senha cadastrados no Firebase Auth:",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            OutlinedTextField(
                                value = formFirebaseAuthEmail.value,
                                onValueChange = { formFirebaseAuthEmail.value = it },
                                label = { Text("Email de Administrador Firebase") },
                                placeholder = { Text("admin@exemplo.com (ou vazio para anônimo)") },
                                modifier = Modifier.fillMaxWidth().testTag("firebase_email_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            OutlinedTextField(
                                value = formFirebaseAuthPassword.value,
                                onValueChange = { formFirebaseAuthPassword.value = it },
                                label = { Text("Senha do Administrador Firebase") },
                                modifier = Modifier.fillMaxWidth().testTag("firebase_password_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        isTestingAuth = true
                                        authStatusMsg = null
                                        viewModel.testFirebaseAuth(formFirebaseAuthEmail.value, formFirebaseAuthPassword.value) { success, msg ->
                                            isTestingAuth = false
                                            authStatusMsg = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = !isTestingAuth,
                                    modifier = Modifier.weight(1f).testTag("test_firebase_auth_btn")
                                ) {
                                    if (isTestingAuth) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("Testar / Conectar", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showRulesDialog = true },
                                    modifier = Modifier.weight(1f).testTag("show_rules_btn")
                                ) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regras / Ajuda", fontSize = 12.sp)
                                }
                            }

                            if (authStatusMsg != null) {
                                Text(
                                    text = authStatusMsg ?: "",
                                    fontSize = 11.sp,
                                    color = if (authStatusMsg?.startsWith("Autenticado com sucesso") == true || authStatusMsg?.startsWith("Autenticado como") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (showRulesDialog) {
                        val rtdbRulesCode = "{\n  \"rules\": {\n    \"dados\": {\n      \".read\": \"auth != null\",\n      \".write\": \"auth != null\",\n      \"indices\": {\n        \".read\": true,\n        \".write\": \"auth != null\"\n      },\n      \"licencas\": {\n        \".read\": true,\n        \".write\": \"auth != null\"\n      }\n    }\n  }\n}"
                        val firestoreRulesCode = "rules_version = '2';\nservice cloud.firestore {\n  match /databases/{database}/documents {\n    match /{document=**} {\n      allow read, write: if request.auth != null;\n    }\n    match /dados_licencas/{docId} {\n      allow read: if true;\n      allow write: if request.auth != null;\n    }\n    match /dados_indices/licenca {\n      allow read: if true;\n      allow write: if request.auth != null;\n    }\n  }\n}"

                        AlertDialog(
                            onDismissRequest = { showRulesDialog = false },
                            title = { Text("Configuração de Segurança Firebase", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Para resolver o erro 'Permission Denied', siga estes 3 passos no console.firebase.com:", fontSize = 12.sp)
                                    
                                    Text("1. Ativar Autenticação Anônima:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Acesse Authentication > Sign-in method > Ative 'Anônimo' (e 'E-mail/senha' se desejar conta admin).", fontSize = 11.sp)

                                    HorizontalDivider()

                                    Text("2. Regras Realtime Database:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Acesse Realtime Database > Regras e cole:", fontSize = 11.sp)
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("RTDB Rules", rtdbRulesCode))
                                            Toast.makeText(context, "Regras do RTDB copiadas para a área de transferência!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar Regras RTDB", fontSize = 12.sp)
                                    }

                                    HorizontalDivider()

                                    Text("3. Regras Cloud Firestore:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Acesse Firestore Database > Regras e cole:", fontSize = 11.sp)
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Firestore Rules", firestoreRulesCode))
                                            Toast.makeText(context, "Regras do Firestore copiadas para a área de transferência!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar Regras Firestore", fontSize = 12.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showRulesDialog = false }) {
                                    Text("Fechar")
                                }
                            }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = formFUrl.value,
                        onValueChange = { formFUrl.value = it },
                        label = { Text("URL Base da FastAPI") },
                        placeholder = { Text("exemplo: http://api.meusite.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = formFToken.value,
                        onValueChange = { formFToken.value = it },
                        label = { Text("Token de Autorização FastAPI (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
        }

        // CARD 3: Custom SMS Operator Pattern Regex configuration
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Code, contentDescription = "Regex", modifier = Modifier.size(20.dp))
                    Text("Formato de Extração (Custom Regex)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("Permite alterar a expressão regular para suportar múltiplos operadores e formatos de SMS.", fontSize = 11.sp, color = Color.Gray)
                
                OutlinedTextField(
                    value = formCRegex.value,
                    onValueChange = { formCRegex.value = it },
                    label = { Text("Expressão Regular de Captura") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            formCRegex.value = """(?i)id da transacao:?\s*([a-z0-9_\-\.]+)\.?\s+recebeste\s+([0-9.,]+)\s*(?:mt)?\s+de\s+conta\s+([0-9]+),?\s*nome:?\s*(.*?)\s+as\s+([0-9:]+)\s+de\s+([0-9/]+)\.?.*?o saldo da tua conta e\s+([0-9.,]+)"""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f).testTag("regex_emola_preset_btn")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Predefinir E-mola", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            formCRegex.value = """(?i)Confirmado\s+([a-z0-9]+)\.?\s+recebeste\s+([0-9.,]+)\s*(?:mt)?\s+de\s+([0-9]+)\s*-\s*(.*?)\s+aos\s+([0-9/]+)\s+as\s+([0-9:]+\s*(?:am|pm)?)\.?\s+o teu novo saldo m-pesa e de\s+([0-9.,]+)"""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f).testTag("regex_mpesa_preset_btn")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Predefinir M-pesa", fontSize = 11.sp)
                    }
                }

                TextButton(
                    onClick = {
                        formCRegex.value = """(?i)ID da transacao:\s*([^\s\.]+)\.?\s+Recebeste\s+([0-9.,]+)\s*(?:MT)?\s+de\s+conta\s+(\d+),\s*nome:\s*(.*?)\s+as\s+([0-9:]+)\s+de\s+([0-9/]+)\..*?O saldo da tua conta e\s+([0-9.,]+)\s*(?:MT)?"""
                    },
                    modifier = Modifier.align(Alignment.End).testTag("regex_reset_btn")
                ) {
                    Text("Redefinir Expressão Padrão", fontSize = 12.sp)
                }
            }
        }

        // CARD 4: Master Admin Templates Publisher & Parameter Editor (/dados/instrucoes_admin_templates.json)
        AdminTemplateEditorCard(viewModel = viewModel)

        // SAVE BUTTONS
        Button(
            onClick = {
                val valMinimo = formMinimo.value.toDoubleOrNull() ?: 1000.0
                val valMinimoAtivacao = formMinimoAtivacao.value.toDoubleOrNull() ?: 1000.0
                val valValidadeMeses = formValidadeMeses.value.toIntOrNull() ?: 12
                val valMaxRefundDays = formMaxRefundDays.value.toIntOrNull() ?: 7
                val valSyncIntervalMin = formSyncIntervalMin.value.toIntOrNull() ?: 30
                val valDiscountPercent = formDiscountPercent.value.toDoubleOrNull() ?: 10.0

                viewModel.updateSettings(
                    mMinimo = valMinimo,
                    mMinimoAtivacao = valMinimoAtivacao,
                    mValidadeMeses = valValidadeMeses,
                    mMode = formMode.value,
                    ghToken = formGhToken.value,
                    ghRepo = formGhRepo.value,
                    ghBranch = formGhBranch.value,
                    ghPath = formGhPath.value,
                    fUrl = formFUrl.value,
                    fToken = formFToken.value,
                    aSms = formASms.value,
                    aSync = formASync.value,
                    cRegex = formCRegex.value,
                    filterOfficial = formFilterOfficial.value,
                    officialSenders = formOfficialSenders.value,
                    maxRefundDaysVal = valMaxRefundDays,
                    bgSyncEnabled = formBgSyncEnabled.value,
                    syncIntervalMin = valSyncIntervalMin,
                    smsBindingEnabledVal = formSmsBinding.value,
                    discountEnabledVal = formDiscountEnabled.value,
                    discountTextVal = formDiscountText.value,
                    discountPercentVal = valDiscountPercent,
                    settingsPasswordVal = formSettingsPassword.value,
                    firebaseAuthEmailVal = formFirebaseAuthEmail.value,
                    firebaseAuthPasswordVal = formFirebaseAuthPassword.value,
                    firebaseDbTargetVal = formFirebaseDbTarget.value
                )
                Toast.makeText(context, "Configurações guardadas e aplicadas!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_settings_btn"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = "Save")
            Spacer(modifier = Modifier.width(8.dp))
            ToUpperCaseText("Guardar Configurações")
        }

        // CORE UTILS & TROUBLESHOOTING CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cuidado & Manutenção local", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                Text("Permite limpar toda a fila local e todos os utilizadores para fins de testes e auditoria.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.retryUnsyncedQueue()
                            Toast.makeText(context, "Retomando sincronizações falhadas...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Forçar Fila", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            viewModel.clearLocalCache()
                            Toast.makeText(context, "Base de dados redefinida!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Limpar BD", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: SYSTEM LOGS AUDIT
// ==========================================
@Composable
fun LogsScreen(viewModel: SmsGatewayViewModel) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val smsLogs by viewModel.smsLogs.collectAsStateWithLifecycle()
    val selectedSubTab = remember { mutableIntStateOf(0) } // 0 = Auditoria, 1 = Respostas SMS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Painel de Auditoria & Logs", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)

        TabRow(selectedTabIndex = selectedSubTab.intValue) {
            Tab(
                selected = selectedSubTab.intValue == 0,
                onClick = { selectedSubTab.intValue = 0 },
                text = { Text("Registros Auditoria", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab.intValue == 1,
                onClick = { selectedSubTab.intValue = 1 },
                text = { Text("SMS Enviados", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedSubTab.intValue == 0) {
            // Audit List
            if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Nenhum log de auditoria disponível.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(auditLogs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "De: ${log.sender}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (log.status) {
                                                    "SUCCESS", "SUCCESS_DIRECT", "SUCCESS_ACCUMULATED", "CREDIT_SAVED" -> Color(0xFFE8F5E9)
                                                    "FILTERED" -> Color(0xFFECEFF1)
                                                    "PENDING_NEW", "PENDING_ACCUMULATED" -> Color(0xFFFFF3E0)
                                                    else -> Color(0xFFFFEBEE)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = log.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (log.status) {
                                                "SUCCESS", "SUCCESS_DIRECT", "SUCCESS_ACCUMULATED", "CREDIT_SAVED" -> Color(0xFF2E7D32)
                                                "FILTERED" -> Color(0xFF455A64)
                                                "PENDING_NEW", "PENDING_ACCUMULATED" -> Color(0xFFE65100)
                                                else -> Color(0xFFC62828)
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = log.messageText,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (log.details != null) {
                                    Text(
                                        text = "Detalhes: ${log.details}",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = formatTimestamp(log.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // SMS logs list
            if (smsLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Nenhum SMS enviado.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(smsLogs, key = { it.id }) { sms ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Para: ${sms.recipient}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (sms.status == "SENT") Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = sms.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sms.status == "SENT") Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                                Text(
                                    text = sms.messageText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (sms.errorMessage != null) {
                                    Text(
                                        text = "Erro: ${sms.errorMessage}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = formatTimestamp(sms.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.LightGray,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LicenseTierCard(
    tier: LicenseTierEntity,
    onSave: (LicenseTierEntity) -> Unit
) {
    val context = LocalContext.current
    var formValor by remember(tier.valor) { mutableStateOf(if (tier.valor % 1.0 == 0.0) tier.valor.toInt().toString() else tier.valor.toString()) }
    var formDias by remember(tier.diasValidade) { mutableStateOf(tier.diasValidade.toString()) }
    var formDescricao by remember(tier.descricao) { mutableStateOf(tier.descricao) }
    
    // Parâmetros de Recursos da Licença
    var formVincularConta by remember(tier.vincularConta) { mutableStateOf(tier.vincularConta.toString()) }
    var formTemplates by remember(tier.templates) { mutableStateOf(tier.templates) }
    var formCapturaTela by remember(tier.capturaTela) { mutableStateOf(tier.capturaTela) }
    var formGraficoPatrimonio by remember(tier.graficoPatrimonio) { mutableStateOf(tier.graficoPatrimonio) }
    var formAudio by remember(tier.audio) { mutableStateOf(tier.audio) }
    var formSala by remember(tier.sala) { mutableStateOf(tier.sala) }

    // Links de Redes Sociais / Atendimento
    var formWhatsappLink by remember(tier.whatsappLink) { mutableStateOf(tier.whatsappLink) }
    var formTelegramLink by remember(tier.telegramLink) { mutableStateOf(tier.telegramLink) }

    // QR Code de Pagamento (Armazenamento Binário em Bytes)
    var formQrCodeBytes by remember(tier.qrCodeBytes) { mutableStateOf(tier.qrCodeBytes) }
    var formQrCodeLink by remember(tier.qrCodeLink) { mutableStateOf(tier.qrCodeLink) }
    var showQrCodeDialog by remember { mutableStateOf(false) }

    // Launcher para carregar imagem de QR Code da galeria
    val qrCodePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val bytes = context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    stream.readBytes()
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    formQrCodeBytes = bytes
                    // Tentar decodificar link/conteúdo do QR Code automaticamente usando ZXing
                    val decodedText = QrCodeUtils.decodeQrCodeFromBytes(bytes)
                    if (!decodedText.isNullOrBlank()) {
                        formQrCodeLink = decodedText
                        Toast.makeText(context, "QR Code carregado e link decodificado com sucesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        if (formQrCodeLink.isBlank()) {
                            formQrCodeLink = "qrcode_pagamento_${tier.id}"
                        }
                        Toast.makeText(context, "Imagem do QR Code carregada em dados binários (bytes)!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao carregar imagem: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val qrBitmap = remember(formQrCodeBytes) {
        QrCodeUtils.decodeBitmap(formQrCodeBytes)
    }

    val (badgeColor, badgeBg) = when (tier.id.lowercase()) {
        "starter" -> Color(0xFF10B981) to Color(0xFF10B981).copy(alpha = 0.15f)
        "pro" -> Color(0xFF00E5FF) to Color(0xFF00E5FF).copy(alpha = 0.15f)
        "master_vip" -> Color(0xFFFFB300) to Color(0xFFFFB300).copy(alpha = 0.15f)
        "trial" -> Color(0xFF94A3B8) to Color(0xFF94A3B8).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    }

    // Modal / Dialog de Visualização Ampliada do QR Code
    if (showQrCodeDialog) {
        Dialog(onDismissRequest = { showQrCodeDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QR Code de Pagamento - ${tier.nome}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showQrCodeDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }

                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(2.dp, badgeColor, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code Ampliado de ${tier.nome}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        Text(
                            "Nenhum QR Code carregado.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    if (formQrCodeLink.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Chave / Link do QR Code:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        formQrCodeLink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("QR Code Link", formQrCodeLink))
                                        Toast.makeText(context, "Link do QR Code copiado!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showQrCodeDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fechar Visualização")
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("license_card_${tier.id}"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Name & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tier.nome.uppercase(),
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Licença ${tier.nome}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "${formDias} dias",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            // Campo 1: Nome da Licença (Fixo)
            OutlinedTextField(
                value = tier.nome,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Nome da Licença (Fixo)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("license_name_${tier.id}"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = badgeColor
                    )
                }
            )

            // Row with Valor, Dias and Vincular Contas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Campo 2: Valor da Licença (Editável)
                OutlinedTextField(
                    value = formValor,
                    onValueChange = { formValor = it },
                    label = { Text("Valor (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("license_val_${tier.id}"),
                    singleLine = true,
                    leadingIcon = {
                        Text(
                            "MT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    }
                )

                // Campo 3: Dias de Validade (Editável)
                OutlinedTextField(
                    value = formDias,
                    onValueChange = { formDias = it },
                    label = { Text("Validade (Dias)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("license_days_${tier.id}"),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            // Campo: Vincular Contas MT5 (Valor Numérico)
            OutlinedTextField(
                value = formVincularConta,
                onValueChange = { formVincularConta = it },
                label = { Text("Vincular Contas MT5 (Limite Numérico)") },
                placeholder = { Text("Ex: 1, 2, 5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("license_vincular_${tier.id}"),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            // Section: Links de Redes Sociais / Atendimento (WhatsApp & Telegram)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Links de Atendimento & Canais (WhatsApp & Telegram)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Campo WhatsApp Link
                    OutlinedTextField(
                        value = formWhatsappLink,
                        onValueChange = { formWhatsappLink = it },
                        label = { Text("Link do WhatsApp (Completo)") },
                        placeholder = { Text("Ex: https://wa.me/25884xxxxxxx") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("license_whatsapp_${tier.id}"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "WhatsApp",
                                tint = Color(0xFF25D366)
                            )
                        },
                        trailingIcon = {
                            if (formWhatsappLink.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formWhatsappLink))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Link inválido: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Abrir WhatsApp", tint = Color(0xFF25D366))
                                }
                            }
                        }
                    )

                    // Campo Telegram Link
                    OutlinedTextField(
                        value = formTelegramLink,
                        onValueChange = { formTelegramLink = it },
                        label = { Text("Link do Telegram (Completo)") },
                        placeholder = { Text("Ex: https://t.me/seucanal") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("license_telegram_${tier.id}"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Telegram",
                                tint = Color(0xFF229ED9)
                            )
                        },
                        trailingIcon = {
                            if (formTelegramLink.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formTelegramLink))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Link inválido: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Abrir Telegram", tint = Color(0xFF229ED9))
                                }
                            }
                        }
                    )
                }
            }

            // Section: QR Code de Pagamento (Imagem, Binário e Link Automático)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "QR Code de Pagamento",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (formQrCodeBytes != null && formQrCodeBytes!!.isNotEmpty()) {
                            Text(
                                "Imagem Binária Carregada (${formQrCodeBytes!!.size} bytes)",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Visualizador / Botão de Seleção do QR Code
                    if (qrBitmap != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Miniatura Clicável
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, RoundedCornerShape(6.dp))
                                    .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                                    .clickable { showQrCodeDialog = true }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code Miniatura",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "QR Code Ativo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Toque na imagem para ampliar e visualizar em tamanho real.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showQrCodeDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Visualizar", fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { qrCodePickerLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Trocar", fontSize = 10.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            formQrCodeBytes = null
                                            formQrCodeLink = ""
                                            Toast.makeText(context, "QR Code removido.", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        // Caixa para carregar novo QR Code
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { qrCodePickerLauncher.launch("image/*") }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Carregar QR Code",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    "Carregar Imagem de QR Code de Pagamento",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Clique para selecionar uma imagem da galeria (PNG ou JPG). O link será decodificado e a imagem salva em dados binários.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Campo de Link do QR Code (Preenchido Automaticamente / Editável)
                    OutlinedTextField(
                        value = formQrCodeLink,
                        onValueChange = { formQrCodeLink = it },
                        label = { Text("Link / Chave do QR Code (Automático)") },
                        placeholder = { Text("Link ou chave de pagamento decodificada do QR Code") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("license_qr_link_${tier.id}"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }

            // Section: Parâmetros e Recursos
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Parâmetros e Recursos da Licença",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Templates (teamplates)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Templates (Teamplates)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Acesso a configurações e templates predefinidos", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = formTemplates,
                            onCheckedChange = { formTemplates = it },
                            modifier = Modifier.testTag("switch_templates_${tier.id}")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp)

                    // 2. Captura de Tela
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Captura de Tela", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Capturar prints e visualização gráfica de operações", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = formCapturaTela,
                            onCheckedChange = { formCapturaTela = it },
                            modifier = Modifier.testTag("switch_captura_${tier.id}")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp)

                    // 3. Gráfico de Património
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Gráfico de Património", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Monitoramento dinâmico de equity e curva de capital", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = formGraficoPatrimonio,
                            onCheckedChange = { formGraficoPatrimonio = it },
                            modifier = Modifier.testTag("switch_patrimonio_${tier.id}")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp)

                    // 4. Áudio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Áudio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Alertas sonoros e notificações por voz no EA", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = formAudio,
                            onCheckedChange = { formAudio = it },
                            modifier = Modifier.testTag("switch_audio_${tier.id}")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp)

                    // 5. Sala
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Sala VIP / Sinais", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Acesso a salas exclusivas de operações e análises", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = formSala,
                            onCheckedChange = { formSala = it },
                            modifier = Modifier.testTag("switch_sala_${tier.id}")
                        )
                    }
                }
            }

            // Campo: Descrição da Licença (Editável)
            OutlinedTextField(
                value = formDescricao,
                onValueChange = { formDescricao = it },
                label = { Text("Descrição da Licença (Benefícios e Recursos)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("license_desc_${tier.id}"),
                minLines = 2,
                maxLines = 4
            )

            // Botão "Salvar e Sincronizar"
            Button(
                onClick = {
                    val parsedValor = formValor.replace(",", ".").toDoubleOrNull() ?: tier.valor
                    val parsedDias = formDias.toIntOrNull() ?: tier.diasValidade
                    val parsedVincular = formVincularConta.toIntOrNull() ?: tier.vincularConta
                    val updated = tier.copy(
                        valor = parsedValor,
                        diasValidade = parsedDias,
                        descricao = formDescricao.trim(),
                        templates = formTemplates,
                        capturaTela = formCapturaTela,
                        graficoPatrimonio = formGraficoPatrimonio,
                        audio = formAudio,
                        vincularConta = parsedVincular,
                        sala = formSala,
                        whatsappLink = formWhatsappLink.trim(),
                        telegramLink = formTelegramLink.trim(),
                        qrCodeBytes = formQrCodeBytes,
                        qrCodeLink = formQrCodeLink.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updated)
                    Toast.makeText(context, "Licença ${tier.nome} salva e sincronizada com sucesso!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_license_${tier.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Salvar",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar e Sincronizar ${tier.nome}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTemplateEditorCard(viewModel: SmsGatewayViewModel) {
    val context = LocalContext.current
    var isFormMode by remember { mutableStateOf(true) }
    var expandedSection by remember { mutableStateOf<Int?>(0) } // null = collapse all, 0 = metadata, 1-9 = EA sections

    // Template Metadata State
    val tplId = remember { mutableStateOf("tpl_001_fimathe_m15") }
    val tplTitulo = remember { mutableStateOf("⚡ Template M15 Gold Conservador (Oficial Admin)") }
    val tplDescricao = remember { mutableStateOf("Setup oficial com gestão de risco ajustada para XAUUSD e EURUSD nas sessões de Londres e Nova Iorque. Lote zerado 0.00 por segurança.") }
    val tplAutor = remember { mutableStateOf("Admin Master Fimaster") }
    val tplDataPublicacao = remember { mutableStateOf("02/08/2026 09:00") }
    val tplValidoAte = remember { mutableStateOf("31/12/2026") }
    val tplDisponivel = remember { mutableStateOf(true) }
    val tplVersaoMinimaEa = remember { mutableStateOf("v3.2") }
    val tplPontosAtivo = remember { mutableStateOf("250 pts") }
    val tplParidade = remember { mutableStateOf("XAUUSD") }

    // Section 1: Auth
    val mt5AccountId = remember { mutableStateOf("TEMPLATE") }
    val senha = remember { mutableStateOf("123456") }

    // Section 2: Colors
    val esquemaCoresEnum = remember { mutableStateOf("CYAN_NEON") }
    val corDeCanal = remember { mutableStateOf("#22D3EE") }
    val corDeLinhas = remember { mutableStateOf("#FF00E5") }
    val corrDeEquador = remember { mutableStateOf("#FFFF00") }
    val linhasDeEquador = remember { mutableStateOf(false) }

    // Section 3: Trend
    val trend = remember { mutableStateOf("TENDENCIA_DE_ALTA") }
    val mEquadorAlta = remember { mutableStateOf("1.2500") }
    val mEquadorBaixa = remember { mutableStateOf("1.2400") }

    // Section 4: Strategy
    val estrategia = remember { mutableStateOf("FIMATHE") }
    val operationalPeriod = remember { mutableStateOf("PERIOD_M15") }
    val viradaDeJogo = remember { mutableStateOf(false) }
    val nives = remember { mutableStateOf("1.0") }
    val costurar = remember { mutableStateOf(true) }
    val tema = remember { mutableStateOf(false) }

    // Section 5: Auto & Sessions
    val eaAtivo = remember { mutableStateOf(true) }
    val eaAuto = remember { mutableStateOf(false) }
    val autoPeriod = remember { mutableStateOf("HORA_1") }
    val autoSurfada = remember { mutableStateOf(false) }
    val sessaoAsiaToquio = remember { mutableStateOf(false) }
    val sessaoLondres = remember { mutableStateOf(true) }
    val sessaoNovaYorqui = remember { mutableStateOf(true) }

    // Section 6: Posic
    val expansaoMinima = remember { mutableStateOf("10") }
    val expansaoMaxima = remember { mutableStateOf("30") }
    val compra = remember { mutableStateOf("1.2550") }
    val venda = remember { mutableStateOf("1.2500") }
    val santo = remember { mutableStateOf("20.0") }
    val dedo = remember { mutableStateOf("10") }
    val posicaoTake = remember { mutableStateOf(false) }
    val buyTake = remember { mutableStateOf("0.0") }
    val sellTake = remember { mutableStateOf("0.0") }

    // Section 7: Risk
    val saldo = remember { mutableStateOf("1000.0") }
    val lot = remember { mutableStateOf("0.00") }
    val gerenciamentoDeRiscoDiario = remember { mutableStateOf(true) }
    val porcentos = remember { mutableStateOf("1.0") }
    val porcentosg = remember { mutableStateOf("1.5") }
    val gerenciamentoDeRiscoSemanal = remember { mutableStateOf(false) }
    val porcentoo = remember { mutableStateOf("2.0") }
    val porcentoss = remember { mutableStateOf("2.0") }

    // Section 8: Ops
    val ativarOuDesativarCompra = remember { mutableStateOf(true) }
    val ativarOuDesativarVenda = remember { mutableStateOf(true) }
    val modifySlForOxO = remember { mutableStateOf(true) }
    val condicaoDeRompimentoC = remember { mutableStateOf(true) }
    val condicaoDeRompimentoV = remember { mutableStateOf(true) }
    val gmail = remember { mutableStateOf(true) }
    val notific = remember { mutableStateOf(true) }

    // Section 9: Exchange
    val cambio = remember { mutableStateOf("64.0") }

    // Helper to generate JSON string from form input state
    fun buildJsonFromState(): String {
        return """{
  "instrucoes_admin_templates": {
    "descricao": "Schema Único de Parâmetros para Publicação de Templates pelo Administrador Master.",
    "limite_publicados_ativos": 3,
    "regra_substituicao": "Publicação de 3 templates ativos. Quando 1 expira, é automaticamente substituído pelo próximo publicado.",
    "templates": {
      "${tplId.value}": {
        "id": "${tplId.value}",
        "titulo": "${tplTitulo.value.replace("\"", "\\\"")}",
        "descricao": "${tplDescricao.value.replace("\"", "\\\"")}",
        "autor": "${tplAutor.value.replace("\"", "\\\"")}",
        "dataPublicacao": "${tplDataPublicacao.value}",
        "validoAte": "${tplValidoAte.value}",
        "disponivel": ${tplDisponivel.value},
        "versaoMinimaEa": "${tplVersaoMinimaEa.value}",
        "pontosAtivo": "${tplPontosAtivo.value}",
        "paridade": "${tplParidade.value}",
        "config": {
          "mt5AccountId": "${mt5AccountId.value}",
          "SENHA": "${senha.value}",
          "ESQUEMA_CORES_ENUM": "${esquemaCoresEnum.value}",
          "cor_de_canal": "${corDeCanal.value}",
          "cor_de_linhas": "${corDeLinhas.value}",
          "corr_de_equador": "${corrDeEquador.value}",
          "LINHAS_DE_EQUADOR": ${linhasDeEquador.value},
          "TREND": "${trend.value}",
          "M_equador_alta": ${mEquadorAlta.value.toDoubleOrNull() ?: 1.2500},
          "M_equador_baixa": ${mEquadorBaixa.value.toDoubleOrNull() ?: 1.2400},
          "TEMA": ${tema.value},
          "ESTRATÉGIA": "${estrategia.value}",
          "virada_de_jogo": ${viradaDeJogo.value},
          "Nives": ${nives.value.toDoubleOrNull() ?: 1.0},
          "Costurar": ${costurar.value},
          "OperationalPeriod": "${operationalPeriod.value}",
          "lot": ${lot.value.toDoubleOrNull() ?: 0.00},
          "EA_ATIVO": ${eaAtivo.value},
          "EA_AUTO": ${eaAuto.value},
          "AUTO_PERIOD": "${autoPeriod.value}",
          "AUTO_SURFADA": ${autoSurfada.value},
          "SESSAO_ASIA_TOQUIO": ${sessaoAsiaToquio.value},
          "SESSAO_LONDRES": ${sessaoLondres.value},
          "SESSAO_NOVA_YORQUI": ${sessaoNovaYorqui.value},
          "EXPANSAO_MINIMA": ${expansaoMinima.value.toIntOrNull() ?: 10},
          "EXPANSAO_MAXIMA": ${expansaoMaxima.value.toIntOrNull() ?: 30},
          "compra": ${compra.value.toDoubleOrNull() ?: 1.2550},
          "venda": ${venda.value.toDoubleOrNull() ?: 1.2500},
          "santo": ${santo.value.toDoubleOrNull() ?: 20.0},
          "dedo": ${dedo.value.toIntOrNull() ?: 10},
          "posicaoTake": ${posicaoTake.value},
          "buy_take": ${buyTake.value.toDoubleOrNull() ?: 0.0},
          "sell_take": ${sellTake.value.toDoubleOrNull() ?: 0.0},
          "SALDO": ${saldo.value.toDoubleOrNull() ?: 1000.0},
          "GERENCIAMENTO_DE_RISCO_DIARIO": ${gerenciamentoDeRiscoDiario.value},
          "porcentos": ${porcentos.value.toDoubleOrNull() ?: 1.0},
          "poercentosg": ${porcentosg.value.toDoubleOrNull() ?: 1.5},
          "GERENCIAMENTO_DE_RISCO_SEMANAL": ${gerenciamentoDeRiscoSemanal.value},
          "PORCENTOO": ${porcentoo.value.toDoubleOrNull() ?: 2.0},
          "PORCENTOSS": ${porcentoss.value.toDoubleOrNull() ?: 2.0},
          "GMAIL": ${gmail.value},
          "notific": ${notific.value},
          "ativar_ou_desativar_venda": ${ativarOuDesativarVenda.value},
          "ativar_ou_desativar_compra": ${ativarOuDesativarCompra.value},
          "Modify_Sl_For_OxO": ${modifySlForOxO.value},
          "condicao_De_rompimento_c": ${condicaoDeRompimentoC.value},
          "condicao_De_rompimento_v": ${condicaoDeRompimentoV.value},
          "CAMBIO": ${cambio.value.toDoubleOrNull() ?: 64.0}
        }
      }
    }
  }
}"""
    }

    val rawJsonText = remember { mutableStateOf(buildJsonFromState()) }

    // Helper to parse JSON into form fields
    fun loadJsonIntoForm(jsonStr: String) {
        try {
            val root = org.json.JSONObject(jsonStr)
            var targetObj: org.json.JSONObject? = null
            if (root.has("instrucoes_admin_templates")) {
                val wrapper = root.getJSONObject("instrucoes_admin_templates")
                if (wrapper.has("templates")) {
                    val tpls = wrapper.getJSONObject("templates")
                    val keys = tpls.keys()
                    if (keys.hasNext()) {
                        targetObj = tpls.getJSONObject(keys.next())
                    }
                }
            } else if (root.has("id")) {
                targetObj = root
            }

            if (targetObj != null) {
                tplId.value = targetObj.optString("id", tplId.value)
                tplTitulo.value = targetObj.optString("titulo", tplTitulo.value)
                tplDescricao.value = targetObj.optString("descricao", tplDescricao.value)
                tplAutor.value = targetObj.optString("autor", tplAutor.value)
                tplDataPublicacao.value = targetObj.optString("dataPublicacao", tplDataPublicacao.value)
                tplValidoAte.value = targetObj.optString("validoAte", tplValidoAte.value)
                tplDisponivel.value = targetObj.optBoolean("disponivel", tplDisponivel.value)
                tplVersaoMinimaEa.value = targetObj.optString("versaoMinimaEa", tplVersaoMinimaEa.value)
                tplPontosAtivo.value = targetObj.optString("pontosAtivo", tplPontosAtivo.value)
                tplParidade.value = targetObj.optString("paridade", tplParidade.value)

                if (targetObj.has("config")) {
                    val cfg = targetObj.getJSONObject("config")
                    mt5AccountId.value = cfg.optString("mt5AccountId", mt5AccountId.value)
                    senha.value = cfg.optString("SENHA", senha.value)
                    esquemaCoresEnum.value = cfg.optString("ESQUEMA_CORES_ENUM", esquemaCoresEnum.value)
                    corDeCanal.value = cfg.optString("cor_de_canal", corDeCanal.value)
                    corDeLinhas.value = cfg.optString("cor_de_linhas", corDeLinhas.value)
                    corrDeEquador.value = cfg.optString("corr_de_equador", corrDeEquador.value)
                    linhasDeEquador.value = cfg.optBoolean("LINHAS_DE_EQUADOR", linhasDeEquador.value)
                    trend.value = if (cfg.has("TREND")) cfg.getString("TREND") else cfg.optString("TENDENCIA", trend.value)
                    mEquadorAlta.value = cfg.optDouble("M_equador_alta", 1.2500).toString()
                    mEquadorBaixa.value = cfg.optDouble("M_equador_baixa", 1.2400).toString()
                    tema.value = cfg.optBoolean("TEMA", tema.value)
                    estrategia.value = if (cfg.has("ESTRATÉGIA")) cfg.getString("ESTRATÉGIA") else cfg.optString("ESTRATEGIA", estrategia.value)
                    viradaDeJogo.value = cfg.optBoolean("virada_de_jogo", viradaDeJogo.value)
                    nives.value = cfg.optDouble("Nives", 1.0).toString()
                    costurar.value = cfg.optBoolean("Costurar", costurar.value)
                    operationalPeriod.value = if (cfg.has("OperationalPeriod")) cfg.getString("OperationalPeriod") else cfg.optString("PeriodoOperacional", operationalPeriod.value)
                    lot.value = cfg.optDouble("lot", 0.00).toString()
                    eaAtivo.value = cfg.optBoolean("EA_ATIVO", eaAtivo.value)
                    eaAuto.value = cfg.optBoolean("EA_AUTO", eaAuto.value)
                    autoPeriod.value = if (cfg.has("AUTO_PERIOD")) cfg.getString("AUTO_PERIOD") else cfg.optString("PERIODO_AUTO", autoPeriod.value)
                    autoSurfada.value = cfg.optBoolean("AUTO_SURFADA", autoSurfada.value)
                    sessaoAsiaToquio.value = cfg.optBoolean("SESSAO_ASIA_TOQUIO", sessaoAsiaToquio.value)
                    sessaoLondres.value = cfg.optBoolean("SESSAO_LONDRES", sessaoLondres.value)
                    sessaoNovaYorqui.value = cfg.optBoolean("SESSAO_NOVA_YORQUI", sessaoNovaYorqui.value)
                    expansaoMinima.value = cfg.optInt("EXPANSAO_MINIMA", 10).toString()
                    expansaoMaxima.value = cfg.optInt("EXPANSAO_MAXIMA", 30).toString()
                    compra.value = cfg.optDouble("compra", 1.2550).toString()
                    venda.value = cfg.optDouble("venda", 1.2500).toString()
                    santo.value = cfg.optDouble("santo", 20.0).toString()
                    dedo.value = cfg.optInt("dedo", 10).toString()
                    posicaoTake.value = cfg.optBoolean("posicaoTake", posicaoTake.value)
                    buyTake.value = cfg.optDouble("buy_take", 0.0).toString()
                    sellTake.value = cfg.optDouble("sell_take", 0.0).toString()
                    saldo.value = cfg.optDouble("SALDO", 1000.0).toString()
                    gerenciamentoDeRiscoDiario.value = cfg.optBoolean("GERENCIAMENTO_DE_RISCO_DIARIO", gerenciamentoDeRiscoDiario.value)
                    porcentos.value = cfg.optDouble("porcentos", 1.0).toString()
                    porcentosg.value = cfg.optDouble("poercentosg", 1.5).toString()
                    gerenciamentoDeRiscoSemanal.value = cfg.optBoolean("GERENCIAMENTO_DE_RISCO_SEMANAL", gerenciamentoDeRiscoSemanal.value)
                    porcentoo.value = cfg.optDouble("PORCENTOO", 2.0).toString()
                    porcentoss.value = cfg.optDouble("PORCENTOSS", 2.0).toString()
                    gmail.value = cfg.optBoolean("GMAIL", gmail.value)
                    notific.value = cfg.optBoolean("notific", notific.value)
                    ativarOuDesativarCompra.value = cfg.optBoolean("ativar_ou_desativar_compra", ativarOuDesativarCompra.value)
                    ativarOuDesativarVenda.value = cfg.optBoolean("ativar_ou_desativar_venda", ativarOuDesativarVenda.value)
                    modifySlForOxO.value = if (cfg.has("Modify_Sl_For_OxO")) cfg.getBoolean("Modify_Sl_For_OxO") else cfg.optBoolean("Modificar_Sl_Para_OxO", modifySlForOxO.value)
                    condicaoDeRompimentoC.value = cfg.optBoolean("condicao_De_rompimento_c", condicaoDeRompimentoC.value)
                    condicaoDeRompimentoV.value = cfg.optBoolean("condicao_De_rompimento_v", condicaoDeRompimentoV.value)
                    cambio.value = cfg.optDouble("CAMBIO", 64.0).toString()
                }
                Toast.makeText(context, "Valores sincronizados com sucesso no formulário!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao analisar JSON: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = "Editor de Templates", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Editor de Templates EA (Administrador Master)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text("Ajuste os parâmetros dos templates oficiais organizados pelas 9 seções do app ou altere o JSON bruto.", fontSize = 11.sp, color = Color.Gray)

            // Mode Toggle Selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isFormMode,
                    onClick = {
                        if (!isFormMode) {
                            loadJsonIntoForm(rawJsonText.value)
                            isFormMode = true
                        }
                    },
                    label = { Text("📝 Campos do Formulário", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = !isFormMode,
                    onClick = {
                        if (isFormMode) {
                            rawJsonText.value = buildJsonFromState()
                            isFormMode = false
                        }
                    },
                    label = { Text("📄 JSON Bruto", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }

            if (isFormMode) {
                // VISUAL FORM INPUTS MODE

                // Section 0: Template Metadata Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 0) null else 0 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Informações Básicas do Template", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(if (expandedSection == 0) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }

                        if (expandedSection == 0) {
                            OutlinedTextField(
                                value = tplId.value,
                                onValueChange = { tplId.value = it; rawJsonText.value = buildJsonFromState() },
                                label = { Text("ID Único do Template (ex: tpl_001_fimathe_m15)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = tplTitulo.value,
                                onValueChange = { tplTitulo.value = it; rawJsonText.value = buildJsonFromState() },
                                label = { Text("Título de Exibição") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = tplDescricao.value,
                                onValueChange = { tplDescricao.value = it; rawJsonText.value = buildJsonFromState() },
                                label = { Text("Descrição / Propósito do Setup") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = tplParidade.value,
                                    onValueChange = { tplParidade.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Paridade (ex: XAUUSD)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = tplPontosAtivo.value,
                                    onValueChange = { tplPontosAtivo.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Pontos (ex: 250 pts)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = tplDataPublicacao.value,
                                    onValueChange = { tplDataPublicacao.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Data de Publicação") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = tplValidoAte.value,
                                    onValueChange = { tplValidoAte.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Válido Até (Expiração)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = tplAutor.value,
                                    onValueChange = { tplAutor.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Autor do Template") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = tplVersaoMinimaEa.value,
                                    onValueChange = { tplVersaoMinimaEa.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Versão Mínima EA") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status Disponível no App", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = tplDisponivel.value,
                                    onCheckedChange = { tplDisponivel.value = it; rawJsonText.value = buildJsonFromState() }
                                )
                            }
                        }
                    }
                }

                // SECTION 1: AUTENTICAÇÃO & EXPIRAÇÃO
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 1) null else 1 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("1. Autenticação & Expiração", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 1) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 1) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = mt5AccountId.value,
                                    onValueChange = { mt5AccountId.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("mt5AccountId") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = senha.value,
                                    onValueChange = { senha.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("SENHA") },
                                    textStyle = TextStyle(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // SECTION 2: ESQUEMA DE CORES
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 2) null else 2 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2. Esquema de Cores MQL5", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 2) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 2) {
                            Text("ESQUEMA_CORES_ENUM:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("CYAN_NEON", "DARK_MATRIX", "GOLDEN_PRO", "PURPLE_NIGHT", "CLASSIC_BLUE", "CUSTOM")) { item ->
                                    FilterChip(
                                        selected = esquemaCoresEnum.value == item,
                                        onClick = { esquemaCoresEnum.value = item; rawJsonText.value = buildJsonFromState() },
                                        label = { Text(item, fontSize = 10.sp) }
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = corDeCanal.value,
                                    onValueChange = { corDeCanal.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("cor_de_canal") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = corDeLinhas.value,
                                    onValueChange = { corDeLinhas.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("cor_de_linhas") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = corrDeEquador.value,
                                    onValueChange = { corrDeEquador.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("corr_de_equador") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("LINHAS_DE_EQUADOR (Exibir no gráfico)", fontSize = 11.sp)
                                Switch(checked = linhasDeEquador.value, onCheckedChange = { linhasDeEquador.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                        }
                    }
                }

                // SECTION 3: CANAIS DE TENDÊNCIA
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 3) null else 3 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3. Canais de Tendência", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 3) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 3) {
                            Text("TREND / TENDÊNCIA:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = trend.value == "TENDENCIA_DE_ALTA",
                                    onClick = { trend.value = "TENDENCIA_DE_ALTA"; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("TENDENCIA_DE_ALTA 🟢", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = trend.value == "TENDENCIA_DE_BAIXA",
                                    onClick = { trend.value = "TENDENCIA_DE_BAIXA"; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("TENDENCIA_DE_BAIXA 🔴", fontSize = 10.sp) }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = mEquadorAlta.value,
                                    onValueChange = { mEquadorAlta.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("M_equador_alta") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = mEquadorBaixa.value,
                                    onValueChange = { mEquadorBaixa.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("M_equador_baixa") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // SECTION 4: ESTRATÉGIA PRINCIPAL
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 4) null else 4 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("4. Estratégia Principal", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 4) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 4) {
                            Text("ESTRATÉGIA:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = estrategia.value == "FIMATHE",
                                    onClick = { estrategia.value = "FIMATHE"; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("FIMATHE (Tradicional)", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = estrategia.value == "F_SURFADA",
                                    onClick = { estrategia.value = "F_SURFADA"; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("F_SURFADA (Grandes Tendências)", fontSize = 10.sp) }
                                )
                            }

                            Text("OperationalPeriod (Timeframe):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("PERIOD_M1", "PERIOD_M5", "PERIOD_M15", "PERIOD_M30", "PERIOD_H1", "PERIOD_H4", "PERIOD_D1")) { tf ->
                                    FilterChip(
                                        selected = operationalPeriod.value == tf,
                                        onClick = { operationalPeriod.value = tf; rawJsonText.value = buildJsonFromState() },
                                        label = { Text(tf, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = nives.value,
                                    onValueChange = { nives.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("Nives (Níveis)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("virada_de_jogo", fontSize = 11.sp)
                                Switch(checked = viradaDeJogo.value, onCheckedChange = { viradaDeJogo.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costurar (Operações de Hedge)", fontSize = 11.sp)
                                Switch(checked = costurar.value, onCheckedChange = { costurar.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TEMA (Média Móvel 9/21)", fontSize = 11.sp)
                                Switch(checked = tema.value, onCheckedChange = { tema.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                        }
                    }
                }

                // SECTION 5: AUTOMACÃO & SESSÕES
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 5) null else 5 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("5. Automação & Sessões de Mercado", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 5) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 5) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EA_ATIVO (Execução Geral)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Switch(checked = eaAtivo.value, onCheckedChange = { eaAtivo.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EA_AUTO (Automação MQL5)", fontSize = 11.sp)
                                Switch(checked = eaAuto.value, onCheckedChange = { eaAuto.value = it; rawJsonText.value = buildJsonFromState() })
                            }

                            Text("AUTO_PERIOD:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("MANUAL", "SESSOES", "SEMANAL", "DIARIO", "HORAS_8", "HORA_1")) { ap ->
                                    FilterChip(
                                        selected = autoPeriod.value == ap,
                                        onClick = { autoPeriod.value = ap; rawJsonText.value = buildJsonFromState() },
                                        label = { Text(ap, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("AUTO_SURFADA", fontSize = 11.sp)
                                Switch(checked = autoSurfada.value, onCheckedChange = { autoSurfada.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SESSAO_ASIA_TOQUIO", fontSize = 11.sp)
                                Switch(checked = sessaoAsiaToquio.value, onCheckedChange = { sessaoAsiaToquio.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SESSAO_LONDRES", fontSize = 11.sp)
                                Switch(checked = sessaoLondres.value, onCheckedChange = { sessaoLondres.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SESSAO_NOVA_YORQUI", fontSize = 11.sp)
                                Switch(checked = sessaoNovaYorqui.value, onCheckedChange = { sessaoNovaYorqui.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                        }
                    }
                }

                // SECTION 6: POSICIONAMENTO DE ORDEM
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 6) null else 6 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("6. Posicionamento de Ordem", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 6) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 6) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = expansaoMinima.value,
                                    onValueChange = { expansaoMinima.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("EXPANSAO_MINIMA") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = expansaoMaxima.value,
                                    onValueChange = { expansaoMaxima.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("EXPANSAO_MAXIMA") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = compra.value,
                                    onValueChange = { compra.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("compra") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = venda.value,
                                    onValueChange = { venda.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("venda") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = santo.value,
                                    onValueChange = { santo.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("santo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = dedo.value,
                                    onValueChange = { dedo.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("dedo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("posicaoTake", fontSize = 11.sp)
                                Switch(checked = posicaoTake.value, onCheckedChange = { posicaoTake.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = buyTake.value,
                                    onValueChange = { buyTake.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("buy_take") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sellTake.value,
                                    onValueChange = { sellTake.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("sell_take") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // SECTION 7: GESTÃO DE CAPITAL & RISCO
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 7) null else 7 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("7. Gestão de Capital & Risco", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 7) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 7) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = saldo.value,
                                    onValueChange = { saldo.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("SALDO Demo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = lot.value,
                                    onValueChange = { lot.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("lot (Padrão: 0.00)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Text("🛡️ Nota: O lote é mantido zerado (0.00) por segurança nos templates do Administrador.", fontSize = 10.sp, color = Color.Gray)

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GERENCIAMENTO_DE_RISCO_DIARIO", fontSize = 11.sp)
                                Switch(checked = gerenciamentoDeRiscoDiario.value, onCheckedChange = { gerenciamentoDeRiscoDiario.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = porcentos.value,
                                    onValueChange = { porcentos.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("porcentos (Risk %)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = porcentosg.value,
                                    onValueChange = { porcentosg.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("poercentosg (Gain %)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GERENCIAMENTO_DE_RISCO_SEMANAL", fontSize = 11.sp)
                                Switch(checked = gerenciamentoDeRiscoSemanal.value, onCheckedChange = { gerenciamentoDeRiscoSemanal.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = porcentoo.value,
                                    onValueChange = { porcentoo.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("PORCENTOO") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = porcentoss.value,
                                    onValueChange = { porcentoss.value = it; rawJsonText.value = buildJsonFromState() },
                                    label = { Text("PORCENTOSS") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // SECTION 8: PARÂMETROS OPERACIONAIS
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 8) null else 8 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("8. Parâmetros Operacionais", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 8) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 8) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ativar_ou_desativar_compra", fontSize = 11.sp)
                                Switch(checked = ativarOuDesativarCompra.value, onCheckedChange = { ativarOuDesativarCompra.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ativar_ou_desativar_venda", fontSize = 11.sp)
                                Switch(checked = ativarOuDesativarVenda.value, onCheckedChange = { ativarOuDesativarVenda.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Modify_Sl_For_OxO (Zero a Zero)", fontSize = 11.sp)
                                Switch(checked = modifySlForOxO.value, onCheckedChange = { modifySlForOxO.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("condicao_De_rompimento_c", fontSize = 11.sp)
                                Switch(checked = condicaoDeRompimentoC.value, onCheckedChange = { condicaoDeRompimentoC.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("condicao_De_rompimento_v", fontSize = 11.sp)
                                Switch(checked = condicaoDeRompimentoV.value, onCheckedChange = { condicaoDeRompimentoV.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GMAIL (Alertas por E-mail)", fontSize = 11.sp)
                                Switch(checked = gmail.value, onCheckedChange = { gmail.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("notific (Push Notifications)", fontSize = 11.sp)
                                Switch(checked = notific.value, onCheckedChange = { notific.value = it; rawJsonText.value = buildJsonFromState() })
                            }
                        }
                    }
                }

                // SECTION 9: RESULTADO & CÂMBIO
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expandedSection = if (expandedSection == 9) null else 9 },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("9. Resultado & Câmbio", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(if (expandedSection == 9) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expandedSection == 9) {
                            OutlinedTextField(
                                value = cambio.value,
                                onValueChange = { cambio.value = it; rawJsonText.value = buildJsonFromState() },
                                label = { Text("CAMBIO (Taxa de Conversão Meticais)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            } else {
                // RAW JSON TEXT MODE
                OutlinedTextField(
                    value = rawJsonText.value,
                    onValueChange = { rawJsonText.value = it },
                    label = { Text("Schema JSON dos Templates Admin (/dados/indices/instrucoes_admin_templates.json)") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 15
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { loadJsonIntoForm(rawJsonText.value) }) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sincronizar Campos do Formulário a partir deste JSON", fontSize = 11.sp)
                    }
                }
            }

            // ACTION BUTTONS
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val currentJson = if (isFormMode) buildJsonFromState() else rawJsonText.value
                        rawJsonText.value = currentJson
                        Toast.makeText(context, "JSON do template atualizado com base nos campos do formulário!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gerar JSON", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val payloadToPublish = if (isFormMode) buildJsonFromState() else rawJsonText.value
                        viewModel.publishAdminTemplateJson(payloadToPublish) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(2f).testTag("publish_admin_templates_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publicar no Servidor (/dados/indices/instrucoes_admin_templates.json)", fontSize = 11.sp)
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun ToUpperCaseText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        modifier = modifier,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    )
}

