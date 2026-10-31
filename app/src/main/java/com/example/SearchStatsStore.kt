package com.example

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Her profilin en çok aranan ifadeleri, son aramalardan bağımsızdır. */
class SearchStatsStore(context: Context, profileId: String) {
    private val prefs = context.getSharedPreferences("xmusic_library_$profileId", Context.MODE_PRIVATE)
    @Synchronized fun record(query: String) {
        val term = query.trim().replace(Regex("\\s+"), " ")
        if (term.length !in 2..100) return
        val key = term.lowercase(Locale.forLanguageTag("tr-TR"))
        val list = read()
        val rows = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.toMutableList()
        val row = rows.firstOrNull { it.optString("query").lowercase(Locale.forLanguageTag("tr-TR")) == key }
            ?: org.json.JSONObject().put("query", term).put("count", 0).also { rows.add(it) }
        row.put("query", term).put("count", (row.optInt("count") + 1).coerceAtMost(1_000_000))
        val sorted = rows.sortedWith(compareByDescending<org.json.JSONObject> { it.optInt("count") }.thenBy { it.optString("query") }).take(100)
        check(prefs.edit().putString("searchStats", JSONArray(sorted).toString()).commit())
    }
    @Synchronized fun read(): JSONArray = try {
        JSONArray(prefs.getString("searchStats", "[]"))
    } catch (_: Exception) { JSONArray() }
}
