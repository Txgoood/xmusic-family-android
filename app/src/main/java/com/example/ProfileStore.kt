package com.example

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Yerel yetişkin profilleri. Çocuk güvenliği tamamlanmadan çocuk profili açılmaz. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE)
    private fun records(): JSONArray = JSONArray(prefs.getString("profiles", null)
        ?: "[{\"id\":\"default\",\"name\":\"Ben\",\"salt\":\"\",\"hash\":\"\"}]")
    fun activeId(): String = prefs.getString("active", "default") ?: "default"
    fun publicState(): String {
        val rows = records(); val result = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            result.put(JSONObject().put("id", row.getString("id")).put("name", row.getString("name"))
                .put("hasPin", row.optString("hash").isNotEmpty()))
        }
        return JSONObject().put("active", activeId()).put("profiles", result).toString()
    }
    private fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 120000, 256)
        return try { Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, Base64.NO_WRAP) }
        finally { spec.clearPassword() }
    }
    private fun verify(row: JSONObject, pin: String): Boolean {
        if (row.optString("hash").isEmpty()) return true
        return MessageDigest.isEqual(row.getString("hash").toByteArray(), hash(pin, row.getString("salt")).toByteArray())
    }
    @Synchronized fun change(action: String, id: String, name: String, oldPin: String, newPin: String): Boolean {
        val rows = records()
        val row = if (action == "create") null else (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
        if (action != "create") {
            if (row == null || System.currentTimeMillis() < prefs.getLong("lock_$id", 0L)) return false
            if (!verify(row, oldPin)) {
                val count = prefs.getInt("tries_$id", 0) + 1
                prefs.edit().putInt("tries_$id", if(count >= 5) 0 else count)
                    .putLong("lock_$id", if(count >= 5) System.currentTimeMillis() + 60000L else 0L).commit()
                return false
            }
            prefs.edit().remove("tries_$id").remove("lock_$id").commit()
        }
        if (action == "select") return prefs.edit().putString("active", id).commit()
        if (action == "delete") {
            if (rows.length() <= 1 || activeId() == id) return false
            val filtered = JSONArray();for(i in 0 until rows.length())if(rows.getJSONObject(i).getString("id")!=id)filtered.put(rows.getJSONObject(i))
            return prefs.edit().putString("profiles", filtered.toString()).commit()
        }
        if (action !in setOf("create", "update") || name.isBlank() || name.length > 60 ||
            (newPin.isNotEmpty() && !newPin.matches(Regex("[0-9]{4,8}")))) return false
        if (action == "create" && rows.length() >= 12) return false
        val target = row ?: JSONObject().put("id", java.util.UUID.randomUUID().toString())
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(salt, Base64.NO_WRAP)
        target.put("name", name.trim()).put("salt", if(newPin.isEmpty()) "" else encoded)
            .put("hash", if(newPin.isEmpty()) "" else hash(newPin, encoded))
        if (row == null) rows.put(target)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
}
