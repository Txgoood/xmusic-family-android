package com.example.data.source

import com.example.data.model.Track

class AudiusSource : MusicSource {
    override val sourceName: String = "Audius"

    override suspend fun search(query: String): List<Track> {
        // Audius API çağrısı buraya gelecek (Retrofit ile)
        // Şimdilik test için boş liste veya sahte veri
        return emptyList()
    }
}
