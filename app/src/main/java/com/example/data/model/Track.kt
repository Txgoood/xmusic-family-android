package com.example.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val sourceUrl: String,
    val durationMs: Long
)
