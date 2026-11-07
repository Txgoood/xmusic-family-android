package com.example

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Arama kataloğu sadece program bilgisini bulur; ses yayıncının RSS kaynağından gelir. */
class PodcastCatalogClient {
    private val cache = LinkedHashMap<String, Pair<Long, String>>()

    fun search(query: String): JSONObject {
        require(query.trim().length in 2..100)
        val term = query.trim()
        synchronized(cache) {
            cache[term]?.let { if (System.currentTimeMillis() - it.first < 300_000L) return JSONObject(it.second) }
        }
        val endpoint = URL("https://itunes.apple.com/search?country=TR&media=podcast&entity=podcast&limit=30&term=" +
            URLEncoder.encode(term, "UTF-8"))
        val conn = endpoint.openConnection() as HttpURLConnection
        val result = try {
            conn.connectTimeout = 9000
            conn.readTimeout = 11000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "XMusicFamily/0.3")
            check(conn.responseCode in 200..299) { "Podcast kataloğu yanıt vermedi." }
            val raw = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(out.size() + size <= 2_000_000)
                    out.write(buffer, 0, size)
                }
                out.toString("UTF-8")
            }
            parse(raw)
        } finally { conn.disconnect() }
        synchronized(cache) {
            if (cache.size >= 24) cache.remove(cache.keys.first())
            cache[term] = Pair(System.currentTimeMillis(), result.toString())
        }
        return result
    }

    companion object {
        private fun https(value: String): Boolean = try {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        } catch (_: Exception) { false }

        fun parse(raw: String): JSONObject {
            val entries = JSONObject(raw).optJSONArray("results") ?: JSONArray()
            val rows = JSONArray()
            val seen = HashSet<String>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val feed = item.optString("feedUrl")
                if (item.optString("kind") != "podcast" || !https(feed) || !seen.add(feed)) continue
                val artwork = item.optString("artworkUrl600", item.optString("artworkUrl100"))
                val page = item.optString("collectionViewUrl")
                rows.put(JSONObject().put("feed", feed)
                    .put("title", item.optString("collectionName", item.optString("trackName", "Podcast")).take(300))
                    .put("artist", item.optString("artistName").take(300))
                    .put("artwork", if (https(artwork)) artwork else "")
                    .put("url", if (https(page) && URI(page).host.equals("podcasts.apple.com", true)) page else "")
                    .put("genre", item.optString("primaryGenreName"))
                    .put("episodeCount", item.optInt("trackCount", 0)))
                if (rows.length() >= 30) break
            }
            return JSONObject().put("items", rows)
        }
    }
}
