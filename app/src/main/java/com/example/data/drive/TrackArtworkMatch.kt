package com.example.data.drive

import org.json.JSONObject

/** Ses sürümü işaretleri farklıysa kapak otomatik seçilmez. */
object TrackArtworkMatch {
    private val decoration = Regex("(?i)\\s*[\\[(](official (music )?video|official audio|resmi video|lyrics?)[\\])]\\s*$")
    private val versions = setOf("live", "canli", "konser", "akustik", "acoustic", "remix", "cover", "karaoke", "instrumental", "sped", "slowed", "edit", "version", "versiyon")

    fun matches(item: JSONObject, title: String, artist: String): Boolean {
        val wanted = DriveArchiveIndex.requested(title, artist)
        fun clean(value: String) = DriveArchiveIndex.normalize(value.replace(decoration, ""))
        val wantedArtist = clean(wanted.artist)
        val resultArtist = clean(item.optString("artistName"))
        val wantedTitle = clean(wanted.title)
        val resultTitle = clean(item.optString("trackName"))
        if (wantedArtist.isBlank() || wantedTitle.isBlank()) return false
        if (wantedArtist != resultArtist || wantedTitle != resultTitle) return false
        return wantedTitle.split(' ').filter { it in versions }.toSet() == resultTitle.split(' ').filter { it in versions }.toSet()
    }
}
