package com.example

import android.content.Context
import org.json.JSONObject

/** Oynatıcı arka plandayken de en son konumu profil bazında saklar. */
class PlaybackResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_playback_resume", Context.MODE_PRIVATE)
    fun save(profile: String, value: JSONObject) {
        prefs.edit().putString(profile, value.toString()).apply()
    }
    fun load(profile: String): String = prefs.getString(profile, "{}") ?: "{}"
    fun clear(profile: String) { prefs.edit().remove(profile).apply() }
}
