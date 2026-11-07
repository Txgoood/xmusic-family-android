package com.example

import com.example.data.drive.TrackArtworkMatch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackArtworkMatchTest {
    private fun item(title: String, artist: String) = JSONObject().put("trackName", title).put("artistName", artist)
    @Test fun acceptsAccentsAndOfficialVideoLabel() {
        assertTrue(TrackArtworkMatch.matches(item("Yalnız", "MFÖ"), "Yalnız (Official Video)", "MFO"))
    }
    @Test fun rejectsDifferentArtistsAndVersions() {
        assertFalse(TrackArtworkMatch.matches(item("Yalnız", "Başka Sanatçı"), "Yalnız", "MFÖ"))
        assertFalse(TrackArtworkMatch.matches(item("Yalnız - Remix", "MFÖ"), "Yalnız", "MFÖ"))
        assertFalse(TrackArtworkMatch.matches(item("Yalnız", "MFÖ"), "Yalnız (Live)", "MFÖ"))
    }
}
