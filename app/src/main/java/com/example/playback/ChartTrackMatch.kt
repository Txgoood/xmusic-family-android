package com.example.playback

import java.text.Normalizer
import java.util.Locale

/** Yazım farklarını temizler, kaydın sürümünü değiştiren sözcük ve sayıları korur. */
internal object ChartTrackMatch {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace('ı', 'i').replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\b(official music video|official lyric video|official video|official audio|music video|resmi video|lyrics?|hd|hq|4k|1080p|720p)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun versions(value: String): Set<String> = normalize(value).split(' ').filter {
        it in setOf("remix", "mix", "live", "canli", "konser", "acoustic", "akustik", "cover", "instrumental", "karaoke", "sped", "slowed", "remaster", "remastered") || it.any(Char::isDigit)
    }.toSet()

    fun matches(title: String, artist: String, candidate: String, uploader: String = ""): Boolean {
        val song = normalize(title); val performer = normalize(artist); val actual = normalize(candidate)
        val body = actual.removePrefix("$performer ").removeSuffix(" $performer")
        if (song.isBlank() || performer.isBlank() || versions(title) != versions(body)) return false
        val compact: (String) -> String = { it.replace(" ", "") }
        // Sanatçı-başlık sırası, noktalama ve resmi video etiketi güvenle değişebilir.
        if (compact(actual) in setOf(compact("$performer $song"), compact("$song $performer"))) return true
        val channel = normalize(uploader).replace(Regex("\\b(official|vevo|topic)\\b"), " ").trim().replace(Regex("\\s+"), " ")
        return compact(actual) == compact(song) && compact(channel) == compact(performer)
    }
}
