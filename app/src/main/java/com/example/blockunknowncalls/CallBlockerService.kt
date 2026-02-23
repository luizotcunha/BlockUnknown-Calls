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

        val inWhitelist = whitelist.any { isSameNumber(it, phoneNumber) }

        // 1. Condições para deixar passar:
        // App desativado, contatos, whitelist OU números importantes (0800, emergência, etc)
        if (!isEnabled || isImportantNumber(phoneNumber) || isNumberInContacts(phoneNumber) || inWhitelist) {
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

    /**
     * Identifica números de emergência, utilidade pública e centrais de atendimento.
     */
    private fun isImportantNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("\\D"), "")

        // Se a string ficou vazia por algum motivo, não é importante
        if (cleanNumber.isEmpty()) return false

        // 1. Números curtos de emergência e operadoras (ex: 190, 192, 104, 136, etc.)
        // Geralmente possuem entre 3 e 5 dígitos.
        if (cleanNumber.length in 3..5) return true

        // 2. Prefixos gratuitos e de custo compartilhado (0800, 0300)
        if (cleanNumber.startsWith("0800")) return true


        return false
    }

    private fun isSameNumber(num1: String, num2: String): Boolean {
        val clean1 = num1.replace(Regex("\\D"), "")
        val clean2 = num2.replace(Regex("\\D"), "")
        if (clean1.isEmpty() || clean2.isEmpty()) return false

        return clean1.endsWith(clean2) || clean2.endsWith(clean1)
    }

    private fun isNumberInContacts(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        if (checkPhoneLookup(phoneNumber)) return true

        if (!phoneNumber.startsWith("+")) {
            if (checkPhoneLookup("+$phoneNumber")) return true
        }

        val cleanNumber = phoneNumber.replace(Regex("\\D"), "")

        if (cleanNumber.startsWith("55") && cleanNumber.length >= 12) {
            val semDDI = cleanNumber.substring(2)
            if (checkPhoneLookup(semDDI)) return true

            val semDDD = semDDI.substring(2)
            if (checkPhoneLookup(semDDD)) return true
        }

        return false
    }

    private fun checkPhoneLookup(number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
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
}