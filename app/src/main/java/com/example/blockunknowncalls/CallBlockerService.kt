package com.example.blockunknowncalls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat

class CallBlockerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = BlockerStore.prefs(this)
        val isEnabled = prefs.getBoolean(BlockerStore.KEY_ENABLED, true)
        val shouldBlock = prefs.getBoolean(BlockerStore.KEY_AUTO_BLOCK, true)

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""

        val inWhitelist = BlockerStore.isWhitelisted(prefs, phoneNumber)

        // 1. Condições para deixar passar:
        // App desativado, contatos, whitelist OU números importantes (0800, emergência, etc)
        if (!isEnabled || isImportantNumber(phoneNumber) || isNumberInContacts(phoneNumber) || inWhitelist) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 2. Salvar no histórico de bloqueados
        BlockerStore.addToHistory(prefs, phoneNumber)

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

        // 2. Números gratuitos (0800)
        if (cleanNumber.startsWith("0800")) return true


        return false
    }

    private fun isNumberInContacts(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        // Sem permissão não dá para saber se é contato: na dúvida, não bloqueia.
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return true
        }

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

        // Fallback: o PhoneLookup do Android falha com vários formatos brasileiros
        // (contato salvo sem o 9º dígito, com código de operadora "0 15 11...", sem DDD etc).
        // Compara os últimos 8 dígitos (número local) com todos os telefones salvos.
        return matchesAnyContactBySuffix(cleanNumber)
    }

    private fun matchesAnyContactBySuffix(cleanIncoming: String): Boolean {
        if (cleanIncoming.length < 8) return false
        val incomingSuffix = cleanIncoming.takeLast(8)

        return try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    for (col in 0..1) {
                        val digits = it.getString(col)?.replace(Regex("\\D"), "") ?: continue
                        if (digits.length >= 8 && digits.takeLast(8) == incomingSuffix) return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
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

    private fun sendNotification(title: String, message: String) {
        val channelId = "blocker_logs"
        val manager = getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Logs de Bloqueio", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}