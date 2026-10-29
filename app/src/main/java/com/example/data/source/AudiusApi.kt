package com.example.data.source

import com.example.data.model.Track
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Audius API Entegrasyonu
interface AudiusApi {
    @GET("v1/tracks/search")
    suspend fun searchTracks(@Query("query") query: String): Response<AudiusResponse>
}

data class AudiusResponse(
    val data: List<Track>
)
