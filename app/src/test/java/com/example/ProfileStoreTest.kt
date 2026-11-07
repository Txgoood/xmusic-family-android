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
class ProfileStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("xmusic_family_sync", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun pinIsRequiredForProtectedProfileAndSurvivesRecreation() {
        val store = ProfileStore(context)
        assertTrue(store.change("create", "", "Eşim", "", "9876"))
        val state = JSONObject(store.publicState())
        val id = state.getJSONArray("profiles").getJSONObject(1).getString("id")
        assertFalse(store.change("select", id, "", "0000", ""))
        assertEquals("default", store.activeId())
        assertTrue(ProfileStore(context).change("select", id, "", "9876", ""))
        assertEquals(id, ProfileStore(context).activeId())
        assertFalse(context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE).getString("profiles", "")!!.contains("9876"))
    }
    @Test fun activeAndLastProfileCannotBeDeletedAndInvalidPinCannotBeSaved() {
        val store = ProfileStore(context)
        assertFalse(store.change("delete", "default", "", "", ""))
        assertFalse(store.change("create", "", "Test", "", "abc"))
        assertTrue(store.change("create", "", "Şifresiz", "", ""))
        val id = JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        assertTrue(store.change("select", id, "", "", ""))
        assertFalse(store.change("delete", id, "", "", ""))
        assertFalse(store.change("delete", "default", "", "", ""))
        assertEquals(2, JSONObject(store.publicState()).getJSONArray("profiles").length())
    }
    @Test fun fiveWrongPinsTemporarilyLockTheProfile() {
        val store=ProfileStore(context)
        assertTrue(store.change("create", "", "Kilitli", "", "2468"))
        val id=JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        repeat(5){ assertFalse(store.change("select",id,"","0000","")) }
        assertFalse(store.change("select",id,"","2468",""))
    }
    private fun child(store: ProfileStore): String {
        assertTrue(store.setParentPin("", "1357"))
        assertTrue(store.saveProfile("", "Çocuk", "child", "", "", "1357", 1, false))
        return JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
    }
    @Test fun childCannotEscalateOrExitWithoutParentPinAndPolicySurvivesRestart() {
        val store = ProfileStore(context); val id = child(store)
        assertTrue(store.change("select", id, "", "", ""))
        assertTrue(ProfileStore(context).isChild())
        assertFalse(store.change("create", "", "Sahte Yönetici", "", ""))
        assertFalse(store.saveProfile(id, "Çocuk", "admin", "", "", "1357", 60, true))
        assertFalse(store.setParentPin("1357", "9999"))
        assertFalse(store.change("select", "default", "", "", ""))
        assertTrue(store.change("select", "default", "", "1357", ""))
        assertTrue(store.isAdmin())
    }
    @Test fun unapprovedContentIsDeniedAndDailyLimitCannotBeBypassedByRestart() {
        val store = ProfileStore(context); val id = child(store)
        val track = "{\"id\":\"experimental:abcdefghijk\",\"title\":\"Onaylı Kayıt\"}"
        assertFalse(store.approve(id, track, false, "0000"))
        assertTrue(store.approve(id, track, false, "1357"))
        assertTrue(store.change("select", id, "", "", ""))
        assertNull(store.playbackBlockReason("drive-source:abcdefghijk"))
        assertNotNull(store.playbackBlockReason("experimental:xxxxxxxxxxx"))
        assertFalse(store.approve(id, track, false, "1357"))
        repeat(30) { store.recordListening(id, 2000) }
        assertEquals("Günlük dinleme süresi doldu", ProfileStore(context).playbackBlockReason("experimental:abcdefghijk"))
        assertEquals(60000L, ProfileStore(context).usedMillis())
    }
    @Test fun lastAdministratorAndParentPinAreRequiredForChildSetup() {
        val store = ProfileStore(context)
        assertEquals("admin", store.role())
        assertFalse(store.saveProfile("", "Çocuk", "child", "", "", "", 60, false))
        assertTrue(store.setParentPin("", "2468"))
        assertFalse(store.saveProfile("default", "Ben", "adult", "", "", "2468", 60, true))
        assertFalse(store.setParentPin("0000", "5555"))
        val raw = context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains("2468"))
    }
    @Test fun familyMixRequiresExplicitAdultOptInAndAdultCannotManageOthers() {
        val store = ProfileStore(context)
        assertTrue(store.mixProfileIds().isEmpty())
        assertTrue(store.saveProfile("", "Eşim", "adult", "", "", "", 60, true))
        val id = JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        assertEquals(listOf(id), store.mixProfileIds())
        assertTrue(store.change("select", id, "", "", ""))
        assertFalse(store.change("create", "", "Yeni", "", ""))
        assertFalse(store.change("delete", "default", "", "", ""))
        assertFalse(store.saveProfile(id, "Eşim", "admin", "", "", "", 60, true))
        assertTrue(store.saveProfile(id, "Eşim", "adult", "", "", "", 60, false))
        assertTrue(store.mixProfileIds().isEmpty())
    }
    @Test fun parentPinRateLimitAppliesAcrossProfiles() {
        val store = ProfileStore(context); val id = child(store)
        assertTrue(store.change("select", id, "", "", ""))
        repeat(5) { assertFalse(store.change("select", "default", "", "0000", "")) }
        assertFalse(ProfileStore(context).change("select", "default", "", "1357", ""))
    }

    @Test fun reviewQueueDeduplicatesPersistsAndNeverAutomaticallyAllowsUnknownTracks() {
        val store = ProfileStore(context); val id = child(store)
        val raw = """{"id":"experimental:review12345","title":"Test (Explicit)"}"""
        assertTrue(store.requestReview(id, raw))
        assertTrue(store.requestReview(id, raw))
        val review = JSONObject(ProfileStore(context).childReview(id))
        assertEquals(1, review.getJSONArray("pending").length())
        assertTrue(review.getJSONArray("pending").getJSONObject(0).getString("reason").contains("yetişkin"))
        assertTrue(store.setChildAge(id, "under7", "1357"))
        assertFalse(store.setChildAge(id, "invalid", "1357"))
        assertTrue(store.change("select", id, "", "", ""))
        assertNotNull(store.playbackBlockReason("experimental:review12345"))
        assertFalse(store.resolveReview(id, "experimental:review12345", true, "1357"))
        assertFalse(store.setChildAge(id, "teen", "1357"))
        assertTrue(store.requestReview(id, """{"title":"Bir şarkı isteği"}"""))
        assertTrue(store.change("select", "default", "", "1357", ""))
        assertFalse(store.resolveReview(id, "experimental:review12345", false, "1357"))
        assertTrue(store.resolveReview(id, "experimental:review12345", true, "1357"))
        assertTrue(store.requestReview(id, raw))
        assertEquals(1, JSONObject(store.childReview(id)).getJSONArray("pending").length())
        assertEquals(1, JSONObject(store.childReview(id)).getJSONArray("denied").length())
        assertTrue(store.approve(id, raw, false, "1357"))
        assertEquals(0, JSONObject(store.childReview(id)).getJSONArray("denied").length())
        assertTrue(store.change("select", id, "", "", ""))
        assertNull(store.playbackBlockReason("experimental:review12345"))
    }

}

