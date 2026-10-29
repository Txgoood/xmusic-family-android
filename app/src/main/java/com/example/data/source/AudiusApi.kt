package com.example.data.source

import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApi {
    @GET("v1/tracks/search")
    suspend fun searchTracks(@Query("query") query: String): AudiusResponse
}

data class AudiusResponse(val data: List<AudiusTrack> = emptyList())
data class AudiusTrack(
    val id: String = "",
    val title: String = "",
    val duration: Long = 0,
    val user: AudiusUser? = null
)
data class AudiusUser(val name: String = "")
