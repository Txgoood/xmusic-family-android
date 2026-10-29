package com.example.data.source

import com.example.data.local.dao.ProfileDao
import com.example.data.model.Track

class XMusicRepository(
    private val profileDao: ProfileDao,
    private val audiusApi: AudiusApi
) {
    // Arama sonuçlarını gerçek Audius veri modelinden uygulama modeline dönüştür.
    suspend fun searchTracks(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return try {
            audiusApi.searchTracks(query.trim()).data.map { item ->
                Track(
                    id = item.id,
                    title = item.title,
                    artist = item.user?.name.orEmpty(),
                    album = "",
                    sourceUrl = "https://discoveryprovider.audius.co/v1/tracks/${item.id}/stream",
                    durationMs = item.duration * 1000
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Gerçek bulut yanıtı alınmadan kayıtları SYNCED olarak işaretleme.
    suspend fun syncPendingProfiles() {
        // Backend entegrasyonu tamamlanana kadar bekleyen kayıtlar korunur.
        profileDao.getPendingProfiles()
    }
}
