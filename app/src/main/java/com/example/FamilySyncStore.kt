package com.example

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Drive yalnızca şifreli veri taşır. Çocuk politikalarını kurucu cihaz imzalar. */
class FamilySyncStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_family_sync", Context.MODE_PRIVATE)
    fun enabled() = prefs.contains("root")
    fun owner() = enabled() && prefs.contains("privateKey")
    fun mayManage() = !enabled() || owner()
    fun folder(): String = prefs.getString("folder", "").orEmpty()
    fun account(): String = prefs.getString("account", "").orEmpty()
    fun root(): String = prefs.getString("root", "").orEmpty()
    fun familyId(): String = prefs.getString("family", "").orEmpty()
    fun deviceId(): String {
        var id = prefs.getString("device", "").orEmpty()
        if (id.isEmpty()) { id = java.util.UUID.randomUUID().toString(); prefs.edit().putString("device", id).commit() }
        return id
    }
    private fun enc(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun dec(text: String) = Base64.decode(text, Base64.NO_WRAP or Base64.URL_SAFE)
    fun state(): String = JSONObject().put("enabled", enabled()).put("owner", owner()).put("lastSync", prefs.getLong("lastSync", 0))
        .put("error", prefs.getString("error", "")).put("members", prefs.getInt("members", 0)).toString()
    fun status(error: String = "") { val edit = prefs.edit().putString("error", error); if (error.isEmpty()) edit.putLong("lastSync", System.currentTimeMillis()); edit.commit() }
    fun prepare(folder: String, account: String) {
        require(!enabled())
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        prefs.edit().clear().putString("folder", folder).putString("account", account).putString("family", java.util.UUID.randomUUID().toString())
            .putString("key", enc(key)).putString("publicKey", enc(pair.public.encoded)).putString("privateKey", enc(pair.private.encoded)).commit()
        deviceId()
    }
    fun finishCreate(root: String) { prefs.edit().putString("root", root).commit() }
    fun abortCreate() { if (!enabled()) prefs.edit().clear().commit() }
    fun invitation(): String {
        require(owner())
        return "XMF1." + enc(JSONObject().put("v", 1).put("folder", folder()).put("root", root()).put("family", familyId())
            .put("key", prefs.getString("key", "")).put("publicKey", prefs.getString("publicKey", "")).toString().toByteArray())
    }
    fun parseInvitation(code: String): JSONObject {
        require(code.startsWith("XMF1.") && code.length < 4000) { "Aile bağlantı kodu geçersiz." }
        val data = JSONObject(String(dec(code.substring(5)), Charsets.UTF_8))
        require(data.getInt("v") == 1 && dec(data.getString("key")).size == 32)
        for (field in listOf("root", "folder", "family")) require(data.getString(field).matches(Regex("[A-Za-z0-9_-]{1,200}")))
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(dec(data.getString("publicKey"))))
        return data
    }
    fun accept(data: JSONObject, account: String) {
        require(!enabled())
        val edit = prefs.edit().clear().putString("account", account)
        for (field in listOf("root", "folder", "family", "key", "publicKey")) edit.putString(field, data.getString(field))
        edit.commit(); deviceId()
    }
    fun seal(data: JSONObject, invitation: JSONObject? = null): String {
        val family = invitation?.getString("family") ?: familyId()
        val key = invitation?.getString("key") ?: prefs.getString("key", "").orEmpty()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dec(key), "AES"), GCMParameterSpec(128, iv)); cipher.updateAAD(family.toByteArray())
        return JSONObject().put("v", 1).put("iv", enc(iv)).put("data", enc(cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8)))).toString()
    }
    fun open(raw: String, invitation: JSONObject? = null): JSONObject {
        require(raw.length <= 4_000_000)
        val envelope = JSONObject(raw); require(envelope.getInt("v") == 1)
        val family = invitation?.getString("family") ?: familyId()
        val key = invitation?.getString("key") ?: prefs.getString("key", "").orEmpty()
        val iv = dec(envelope.getString("iv")); require(iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dec(key), "AES"), GCMParameterSpec(128, iv)); cipher.updateAAD(family.toByteArray())
        return JSONObject(String(cipher.doFinal(dec(envelope.getString("data"))), Charsets.UTF_8))
    }
    fun signedPolicy(policy: JSONObject): JSONObject {
        require(prefs.contains("privateKey"))
        val payload = policy.toString()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(dec(prefs.getString("privateKey", "").orEmpty()))))
        signer.update(payload.toByteArray(Charsets.UTF_8))
        return JSONObject().put("payload", payload).put("signature", enc(signer.sign()))
    }
    fun verifyPolicy(packet: JSONObject, invitation: JSONObject? = null): JSONObject {
        val publicKey = invitation?.getString("publicKey") ?: prefs.getString("publicKey", "").orEmpty()
        val payload = packet.getString("payload"); require(payload.length <= 2_000_000)
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(dec(publicKey))))
        verifier.update(payload.toByteArray(Charsets.UTF_8)); require(verifier.verify(dec(packet.getString("signature")))) { "Aile ayarlarının imzası doğrulanamadı." }
        val policy = JSONObject(payload)
        require(policy.getString("family") == (invitation?.getString("family") ?: familyId()))
        require(policy.getLong("revision") >= prefs.getLong("revision", 0)) { "Eski aile ayarları uygulanmadı." }
        return policy
    }
    fun revision(value: Long) { prefs.edit().putLong("revision", value).commit() }
    fun nextRevision(): Long = maxOf(System.currentTimeMillis(), prefs.getLong("revision", 0) + 1)
    fun deviceFile(): String = prefs.getString("deviceFile", "").orEmpty()
    fun deviceFile(id: String) { prefs.edit().putString("deviceFile", id).commit() }
    fun cache(devices: JSONArray) { prefs.edit().putString("devices", devices.toString()).putInt("members", devices.length()).commit() }
    private fun cached(): JSONArray = try { JSONArray(prefs.getString("devices", "[]")) } catch (_: Exception) { JSONArray() }
    fun remoteUsage(profile: String, day: String): Long {
        val rows = cached(); var total = 0L
        for (i in 0 until rows.length()) {
            val device = rows.getJSONObject(i); if (device.optString("device") == deviceId() || device.optString("day") != day) continue
            val usage = device.optJSONObject("usage")?.optLong(profile, 0L) ?: 0L
            total += usage.coerceIn(0L, 86_400_000L)
        }
        return total
    }
    fun isSharing(id: String, fallback: Boolean): Boolean = prefs.getBoolean("share_$id", fallback)
    fun setSharing(id: String, value: Boolean) { prefs.edit().putBoolean("share_$id", value).commit() }
    fun remoteMix(kind: String): JSONArray {
        val result = JSONArray(); val devices = cached()
        val members = JSONObject(ProfileStore(context).publicState()).getJSONArray("profiles")
        val allowed = (0 until members.length()).map { members.getJSONObject(it) }.filter { it.getString("role") != "child" }.map { it.getString("id") }.toSet()
        for (i in 0 until devices.length()) {
            val device = devices.getJSONObject(i); if (device.optString("device") == deviceId()) continue
            val rows = device.optJSONArray("profiles") ?: continue
            for (n in 0 until rows.length()) {
                val profile = rows.getJSONObject(n); if (profile.optString("id") !in allowed) continue
                val tracks = profile.optJSONArray(kind) ?: continue
                for (t in 0 until minOf(100, tracks.length())) { if (result.length() >= 500) return result; result.put(tracks.getJSONObject(t)) }
            }
        }
        return result
    }
}
