package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicListeningStatsTest {
    private lateinit var context: Context
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("xmusic_listening_genres", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun countsOnlyMusicAndKeepsAssignedGenreForDriveCopy() {
        val stats=MusicListeningStats(context)
        stats.assign("leTDrileoGI","Pop")
        stats.record("experimental:leTDrileoGI")
        stats.record("drive-source:leTDrileoGI")
        stats.record("podcast:one")
        stats.record("local:one")
        val summary=JSONObject(stats.summary())
        assertEquals(3,summary.getInt("total"))
        assertEquals("Pop",summary.getJSONArray("items").getJSONObject(0).getString("genre"))
        assertEquals(2,summary.getJSONArray("items").getJSONObject(0).getInt("count"))
    }
    @Test fun deletingProfileRemovesOnlyTheirCounts() {
        val stats=MusicListeningStats(context)
        stats.record("local:one")
        val profiles=ProfileStore(context)
        assertTrue(profiles.change("create","","Aile","",""))
        val id=JSONObject(profiles.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        assertTrue(profiles.change("select",id,"","",""))
        stats.record("local:two")
        assertEquals(2,JSONObject(stats.summary()).getInt("total"))
        stats.removeProfile(id)
        assertEquals(1,JSONObject(stats.summary()).getInt("total"))
    }
}
