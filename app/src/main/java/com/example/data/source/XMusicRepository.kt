package com.example.data.source

import com.example.data.local.dao.ProfileDao
import com.example.data.model.Track
import com.example.data.local.entities.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class XMusicRepository(
    private val profileDao: ProfileDao,
    private val audiusApi: AudiusApi
) {
    // Arama: API'den al
    suspend fun searchTracks(query: String): List<Track> {
        return try {
            val response = audiusApi.searchTracks(query)
            if (response.isSuccessful) response.body()?.data ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Yerel veriyi buluta senkronize et
    suspend fun syncPendingProfiles() {
        val pendingProfiles = profileDao.getPendingProfiles()
        pendingProfiles.forEach { profile ->
            // Burada Backend API'sine POST isteği yapılacak
            // Başarılı olursa:
            profileDao.insertProfile(profile.copy(syncStatus = SyncStatus.SYNCED))
        }
    }
}
