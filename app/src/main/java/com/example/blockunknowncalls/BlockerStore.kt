package com.example.blockunknowncalls

import android.content.Context
import android.content.SharedPreferences

/**
 * Acesso centralizado às preferências usadas pelo app e pelo serviço de bloqueio.
 */
object BlockerStore {
    const val PREFS = "Settings"
    const val KEY_ENABLED = "enabled"
    const val KEY_AUTO_BLOCK = "auto_block"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_HISTORY = "history_log"
    private const val KEY_HISTORY_LEGACY = "history"
    private const val MAX_HISTORY = 200

    data class Entry(val number: String, val time: Long)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Histórico ----------

    fun history(prefs: SharedPreferences): List<Entry> {
        migrateLegacyHistory(prefs)
        return prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().map(::parse)
    }

    fun addToHistory(prefs: SharedPreferences, number: String) {
        val set = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().toMutableSet()
        set.add("${System.currentTimeMillis()};$number")
        val trimmed = if (set.size > MAX_HISTORY) {
            set.sortedByDescending { parse(it).time }.take(MAX_HISTORY).toSet()
        } else set
        prefs.edit().putStringSet(KEY_HISTORY, trimmed).apply()
    }

    fun removeFromHistory(prefs: SharedPreferences, number: String) {
        val set = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty()
            .filterNot { parse(it).number == number }.toSet()
        prefs.edit().putStringSet(KEY_HISTORY, set).apply()
    }

    fun clearHistory(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_HISTORY).remove(KEY_HISTORY_LEGACY).apply()
    }

    private fun parse(raw: String): Entry {
        val i = raw.indexOf(';')
        if (i < 0) return Entry(raw, 0)
        return Entry(raw.substring(i + 1), raw.substring(0, i).toLongOrNull() ?: 0)
    }

    /** Versões antigas guardavam só o número, sem horário. */
    private fun migrateLegacyHistory(prefs: SharedPreferences) {
        val legacy = prefs.getStringSet(KEY_HISTORY_LEGACY, null) ?: return
        val set = prefs.getStringSet(KEY_HISTORY, emptySet()).orEmpty().toMutableSet()
        legacy.forEach { set.add("0;$it") }
        prefs.edit().putStringSet(KEY_HISTORY, set).remove(KEY_HISTORY_LEGACY).apply()
    }

    // ---------- Whitelist ----------

    fun whitelist(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_WHITELIST, emptySet()).orEmpty()

    fun addToWhitelist(prefs: SharedPreferences, number: String) {
        val set = whitelist(prefs).toMutableSet()
        set.add(number)
        prefs.edit().putStringSet(KEY_WHITELIST, set).apply()
    }

    fun removeFromWhitelist(prefs: SharedPreferences, number: String) {
        val set = whitelist(prefs).toMutableSet()
        set.remove(number)
        prefs.edit().putStringSet(KEY_WHITELIST, set).apply()
    }

    fun isWhitelisted(prefs: SharedPreferences, number: String): Boolean =
        whitelist(prefs).any { sameNumber(it, number) }

    // ---------- Comparação de números ----------

    /**
     * Compara pelos últimos 8 dígitos (número local brasileiro), ignorando
     * DDI, DDD, 9º dígito e código de operadora. Números curtos precisam ser iguais.
     */
    fun sameNumber(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit)
        val db = b.filter(Char::isDigit)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da.length < 8 || db.length < 8) return da == db
        return da.takeLast(8) == db.takeLast(8)
    }
}
