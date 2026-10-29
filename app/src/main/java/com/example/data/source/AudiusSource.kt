package com.example.data.source

import com.example.data.model.Track

class AudiusSource(private val api: AudiusApi = AudiusClient.api) : MusicSource {
    override val sourceName: String = "Audius"

    override suspend fun search(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return api.searchTracks(query.trim()).data
            .filter { it.id.isNotBlank() }
            .map { item ->
                Track(
                    id = item.id,
                    title = item.title,
                    artist = item.user?.name.orEmpty(),
                    album = "",
                    sourceUrl = "https://discoveryprovider.audius.co/v1/tracks/${item.id}/stream",
                    durationMs = item.duration * 1000
                )
            }
    }
}
