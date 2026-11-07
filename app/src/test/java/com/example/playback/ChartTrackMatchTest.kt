package com.example.playback

import org.junit.Assert.*
import org.junit.Test

class ChartTrackMatchTest {
    @Test fun acceptsOrderingPunctuationAndPublisherLabels() {
        assertTrue(ChartTrackMatch.matches("Dudu", "Tarkan", "TARKAN – Dudu (Official Music Video)"))
        assertTrue(ChartTrackMatch.matches("Üzüleceksin 2.0", "SNOW", "Üzüleceksin 2.0 - SNOW [Official Audio]"))
        assertTrue(ChartTrackMatch.matches("Kayıp Kalp", "BLOK3", "BLOK3 - Kayıp Kalp (Official Video)"))
        assertTrue(ChartTrackMatch.matches("Dudu", "Tarkan", "Dudu", "Tarkan - Topic"))
    }
    @Test fun neverChangesVersionOrArtistToGetAPlayableResult() {
        assertFalse(ChartTrackMatch.matches("PERDE 2.0", "SNOW", "SNOW - PERDE"))
        assertFalse(ChartTrackMatch.matches("PERDE", "SNOW", "SNOW - PERDE 2.0"))
        assertFalse(ChartTrackMatch.matches("Dudu", "Tarkan", "Tarkan - Dudu Remix"))
        assertFalse(ChartTrackMatch.matches("Dudu", "Tarkan", "Başka Sanatçı - Dudu"))
        assertFalse(ChartTrackMatch.matches("Dudu", "Tarkan", "Dudu", "Başka Sanatçı"))
        assertFalse(ChartTrackMatch.matches("Dudu", "Tarkan", "Tarkan - Dudu (Live)"))
    }
}
