package com.example.blockunknowncalls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat

class CallBlockerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val sharedPref = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isEnabled = sharedPref.getBoolean("enabled", true)
        val shouldBlock = sharedPref.getBoolean("auto_block", true)
        val whitelist = sharedPref.getStringSet("whitelist", setOf()) ?: setOf()

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""

        // 1. Condições para deixar passar
        if (!isEnabled || isNumberInContacts(phoneNumber) || whitelist.contains(phoneNumber)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 2. Salvar no histórico de bloqueados
        saveToHistory(phoneNumber)

        // 3. Executar Bloqueio ou Silenciamento
        val responseBuilder = CallResponse.Builder()
        if (shouldBlock) {
            responseBuilder.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
            sendNotification("Número Bloqueado", "Chamada de $phoneNumber rejeitada.")
        } else {
            responseBuilder.setDisallowCall(false)
                .setSkipNotification(true)
                .setSilenceCall(true)
            sendNotification("Chamada Silenciada", "Número $phoneNumber em silêncio.")
        }

        respondToCall(callDetails, responseBuilder.build())
    }

    private fun saveToHistory(number: String) {
        val sharedPref = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val history = sharedPref.getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        history.add(number)
        sharedPref.edit().putStringSet("history", history).apply()
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "blocker_logs"
        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Logs de Bloqueio", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun isNumberInContacts(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        val exists = cursor?.use { it.count > 0 } ?: false
        return exists
    }
}