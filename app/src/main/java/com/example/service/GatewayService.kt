package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.SmsGatewayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GatewayService : Service() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val CHANNEL_ID = "SmsGatewayChannel"
        private const val NOTIFICATION_ID = 1101
        
        var isServiceRunning = false
            private set

        var instance: GatewayService? = null
            private set

        fun startService(context: Context) {
            val intent = Intent(context, GatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GatewayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        instance = this
        createNotificationChannel()

        // Setup network monitoring for automatic background synchronization
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = SmsGatewayRepository(applicationContext)
                        if (repository.isSyncConfigured()) {
                            Log.d("GatewayService", "Internet disponível detectada em segundo plano! Sincronizando registros pendentes...")
                            repository.syncUnsyncedUsers()
                        } else {
                            Log.d("GatewayService", "Internet disponível. Sincronização em segundo plano pendente de credenciais.")
                        }
                    } catch (e: Exception) {
                        Log.e("GatewayService", "Falha ao sincronizar na rede em segundo plano: ${e.message}")
                    }
                }
            }
        }

        try {
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            connectivityManager?.registerNetworkCallback(builder.build(), networkCallback!!)
        } catch (e: Exception) {
            Log.e("GatewayService", "Erro ao registrar callback de rede: ${e.message}")
        }

        startPeriodicSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway Pro Activo")
            .setContentText("A escutar SMS de transações em segundo plano...")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call) // Safe fallback icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    fun startPeriodicSync() {
        syncJob?.cancel()
        val configManager = com.example.data.local.ConfigManager(applicationContext)
        if (!configManager.backgroundSyncEnabled) {
            Log.d("GatewayService", "Sincronização em segundo plano está desativada nas configurações.")
            return
        }

        Log.d("GatewayService", "Iniciando loop de sincronização automática com intervalo de ${configManager.syncIntervalMinutes} minutos.")
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                // Aguarda o intervalo inicial antes do primeiro sync ou executa e depois aguarda?
                // Vamos executar imediatamente e depois aguardar o intervalo.
                try {
                    val repository = SmsGatewayRepository(applicationContext)
                    repository.performBackgroundAutoSync()
                } catch (e: Exception) {
                    Log.e("GatewayService", "Erro na execução da sincronização em segundo plano: ${e.message}")
                }
                
                val minutes = configManager.syncIntervalMinutes
                val delayMillis = minutes * 60 * 1000L
                kotlinx.coroutines.delay(delayMillis)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        syncJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e("GatewayService", "Erro ao desregistrar callback de rede: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SMS Monitor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de segundo plano para o SMS Gateway Pro"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
