package com.example

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Roller ve çocuk kuralları yerel Android katmanında uygulanır. */
class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE)
    private fun records(): JSONArray {
        val rows = JSONArray(prefs.getString("profiles", null) ?: "[{\"id\":\"default\",\"name\":\"Ben\",\"salt\":\"\",\"hash\":\"\"}]")
        for (i in 0 until rows.length()) if (!rows.getJSONObject(i).has("role")) rows.getJSONObject(i).put("role", if (i == 0) "admin" else "adult")
        return rows
    }
    fun activeId(): String = prefs.getString("active", "default") ?: "default"
    private fun find(id: String, rows: JSONArray = records()): JSONObject? = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
    fun role(id: String = activeId()): String = find(id)?.optString("role", "adult") ?: "child"
    fun isChild(): Boolean = role() == "child"
    fun isAdmin(): Boolean = role() == "admin"
    fun hasParentPin(): Boolean = !prefs.getString("parent_hash", "").isNullOrEmpty()
    private fun date(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
    fun usedMillis(id: String = activeId()): Long = prefs.getLong("usage_${date()}_$id", 0L)
    fun recordListening(id: String, elapsed: Long) {
        if (elapsed <= 0 || role(id) != "child") return
        synchronized(ProfileStore::class.java) { val key = "usage_${date()}_$id"; prefs.edit().putLong(key, prefs.getLong(key, 0L) + elapsed.coerceAtMost(2000L)).commit() }
    }
    fun publicState(): String {
        val rows = records(); val result = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val id = row.getString("id")
            val r = row.getString("role")
            result.put(JSONObject().put("id", id).put("name", row.getString("name")).put("role", r)
                .put("hasPin", row.optString("hash").isNotEmpty()).put("needsParentPin", hasParentPin() && (r == "admin" || (isChild() && r != "child")))
                .put("dailyMinutes", row.optInt("dailyMinutes", 60)).put("usedMinutes", usedMillis(id) / 60000)
                .put("mixEnabled", row.optBoolean("mixEnabled", false)).put("approvedCount", row.optJSONArray("approved")?.length() ?: 0).put("pendingCount", row.optJSONArray("pending")?.length() ?: 0))
        }
        return JSONObject().put("active", activeId()).put("role", role()).put("parentConfigured", hasParentPin()).put("profiles", result).toString()
    }
    private fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 120000, 256)
        return try { Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, Base64.NO_WRAP) } finally { spec.clearPassword() }
    }
    private fun setHash(row: JSONObject, pin: String) {
        val salt = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        row.put("salt", if (pin.isEmpty()) "" else salt).put("hash", if (pin.isEmpty()) "" else hash(pin, salt))
    }
    private fun verify(row: JSONObject, pin: String): Boolean {
        if (row.optString("hash").isEmpty()) return true
        return MessageDigest.isEqual(row.getString("hash").toByteArray(), hash(pin, row.getString("salt")).toByteArray())
    }
    private fun checked(row: JSONObject, pin: String, key: String): Boolean {
        if (System.currentTimeMillis() < prefs.getLong("lock_$key", 0L)) return false
        if (!verify(row, pin)) {
            val count = prefs.getInt("tries_$key", 0) + 1
            prefs.edit().putInt("tries_$key", if (count >= 5) 0 else count).putLong("lock_$key", if (count >= 5) System.currentTimeMillis() + 60000 else 0).commit()
            return false
        }
        prefs.edit().remove("tries_$key").remove("lock_$key").commit(); return true
    }
    private fun parent(pin: String): Boolean = hasParentPin() && checked(JSONObject().put("hash", prefs.getString("parent_hash", "")).put("salt", prefs.getString("parent_salt", "")), pin, "parent")
    @Synchronized fun setParentPin(oldPin: String, newPin: String): Boolean {
        if (!isAdmin() || !newPin.matches(Regex("[0-9]{4,8}")) || (hasParentPin() && !parent(oldPin))) return false
        val row = JSONObject(); setHash(row, newPin)
        return prefs.edit().putString("parent_hash", row.getString("hash")).putString("parent_salt", row.getString("salt")).commit()
    }
    @Synchronized fun change(action: String, id: String, name: String, oldPin: String, newPin: String): Boolean {
        val rows = records(); val row = find(id, rows)
        if (action == "select") {
            if (row == null) return false
            val needsParent = hasParentPin() && (row.getString("role") == "admin" || (isChild() && row.getString("role") != "child"))
            if (needsParent) { if (!parent(oldPin)) return false } else if (!checked(row, oldPin, id)) return false
            return prefs.edit().putString("active", id).putString("profiles", rows.toString()).commit()
        }
        if (isChild()) return false
        if (action == "create") return saveProfile("", name, "adult", "", newPin, "", 60, false, false)
        if (row == null || !checked(row, oldPin, id)) return false
        if (action == "delete") {
            if (!isAdmin() || activeId() == id || (row.getString("role") == "admin" && (0 until rows.length()).count { rows.getJSONObject(it).getString("role") == "admin" } <= 1)) return false
            val filtered = JSONArray(); for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") != id) filtered.put(rows.getJSONObject(i))
            return prefs.edit().putString("profiles", filtered.toString()).commit()
        }
        if (action != "update" || (!isAdmin() && activeId() != id) || name.isBlank() || name.length > 60 || (newPin.isNotEmpty() && !newPin.matches(Regex("[0-9]{4,8}")))) return false
        row.put("name", name.trim()); setHash(row, newPin)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    @Synchronized fun saveProfile(id: String, name: String, newRole: String, oldPin: String, newPin: String, parentPin: String, dailyMinutes: Int, mix: Boolean, requireParent: Boolean = true): Boolean {
        if (isChild() || name.isBlank() || name.length > 60 || newRole !in setOf("admin", "adult", "child") || dailyMinutes !in 1..720 || (newPin.isNotEmpty() && !newPin.matches(Regex("[0-9]{4,8}")))) return false
        val rows = records(); val existing = if (id.isBlank()) null else (find(id, rows) ?: return false)
        if (!isAdmin() && (id != activeId() || existing?.getString("role") != newRole)) return false
        if (newRole == "child" && !hasParentPin()) return false
        if (requireParent && hasParentPin() && isAdmin() && !parent(parentPin)) return false
        if (existing != null && !checked(existing, oldPin, id)) return false
        if (existing?.getString("role") == "admin" && newRole != "admin" && (0 until rows.length()).count { rows.getJSONObject(it).getString("role") == "admin" } <= 1) return false
        if (existing == null && rows.length() >= 12) return false
        val target = existing ?: JSONObject().put("id", java.util.UUID.randomUUID().toString())
        target.put("name", name.trim()).put("role", newRole).put("dailyMinutes", dailyMinutes).put("mixEnabled", mix && newRole != "child")
        setHash(target, newPin)
        if (existing == null) rows.put(target)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    private fun key(id: String): String = if (id.startsWith("drive-source:")) "experimental:" + id.substringAfter(':') else id
    fun approved(id: String = activeId()): JSONArray = find(id)?.optJSONArray("approved") ?: JSONArray()
    fun playbackBlockReason(mediaId: String): String? {
        if (!isChild()) return null
        val row = find(activeId()) ?: return "Çocuk profili bulunamadı"
        if (usedMillis() >= row.optInt("dailyMinutes", 60) * 60000L) return "Günlük dinleme süresi doldu"
        val list = approved()
        if ((0 until list.length()).none { key(list.getJSONObject(it).optString("id")) == key(mediaId) }) return "Bu kayıt için ebeveyn onayı gerekiyor"
        return null
    }
    @Synchronized fun approve(childId: String, value: String, remove: Boolean, pin: String): Boolean {
        if (!isAdmin() || !parent(pin) || value.length > 5000) return false
        val rows = records(); val child = find(childId, rows) ?: return false
        if (child.getString("role") != "child") return false
        val track = try { JSONObject(value) } catch (_: Exception) { return false }
        val id = track.optString("id"); if (id.isBlank() || id.length > 500) return false
        val list = child.optJSONArray("approved") ?: JSONArray(); val updated = JSONArray()
        for (i in 0 until list.length()) if (key(list.getJSONObject(i).optString("id")) != key(id)) updated.put(list.getJSONObject(i))
        if (!remove) {
            if (updated.length() >= 500) return false
            updated.put(JSONObject().put("id", id).put("title", track.optString("title").take(180)).put("artist", track.optString("artist").take(180))
                .put("feed", track.optString("feed").take(2048)).put("artwork", track.optString("artwork").take(2048)))
        }
        child.put("approved", updated)
        if (!remove) for (field in listOf("pending", "denied")) {
            val old = child.optJSONArray(field) ?: continue; val keep = JSONArray()
            for (i in 0 until old.length()) { val item = old.getJSONObject(i); if (key(item.optString("id")) != key(id)) keep.put(item) }
            child.put(field, keep)
        }
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    // Başlık taraması yalnızca uyarı üretir; güvenli içerik kararı vermez.
    fun reviewReason(title: String): String = if (Regex("(?i)(explicit|uncensored|sansürsüz|sansursuz|\\b18\\+)").containsMatchIn(title))
        "Başlıkta yetişkin içerik işareti var; ebeveyn incelemesi gerekli" else "Güvenilir içerik/söz değerlendirmesi yok; ebeveyn onayı gerekli"
    fun childReview(id: String): String {
        if (!isAdmin() && id != activeId()) return "{}"
        val row = find(id) ?: return "{}"
        return JSONObject().put("ageGroup", row.optString("ageGroup", "under13"))
            .put("pending", row.optJSONArray("pending") ?: JSONArray()).put("denied", row.optJSONArray("denied") ?: JSONArray()).toString()
    }
    @Synchronized fun setChildAge(id: String, age: String, pin: String): Boolean {
        if (!isAdmin() || !parent(pin) || age !in setOf("under7", "under13", "teen")) return false
        val rows = records(); val row = find(id, rows) ?: return false
        if (row.getString("role") != "child") return false
        row.put("ageGroup", age)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    @Synchronized fun requestReview(id: String, raw: String): Boolean {
        if ((!isAdmin() && (id != activeId() || !isChild())) || raw.length > 5000) return false
        val rows = records(); val row = find(id, rows) ?: return false
        if (row.getString("role") != "child") return false
        val input = try { JSONObject(raw) } catch (_: Exception) { return false }
        val title = input.optString("title").trim().take(180); if (title.isEmpty()) return false
        val trackId = input.optString("id").take(500)
        val requestKey = if (trackId.isNotBlank()) key(trackId) else "query:" + title.lowercase(java.util.Locale.ROOT)
        for (field in listOf("approved", "denied", "pending")) {
            val list = row.optJSONArray(field) ?: continue
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optString("requestKey") == requestKey || (trackId.isNotBlank() && key(item.optString("id")) == key(trackId))) return true
            }
        }
        val list = row.optJSONArray("pending") ?: JSONArray(); if (list.length() >= 100) return false
        list.put(JSONObject().put("id", trackId).put("title", title).put("artist", input.optString("artist").take(180))
            .put("requestKey", requestKey).put("reason", reviewReason(title)).put("createdAt", System.currentTimeMillis()))
        row.put("pending", list)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    @Synchronized fun resolveReview(id: String, requestKey: String, deny: Boolean, pin: String): Boolean {
        if (!isAdmin() || !parent(pin)) return false
        val rows = records(); val row = find(id, rows) ?: return false
        if (row.getString("role") != "child") return false
        val pending = row.optJSONArray("pending") ?: JSONArray(); val keep = JSONArray(); var match: JSONObject? = null
        for (i in 0 until pending.length()) { val item = pending.getJSONObject(i); if (item.optString("requestKey") == requestKey) match = item else keep.put(item) }
        val found = match ?: return false
        if (!deny && (found.optString("id").isBlank() || (0 until approved(id).length()).none { key(approved(id).getJSONObject(it).optString("id")) == key(found.optString("id")) })) return false
        if (deny) { val denied = row.optJSONArray("denied") ?: JSONArray(); if (denied.length() >= 500) return false; denied.put(found); row.put("denied", denied) }
        row.put("pending", keep)
        return prefs.edit().putString("profiles", rows.toString()).commit()
    }
    fun mixProfileIds(): List<String> { val rows = records(); return (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.getString("role") != "child" && it.optBoolean("mixEnabled", false) }.map { it.getString("id") } }
}
