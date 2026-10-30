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
class PlaybackResumeStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("xmusic_playback_resume", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun lastPositionSurvivesRecreationAndIsSeparateForProfiles() {
        PlaybackResumeStore(context).save("one", JSONObject().put("id","experimental:abcdefghijk").put("position",42000))
        PlaybackResumeStore(context).save("two", JSONObject().put("id","local:other").put("position",9000))
        assertEquals(42000L,JSONObject(PlaybackResumeStore(context).load("one")).getLong("position"))
        PlaybackResumeStore(context).clear("one")
        assertFalse(JSONObject(PlaybackResumeStore(context).load("one")).has("id"))
        assertEquals(9000L,JSONObject(PlaybackResumeStore(context).load("two")).getLong("position"))
    }
}
