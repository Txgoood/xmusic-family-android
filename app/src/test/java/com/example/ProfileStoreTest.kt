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
        assertTrue(store.change("delete", "default", "", "", ""))
        assertEquals(1, JSONObject(store.publicState()).getJSONArray("profiles").length())
    }
    @Test fun fiveWrongPinsTemporarilyLockTheProfile() {
        val store=ProfileStore(context)
        assertTrue(store.change("create", "", "Kilitli", "", "2468"))
        val id=JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        repeat(5){ assertFalse(store.change("select",id,"","0000","")) }
        assertFalse(store.change("select",id,"","2468",""))
    }
}
