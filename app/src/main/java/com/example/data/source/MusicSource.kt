package com.example.data.source

import com.example.data.model.Track

interface MusicSource {
    suspend fun search(query: String): List<Track>
    val sourceName: String
}
