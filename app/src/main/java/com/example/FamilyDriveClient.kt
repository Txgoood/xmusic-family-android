package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Her cihaz yalnızca kendi dosyasını yazar; ortak ayar dosyasını kurucu imzalar. */
class FamilyDriveClient(private val context: Context) {
    private val store = FamilySyncStore(context)
    private val profiles = ProfileStore(context)
    private fun valid(id: String): String { require(id.matches(Regex("[A-Za-z0-9_-]{1,200}"))); return id }
    private fun request(url: String, token: String, method: String = "GET", body: String? = null, type: String = "application/json; charset=UTF-8"): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000; conn.readTimeout = 20000; conn.instanceFollowRedirects = false
            conn.requestMethod = method; conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { require(body.length <= 4_000_000); conn.doOutput = true; conn.setRequestProperty("Content-Type", type); conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
            require(conn.responseCode in 200..299) { "Aile Drive bağlantısı başarısız (HTTP ${conn.responseCode}). Hesap, klasör ve otomatik arşiv iznini kontrol et." }
            return conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 4_000_000); output.write(buffer, 0, n) }
                output.toString("UTF-8")
            }
        } finally { conn.disconnect() }
    }
    private fun read(token: String, id: String) = request("https://www.googleapis.com/drive/v3/files/${valid(id)}?alt=media", token)
    private fun write(token: String, id: String, data: String) {
        request("https://www.googleapis.com/upload/drive/v3/files/${valid(id)}?uploadType=media&fields=id", token, "PATCH", data, "application/octet-stream")
    }
    private fun create(token: String, folder: String, kind: String, data: String): String {
        val boundary = "xm_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val metadata = JSONObject().put("name", if (kind == "root") ".xmusic-family-settings" else ".xmusic-family-" + store.deviceId())
            .put("parents", JSONArray().put(valid(folder))).put("mimeType", "application/octet-stream")
            .put("appProperties", JSONObject().put("xmusicFamily", store.familyId()).put("xmusicFamilyKind", kind))
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n$data\r\n--$boundary--\r\n"
        return JSONObject(request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token, "POST", body, "multipart/related; boundary=$boundary")).getString("id")
    }
    fun createFamily(token: String, folder: String, account: String) {
        require(profiles.isAdmin() && profiles.hasParentPin() && !store.enabled()) { "Yönetici profilinde ebeveyn PIN’i oluştur; sonra aileyi kur." }
        profiles.makeGlobalIds(); store.prepare(folder, account)
        try {
            val revision = store.nextRevision()
            val id = create(token, folder, "root", store.seal(store.signedPolicy(profiles.policy(store.familyId(), revision))))
            store.finishCreate(id); store.revision(revision); sync(token)
        } catch (e: Exception) { store.abortCreate(); throw e }
    }
    fun preview(token: String, invitation: JSONObject): JSONObject {
        val data = store.verifyPolicy(store.open(read(token, invitation.getString("root")), invitation), invitation)
        profiles.validatePolicy(data)
        return data
    }
    fun join(token: String, account: String, invitation: JSONObject, profile: String, freshPolicy: JSONObject) {
        require(!profiles.isChild() && !store.enabled())
        require(profiles.validatePolicy(freshPolicy).let { rows -> (0 until rows.length()).any { rows.getJSONObject(it).getString("id") == profile } })
        store.accept(invitation, account)
        profiles.applyPolicy(freshPolicy, profile); store.revision(freshPolicy.getLong("revision"))
        store.setSharing(profile, true)
        sync(token)
    }
    fun sync(token: String): Boolean {
        require(store.enabled())
        var changed = false
        if (!store.owner()) {
            val policy = store.verifyPolicy(store.open(read(token, store.root())))
            val before = profiles.policy(store.familyId(), 0).toString()
            profiles.applyPolicy(policy); store.revision(policy.getLong("revision"))
            changed = before != profiles.policy(store.familyId(), 0).toString()
        }
        val own = profiles.deviceSnapshot(store.familyId(), store.deviceId())
        val encrypted = store.seal(own)
        if (store.deviceFile().isEmpty()) store.deviceFile(create(token, store.folder(), "device", encrypted)) else write(token, store.deviceFile(), encrypted)
        val devices = JSONArray().put(own); val seen = mutableSetOf(store.deviceId())
        val query = "'${valid(store.folder())}' in parents and trashed=false and appProperties has { key='xmusicFamily' and value='${valid(store.familyId())}' } and appProperties has { key='xmusicFamilyKind' and value='device' }"
        var page = ""; var count = 0
        do {
            val result = JSONObject(request("https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") + "&fields=nextPageToken,files(id)&pageSize=100" + if (page.isEmpty()) "" else "&pageToken=" + URLEncoder.encode(page, "UTF-8"), token))
            val files = result.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                require(++count <= 50) { "Aile cihaz sınırı aşıldı." }
                val file = files.getJSONObject(i).getString("id"); if (file == store.deviceFile()) continue
                // Hatalı tek bir cihaz dosyası diğer üyelerin eşitlenmesini durdurmaz.
                val device = try { store.open(read(token, file)) } catch (_: Exception) { continue }
                val deviceId = device.optString("device")
                if (device.optString("family") != store.familyId() || !deviceId.matches(Regex("[A-Za-z0-9_-]{1,200}")) || !seen.add(deviceId)) continue
                devices.put(device)
            }
            page = result.optString("nextPageToken")
        } while (page.isNotEmpty())
        store.cache(devices)
        if (store.owner()) {
            profiles.mergeRemoteRequests(devices)
            val revision = store.nextRevision()
            write(token, store.root(), store.seal(store.signedPolicy(profiles.policy(store.familyId(), revision))))
            store.revision(revision)
        }
        store.status(); return changed
    }
}
