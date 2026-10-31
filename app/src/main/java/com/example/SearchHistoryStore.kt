package com.example

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Son aramaları profilden ayırarak cihazda tutar; en yeni kayıt ilk sıradadır. */
class SearchHistoryStore(context: Context, profileId: String) {
    private val prefs = context.getSharedPreferences("xmusic_library_$profileId", Context.MODE_PRIVATE)

    @Synchronized
    fun update(action: String, query: String): String {
        val rows = try { JSONArray(prefs.getString("searchHistory", "[]")) } catch (_: Exception) { JSONArray() }
        val current = (0 until rows.length()).mapNotNull { rows.optString(it).takeIf { text -> text.isNotBlank() } }.take(12)
        val text = query.replace(Regex("\\s+"), " ").trim()
        val key = text.lowercase(Locale.forLanguageTag("tr-TR"))
        val next = when (action) {
            "read" -> return JSONArray(current).toString()
            "clear" -> emptyList()
            "remove" -> current.filter { it.lowercase(Locale.forLanguageTag("tr-TR")) != key }
            "add" -> {
                if (text.length !in 2..100) return JSONArray(current).toString()
                listOf(text) + current.filter { it.lowercase(Locale.forLanguageTag("tr-TR")) != key }
            }
            else -> return JSONArray(current).toString()
        }.take(12)
        check(prefs.edit().putString("searchHistory", JSONArray(next).toString()).commit()) {
            "Arama geçmişi kaydedilemedi."
        }
        return JSONArray(next).toString()
    }
}
