package com.example.data.source

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.URI

/** Geçici akış adreslerini yalnızca oynatma anında çözer; kalıcı bağlantı olarak saklanmaz. */
class ExperimentalAudioResolver(private val context: Context) {
    companion object {
        private val updateLock = Any()
        private data class CachedStream(val stream: Stream, val createdAt: Long)
        private val streams = LinkedHashMap<String, CachedStream>()
        @Synchronized
        fun invalidate(sourceUrl: String) { streams.remove(sourceUrl) }
        @Synchronized
        private fun cached(sourceUrl: String): Stream? {
            val entry = streams[sourceUrl] ?: return null
            if (android.os.SystemClock.elapsedRealtime() - entry.createdAt >= 90_000L) {
                streams.remove(sourceUrl)
                return null
            }
            return entry.stream
        }
        @Synchronized
        private fun remember(sourceUrl: String, stream: Stream) {
            if (streams.size >= 32) streams.remove(streams.keys.first())
            streams[sourceUrl] = CachedStream(stream, android.os.SystemClock.elapsedRealtime())
        }
    }
    data class Stream(val title: String, val url: String, val headers: Map<String, String>)

    fun resolve(sourceUrl: String): Stream {
        val uri = URI(sourceUrl)
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) {
            "Desteklenmeyen kaynak bağlantısı"
        }
        cached(sourceUrl)?.let { return it }
        val engine = YoutubeDL.getInstance()
        engine.init(context.applicationContext)
        var updateStatus = "güncelleme kontrol edilmedi"
        synchronized(updateLock) {
            val prefs = context.getSharedPreferences("xmusic_source_engine", Context.MODE_PRIVATE)
            val last = prefs.getLong("lastSuccessfulUpdate", 0L)
            if (System.currentTimeMillis() - last > 24 * 60 * 60 * 1000L) {
                try {
                    engine.updateYoutubeDL(context.applicationContext)
                    prefs.edit().putLong("lastSuccessfulUpdate", System.currentTimeMillis()).apply()
                    updateStatus = "güncel"
                } catch (_: Exception) {
                    // Güncelleme erişimi yoksa kurulu motorla devam et.
                    updateStatus = "güncelleme alınamadı"
                }
            } else updateStatus = "güncel"
        }
        val failures = mutableListOf<String>()
        for (hls in listOf(true, false)) {
            try {
                val request = YoutubeDLRequest(sourceUrl)
                request.addOption("--no-playlist")
                request.addOption("--socket-timeout", "15")
                request.addOption("--retries", "1")
                if (hls) {
                    request.addOption("--extractor-args", "youtube:player_client=web_safari")
                    request.addOption("-f", "bestaudio[protocol^=m3u8]/best[protocol^=m3u8]")
                } else {
                    // HLS elde edilemezse varsayılan istemciyi ayrıca dene.
                    request.addOption("-f", "bestaudio/best")
                }
                val info = engine.getInfo(request)
                val url = requireNotNull(info.url) { "Ses akışı bulunamadı" }
                require(URI(url).scheme == "https") { "Güvensiz akış adresi" }
                val allowed = setOf("user-agent", "accept", "accept-language", "referer", "origin")
                val headers = info.httpHeaders.orEmpty().filterKeys { it.lowercase() in allowed }
                    .filterValues { !it.contains('\n') && !it.contains('\r') }
                val stream = Stream(info.title ?: "İsimsiz", url, headers)
                remember(sourceUrl, stream)
                return stream
            } catch (e: Exception) {
                // Akış URL'leri ve sorgu parametrelerini kullanıcı raporuna taşıma.
                val detail = (e.message ?: e.javaClass.simpleName)
                    .replace(Regex("https?://\\S+"), "[bağlantı]")
                    .replace(Regex("\\u001B\\[[0-9;]*m"), "")
                    .take(700)
                failures.add((if (hls) "HLS" else "Varsayılan") + ": " + detail)
            }
        }
        throw java.io.IOException("Motor: " + engine.version(context.applicationContext).orEmpty() +
            " (" + updateStatus + ")\n\n" + failures.joinToString("\n\n"))
    }
}
