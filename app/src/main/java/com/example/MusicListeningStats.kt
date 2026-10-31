package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Yalnız bu cihazdaki profillerin gerçek başlayan müzik çalmalarını sayar. */
class MusicListeningStats(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("xmusic_listening_genres", Context.MODE_PRIVATE)
    companion object {
        private val lock = Any()
        private val genres = setOf("Pop","Rock","Rap & Hip-Hop","Çocuk","Elektronik","Akustik","Tasavvuf & Klasik","Caz","Klasik")
    }
    fun assign(videoId: String, genre: String) = synchronized(lock) {
        if (videoId.matches(Regex("[A-Za-z0-9_-]{11}")) && genre in genres)
            prefs.edit().putString("track_$videoId", genre).apply()
    }
    fun record(mediaId: String) = synchronized(lock) {
        if (!listOf("experimental:", "drive-source:", "audius:", "local:", "drive:").any { mediaId.startsWith(it) }) return@synchronized
        val id = mediaId.substringAfter(':')
        val genre = prefs.getString("track_$id", null)?.takeIf { it in genres } ?: "Diğer"
        val key = "profile_" + ProfileStore(context).activeId()
        val counts = try { JSONObject(prefs.getString(key, "{}")) } catch (_: Exception) { JSONObject() }
        counts.put(genre, (counts.optInt(genre, 0) + 1).coerceAtMost(1_000_000))
        prefs.edit().putString(key, counts.toString()).apply()
    }
    fun removeProfile(profileId: String) = synchronized(lock) { prefs.edit().remove("profile_$profileId").apply() }
    fun summary(): String = synchronized(lock) {
        val totals = mutableMapOf<String, Int>()
        prefs.all.filterKeys { it.startsWith("profile_") }.values.forEach { raw ->
            try {
                val obj = JSONObject(raw as String)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key in genres || key == "Diğer") totals[key] = (totals[key] ?: 0) + obj.optInt(key, 0).coerceAtLeast(0)
                }
            } catch (_: Exception) { }
        }
        val rows = JSONArray()
        totals.entries.sortedByDescending { it.value }.forEach { rows.put(JSONObject().put("genre", it.key).put("count", it.value)) }
        JSONObject().put("items", rows).put("total", totals.values.sum()).toString()
    }
}
