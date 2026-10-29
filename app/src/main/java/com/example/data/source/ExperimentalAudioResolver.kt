package com.example.data.source

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.URI

/** Geçici akış adreslerini yalnızca oynatma anında çözer; kalıcı bağlantı olarak saklanmaz. */
class ExperimentalAudioResolver(private val context: Context) {
    data class Stream(val title: String, val url: String, val headers: Map<String, String>)

    fun resolve(sourceUrl: String): Stream {
        val uri = URI(sourceUrl)
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) {
            "Desteklenmeyen kaynak bağlantısı"
        }
        YoutubeDL.getInstance().init(context.applicationContext)
        val request = YoutubeDLRequest(sourceUrl)
        request.addOption("--no-playlist")
        request.addOption("-f", "bestaudio/best")
        val info = YoutubeDL.getInstance().getInfo(request)
        val url = requireNotNull(info.url) { "Ses akışı bulunamadı" }
        require(URI(url).scheme == "https") { "Güvensiz akış adresi" }
        val allowed = setOf("user-agent", "accept", "accept-language", "referer", "origin")
        val headers = info.httpHeaders.orEmpty().filterKeys { it.lowercase() in allowed }
            .filterValues { !it.contains('\n') && !it.contains('\r') }
        return Stream(info.title ?: "İsimsiz", url, headers)
    }
}
