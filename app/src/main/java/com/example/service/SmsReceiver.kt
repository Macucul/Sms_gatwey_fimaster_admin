package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.repository.SmsGatewayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SMS Broadcast received.")
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) {
                Log.w(TAG, "Message list from intent was empty.")
                return
            }

            // Group multi-part messages by sender index
            val sender = messages[0].displayOriginatingAddress ?: "Desconhecido"
            val fullBodyBuilder = StringBuilder()
            for (msg in messages) {
                fullBodyBuilder.append(msg.displayMessageBody)
            }
            val fullBody = fullBodyBuilder.toString()

            Log.d(TAG, "Incoming SMS from $sender. Length: ${fullBody.length}")

            // Enforce async processing as BroadcastReceiver life-cycles are short (<=10s)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = SmsGatewayRepository(context.applicationContext)
                    repository.processIncomingSms(sender, fullBody)
                    Log.d(TAG, "Async SMS processing completed successfully!")
                } catch (e: Exception) {
                    Log.e(TAG, "Error inside async SMS processor: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
