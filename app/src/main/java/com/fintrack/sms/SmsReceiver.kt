package com.fintrack.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.fintrack.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val grouped = messages.groupBy { msg ->
            val sender = msg.originatingAddress.orEmpty()
            val timestamp = msg.timestampMillis
            sender to timestamp
        }

        val parsed = grouped.mapNotNull { (key, segments) ->
            val sender = key.first
            val timestamp = key.second
            val body = segments.joinToString(separator = "") { it.messageBody.orEmpty() }
            SmsParser.parse(sender = sender, body = body, timestampMillis = timestamp)
        }

        if (parsed.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(context).transactionDao().insertAll(parsed)
        }
    }
}
