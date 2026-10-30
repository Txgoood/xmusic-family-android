package com.example.data.drive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.IOException

/** Yalnızca uygulamanın oluşturduğu klasörü bulur veya oluşturur. */
class DriveFolderClient {
    fun getOrCreate(token: String): String {
        require(token.isNotBlank() && !token.contains('\n') && !token.contains('\r'))
        val query = "trashed = false and mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='xmusicRole' and value='musicLibrary' }"
        val endpoint = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") +
            "&fields=files(id,name)&pageSize=100"
        val existing = request(endpoint, token, null).optJSONArray("files")
        if (existing != null && existing.length() > 0) return existing.getJSONObject(0).getString("id")
        val metadata = JSONObject().put("name", "XMusic Family")
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("appProperties", JSONObject().put("xmusicRole", "musicLibrary"))
        return request("https://www.googleapis.com/drive/v3/files?fields=id", token, metadata).getString("id")
    }

    private fun request(address: String, token: String, body: JSONObject?): JSONObject {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Drive klasör işlemi başarısız (HTTP $code)")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
