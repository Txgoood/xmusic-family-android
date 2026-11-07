package com.example.data.source

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** Türkiye sıralamalarını kaynak sırasıyla saklar ve yalnızca yedi günde bir yeniler. */
internal class TurkeyMusicCharts(context: Context) {
    private val prefs = context.getSharedPreferences("xmusic_turkey_charts", Context.MODE_PRIVATE)

    fun cached(key: String): JSONObject? =
        prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun refresh(key: String) = synchronized(lock) {
        val source = sources[key] ?: return@synchronized null
        val previous = cached(key)
        val age = System.currentTimeMillis() - (previous?.optLong("checkedAt") ?: 0L)
        if (age in 0 until week) return@synchronized previous
        if (System.currentTimeMillis() - prefs.getLong("attempt:$key", 0L) in 0 until retryDelay) {
            return@synchronized previous
        }
        prefs.edit().putLong("attempt:$key", System.currentTimeMillis()).apply()
        runCatching {
            val connection = URL("https://kworb.net/${source.path}").openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            val html = try {
                check(connection.responseCode == 200)
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val heading = headingPattern.find(html)?.groupValues?.get(1).orEmpty()
            check(decode(heading).contains(source.heading))
            val table = html.substringAfter("<tbody>", "").substringBefore("</tbody>")
            val tracks = JSONArray()
            for (row in rowPattern.findAll(table).take(50)) {
                val cells = cellPattern.findAll(row.groupValues[1]).take(3).map { decode(it.groupValues[1]) }.toList()
                check(cells.size == 3 && cells[0].toIntOrNull() == tracks.length() + 1)
                val track = cells[2].split(" - ", limit = 2)
                check(track.size == 2 && track.all { it.isNotBlank() })
                tracks.put(JSONObject().put("rank", cells[0].toInt())
                    .put("artist", track[0].take(180)).put("title", track[1].take(180)))
            }
            check(tracks.length() == 50)
            val result = JSONObject().put("items", tracks).put("checkedAt", System.currentTimeMillis())
            datePattern.find(heading)?.let { result.put("date", it.value.replace('/', '-')) }
            prefs.edit().putString(key, result.toString()).apply()
            result
        }.getOrElse { previous }
    }

    private fun decode(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private data class Source(val path: String, val heading: String)

    companion object {
        private val lock = Any()
        private const val week = 7L * 24 * 60 * 60 * 1000
        private const val retryDelay = 60L * 60 * 1000
        private val sources = mapOf(
            "spotify" to Source("spotify/country/tr_daily.html", "Spotify Daily Chart - Turkey"),
            "apple" to Source("charts/apple_s/tr.html", "Turkey Apple Music Top Songs"),
            "youtube" to Source("youtube/insights/tr.html", "YouTube Turkey - Week ending"),
            "shazam" to Source("charts/shazam/tr.html", "Shazam Top Songs - Turkey")
        )
        private val headingPattern = Regex("""<span class="pagetitle">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        private val rowPattern = Regex("""<tr\b[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        private val cellPattern = Regex("""<td\b[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
        private val datePattern = Regex("""\d{4}/\d{2}/\d{2}""")
    }
}
