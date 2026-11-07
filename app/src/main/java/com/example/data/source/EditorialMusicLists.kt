package com.example.data.source

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Herkese açık seçki sayfalarından yalnız parça bilgilerini okur. */
internal class EditorialMusicLists(context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_editorial_lists", Context.MODE_PRIVATE)

    fun load(key: String): JSONObject {
        val id = playlists[key] ?: return JSONObject()
        val cached = runCatching { JSONObject(prefs.getString(key, null) ?: "{}") }.getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        if (now - cached.optLong("checkedAt") in 0 until 604800000L) return cached

        return runCatching {
            val connection = URL("https://music.apple.com/tr/playlist/$id").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            val page = try {
                check(connection.responseCode == 200)
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally { connection.disconnect() }
            val payload = page.substringAfter("id=\"serialized-server-data\">", "").substringBefore("</script>")
            val sections = JSONObject(payload).getJSONArray("data").getJSONObject(0)
                .getJSONObject("data").getJSONArray("sections")
            val tracks = JSONArray()
            val seen = HashSet<String>()
            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)
                if (section.optString("itemKind") != "trackLockup") continue
                val items = section.getJSONArray("items")
                for (index in 0 until items.length()) {
                    if (tracks.length() == 50) break
                    val item = items.optJSONObject(index) ?: continue
                    val descriptor = item.optJSONObject("contentDescriptor") ?: continue
                    val trackId = descriptor.optJSONObject("identifiers")?.optString("storeAdamID").orEmpty()
                    val title = item.optString("title").trim()
                    val artist = item.optString("artistName").trim()
                    if (descriptor.optString("kind") != "song" || trackId.isBlank() || title.isBlank() ||
                        artist.isBlank() || !seen.add(trackId)) continue
                    val artwork = item.optJSONObject("artwork")?.optJSONObject("dictionary")?.optString("url")
                        .orEmpty().replace("{w}", "240").replace("{h}", "240").replace("{f}", "jpg")
                    tracks.put(JSONObject().put("title", title.take(180)).put("artist", artist.take(180))
                        .put("artwork", artwork).put("duration", item.optLong("duration"))
                        .put("catalogId", trackId))
                }
            }
            check(tracks.length() > 0)
            if (key == "new") runCatching { addReleaseDates(tracks) }
            JSONObject().put("items", tracks).put("checkedAt", now).also {
                prefs.edit().putString(key, it.toString()).apply()
            }
        }.getOrElse { cached.put("stale", true) }
    }

    private fun addReleaseDates(tracks: JSONArray) {
        val ids = (0 until tracks.length()).map { tracks.getJSONObject(it).getString("catalogId") }
        if (ids.any { !it.matches(Regex("[0-9]{1,16}")) }) return
        val connection = URL("https://itunes.apple.com/lookup?country=TR&id=" + ids.joinToString(","))
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        val results = try {
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                .getJSONArray("results")
        } finally { connection.disconnect() }
        val dates = (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val date = item.optString("releaseDate").take(10)
            if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return@mapNotNull null
            item.optString("trackId") to date
        }.toMap()
        for ((index, id) in ids.withIndex()) {
            dates[id]?.let { tracks.getJSONObject(index).put("releaseDate", it) }
        }
    }

    companion object {
        private val playlists = mapOf(
            "pop" to "pl.59ef70f12c4f427db5fb5383acab5f29",
            "rock" to "pl.a8bd5a973f904853a82973a1a01cadf2",
            "rap" to "pl.1ef459abf3b541488095d2c5518ec617",
            "electronic" to "pl.0f2b91b39c4347829d976cb90d0bd4c8",
            "jazz" to "pl.7dccdd6c06c24d8c82dffd623c579622",
            "classical" to "pl.211e4d2b8853476dacc00652fc648e1b",
            "acoustic" to "pl.90a8ad26100b4e3995f3d14bdaaec26c",
            "sufi" to "pl.a6137a599a1d4f8fb37f6aed6313b77c",
            "new" to "pl.2b0e6e332fdf4b7a91164da3162127b5",
            "kpop" to "pl.48229b41bbfc47d7af39dae8e8b5276e",
            "fitness" to "pl.1cdff292963c489890c4fdc0e5e407b7",
            "party" to "pl.d838905f50af4200a2ebbc614922dee9"
        )
    }
}
