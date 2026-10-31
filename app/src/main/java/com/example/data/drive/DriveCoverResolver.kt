package com.example.data.drive

import android.content.Context
import com.example.CoverArtStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Kapak erişimi ses akışından ayrı iş parçacığında, sınırlı boyutta yapılır. */
open class DriveCoverResolver(context: Context) {
    private val cache = CoverArtStore(context)
    protected open fun read(address: String, token: String? = null, max: Int = 2_000_000): ByteArray {
        val url = URL(address)
        require(url.protocol == "https" && (url.host == "www.googleapis.com" || url.host == "itunes.apple.com" || url.host.endsWith(".mzstatic.com")))
        require(token == null || url.host == "www.googleapis.com")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000; conn.readTimeout = 4000; conn.instanceFollowRedirects = false
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            require(conn.responseCode == 200)
            require(conn.contentLengthLong <= max || conn.contentLengthLong == -1L)
            return conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= max); out.write(buffer, 0, n) }
                out.toByteArray()
            }
        } finally { conn.disconnect() }
    }
    fun resolve(key: String, scope: String, id: String, parent: String, title: String, artist: String, album: String, token: String, active: () -> Boolean) {
        if (!active() || cache.priority(key) >= 2 || !cache.retryAllowed(key)) return
        var failed = false
        val albumKey = CoverArtStore.key(scope, "album:$parent", "v1")
        val parents = parent.split(',').filter { it.matches(Regex("[A-Za-z0-9_-]{1,200}")) }.take(3)
        if (parents.isNotEmpty()) {
            if (cache.copy(albumKey, key, 2)) return
            try {
                if (cache.retryAllowed(albumKey)) {
                    val q = "(" + parents.joinToString(" or ") { "'$it' in parents" } + ") and trashed=false and (mimeType='image/jpeg' or mimeType='image/png' or mimeType='image/webp')"
                    val json = JSONObject(String(read("https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") + "&fields=files(id,name,size,parents)&pageSize=50", token, 128000), Charsets.UTF_8))
                    val rows = json.optJSONArray("files")
                    val images = if (rows == null) emptyList() else (0 until rows.length()).map { rows.getJSONObject(it) }
                    val candidate = images.filter { it.optLong("size", 0) <= 2_000_000 && rankName(it.optString("name")) < 100 }.minByOrNull { image ->
                        val ids = image.optJSONArray("parents")
                        val distance = parents.indexOfFirst { id -> ids != null && (0 until ids.length()).any { ids.optString(it) == id } }.let { if (it < 0) 99 else it }
                        distance * 100 + rankName(image.optString("name"))
                    }
                    if (candidate != null && rankName(candidate.optString("name")) < 100 && active()) {
                        val imageId = candidate.optString("id")
                        if (imageId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) {
                            val bytes = read("https://www.googleapis.com/drive/v3/files/$imageId?alt=media", token)
                            if (active()) { cache.save(albumKey, bytes, 2); cache.copy(albumKey, key, 2) }
                        }
                    }
                    cache.retryAfter(albumKey, 6 * 60 * 60 * 1000L)
                }
            } catch (_: Exception) { failed = true; cache.retryAfter(albumKey, 10 * 60 * 1000L) }
        }
        if (!active() || cache.priority(key) > 0) return
        if (title.isNotBlank() && artist.isNotBlank()) try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val results = JSONObject(String(read("https://itunes.apple.com/search?country=tr&entity=song&limit=10&term=$query", max = 128000), Charsets.UTF_8)).optJSONArray("results")
            if (results != null) for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (!matches(item, title, artist, album)) continue
                val address = item.optString("artworkUrl100").replace("100x100", "384x384")
                val host = try { URL(address).host } catch (_: Exception) { "" }
                if (address.startsWith("https://") && host.endsWith(".mzstatic.com") && active()) {
                    val bytes = read(address)
                    if (active()) cache.save(key, bytes, 1)
                    break
                }
            }
        } catch (_: Exception) { failed = true }
        cache.retryAfter(key, if (failed) 10 * 60 * 1000L else 24 * 60 * 60 * 1000L)
    }
    companion object {
        fun rankName(name: String): Int = when (name.substringBeforeLast('.').lowercase(java.util.Locale.ROOT)) {
            "cover" -> 0; "folder" -> 1; "front" -> 2; "album", "artwork" -> 3; else -> 100
        }
        fun matches(item: JSONObject, title: String, artist: String, album: String): Boolean {
            val wanted = DriveArchiveIndex.requested(title, artist)
            val sameArtist = DriveArchiveIndex.normalize(wanted.artist) == DriveArchiveIndex.normalize(item.optString("artistName"))
            val sameTitle = DriveArchiveIndex.normalize(wanted.title) == DriveArchiveIndex.normalize(item.optString("trackName"))
            val sameAlbum = album.isNotBlank() && DriveArchiveIndex.normalize(album) == DriveArchiveIndex.normalize(item.optString("collectionName"))
            return sameArtist && (sameTitle || sameAlbum)
        }
    }
}
