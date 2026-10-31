package com.example.data.drive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.IOException

/** Yalnızca uygulamanın oluşturduğu klasörü bulur veya oluşturur. */
open class DriveFolderClient {
    fun getOrCreate(token: String, selectedFolder: String = ""): String {
        if (selectedFolder.isNotEmpty()) {
            require(selectedFolder.matches(Regex("[A-Za-z0-9_-]{1,200}")))
            val info = request("https://www.googleapis.com/drive/v3/files/$selectedFolder?fields=id,name,mimeType,trashed,capabilities(canAddChildren)", token, null)
            require(info.optString("mimeType") == "application/vnd.google-apps.folder" && !info.optBoolean("trashed")) { "Seçilen öğe erişilebilir bir klasör değil." }
            require(info.optJSONObject("capabilities")?.optBoolean("canAddChildren") == true) { "Bu klasörde yükleme izni yok. Klasör sahibi seni Düzenleyici olarak eklemeli." }
            return info.getString("id")
        }
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

    data class Track(val id: String, val name: String, val sourceId: String = "", val path: String = "", val modified: String = "") {
        val displayName: String get() = if (path.isEmpty()) name else "$path / $name"
    }

    /** Seçilen kökün dışına çıkmaz. Klasörler toplu sorgulanır, kısayollar izlenmez. */
    fun listAudio(token: String, folderId: String): List<Track> {
        require(folderId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        val pending = java.util.ArrayDeque<Pair<String, String>>()
        pending.add(folderId to "")
        val visited = mutableSetOf(folderId); val seenFiles = mutableSetOf<String>()
        val tracks = mutableListOf<Track>(); var pages = 0
        while (pending.isNotEmpty()) {
            val batch = mutableListOf<Pair<String, String>>()
            repeat(minOf(30, pending.size)) { batch.add(pending.removeFirst()) }
            val paths = batch.toMap()
            val query = "(" + batch.joinToString(" or ") { "'${it.first}' in parents" } + ") and trashed=false"
            val seenPages = mutableSetOf<String>(); var page = ""
            do {
                require(++pages <= 500) { "Arşiv tarama sınırı aşıldı; önceki indeks korundu." }
                val address = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") +
                    "&fields=nextPageToken,incompleteSearch,files(id,name,mimeType,parents,modifiedTime,appProperties)&pageSize=1000&orderBy=name" +
                    if (page.isEmpty()) "" else "&pageToken=" + URLEncoder.encode(page, "UTF-8")
                val result = request(address, token, null)
                require(!result.optBoolean("incompleteSearch", false)) { "Drive eksik arama yanıtı döndürdü; önceki indeks korundu." }
                val rows = result.optJSONArray("files") ?: org.json.JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i); val id = row.optString("id")
                    if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) continue
                    val parents = row.optJSONArray("parents") ?: org.json.JSONArray()
                    val parent = (0 until parents.length()).map { parents.optString(it) }.firstOrNull { paths.containsKey(it) } ?: continue
                    val path = paths.getValue(parent); val name = row.optString("name", "Ses dosyası").take(255)
                    val mime = row.optString("mimeType")
                    if (mime == "application/vnd.google-apps.folder") {
                        if (name != ".thumbnails" && visited.add(id)) {
                            require(visited.size <= 5000) { "Klasör sınırı aşıldı; önceki indeks korundu." }
                            val next = if (path.isEmpty()) name else "$path/$name"
                            require(next.count { it == '/' } < 64) { "Klasör derinliği sınırı aşıldı; önceki indeks korundu." }
                            pending.add(id to next)
                        }
                    } else if (mime != "application/vnd.google-apps.shortcut") {
                        val audio = mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
                        if (audio && seenFiles.add(id)) {
                            require(tracks.size < 50000) { "Dosya sınırı aşıldı; önceki indeks korundu." }
                            tracks.add(Track(id, name, row.optJSONObject("appProperties")?.optString("youtubeId").orEmpty(), path, row.optString("modifiedTime")))
                        }
                    }
                }
                page = result.optString("nextPageToken")
                if (page.isNotEmpty() && !seenPages.add(page)) throw IOException("Drive listeleme yanıtı tekrarlandı")
            } while (page.isNotEmpty())
        }
        return tracks.sortedBy { it.displayName.lowercase(java.util.Locale.ROOT) }
    }

    fun accountId(token: String): String =
        request("https://www.googleapis.com/drive/v3/about?fields=user(permissionId)", token, null)
            .getJSONObject("user").getString("permissionId")

    fun findByHash(token: String, folderId: String, hash: String): String? {
        require(folderId.matches(Regex("[A-Za-z0-9_-]{1,200}")) && hash.matches(Regex("[a-f0-9]{64}")))
        val query = "'$folderId' in parents and trashed=false and appProperties has { key='contentSha256' and value='$hash' }"
        val rows = request("https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") +
            "&fields=files(id)&pageSize=1", token, null).optJSONArray("files")
        return if (rows != null && rows.length() > 0) rows.getJSONObject(0).getString("id") else null
    }

    fun findBySource(token: String, folderId: String, sourceId: String): String? {
        require(folderId.matches(Regex("[A-Za-z0-9_-]{1,200}")) && sourceId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val query = "'$folderId' in parents and trashed=false and appProperties has { key='youtubeId' and value='$sourceId' }"
        val rows = request("https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") +
            "&fields=files(id)&pageSize=1", token, null).optJSONArray("files")
        return if (rows != null && rows.length() > 0) rows.getJSONObject(0).getString("id") else null
    }

    protected open fun request(address: String, token: String, body: JSONObject?): JSONObject {
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




