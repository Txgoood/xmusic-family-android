package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilySyncStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        for (name in listOf("xmusic_profiles", "xmusic_family_sync", "xmusic_library_default", "xmusic_library")) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun encryptedRulesRequireTheFounderSignatureEvenWithTheFamilyKey() {
        val profiles = ProfileStore(context); assertTrue(profiles.setParentPin("", "1357"))
        val store = FamilySyncStore(context); store.prepare("folder123", "account123"); store.finishCreate("root123")
        val policy = profiles.policy(store.familyId(), 1); val signed = store.signedPolicy(policy)
        val encrypted = store.seal(signed)
        assertFalse(encrypted.contains("parentHash")); assertFalse(encrypted.contains("\"Ben\""))
        assertEquals(policy.toString(), store.verifyPolicy(store.open(encrypted)).toString())
        val forged = JSONObject(signed.toString()); val payload = JSONObject(forged.getString("payload"))
        payload.getJSONArray("profiles").getJSONObject(0).put("name", "Değiştirilmiş")
        forged.put("payload", payload.toString())
        assertThrows(Exception::class.java) { store.verifyPolicy(store.open(store.seal(forged))) }
        val envelope = JSONObject(encrypted); envelope.put("data", envelope.getString("data").reversed())
        assertThrows(Exception::class.java) { store.open(envelope.toString()) }
        store.revision(2)
        assertThrows(Exception::class.java) { store.verifyPolicy(signed) }
    }
    @Test fun joiningPreservesLibraryAndChildRulesAndCannotPromoteLocalUsers() {
        val profiles = ProfileStore(context)
        assertTrue(profiles.setParentPin("", "1357"))
        assertTrue(profiles.saveProfile("", "Fatma", "adult", "", "", "1357", 60, false))
        assertTrue(profiles.saveProfile("", "Çocuk", "child", "", "", "1357", 30, false))
        profiles.makeGlobalIds()
        val store = FamilySyncStore(context); store.prepare("folder123", "account123"); store.finishCreate("root123")
        val signed = store.signedPolicy(profiles.policy(store.familyId(), 1)); val invitation = store.parseInvitation(store.invitation())
        val policy = store.verifyPolicy(signed); val rows = policy.getJSONArray("profiles")
        val spouse = rows.getJSONObject(1).getString("id"); val kid = rows.getJSONObject(2).getString("id")
        // İkinci cihazın ilk yerel profili ve kitaplığı taklit edilir.
        context.getSharedPreferences("xmusic_family_sync", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("xmusic_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        val library = """{"favorites":[{"id":"experimental:abcdefghijk","title":"Yerel favori"}]}"""
        context.getSharedPreferences("xmusic_library_default", Context.MODE_PRIVATE).edit().putString("data", library).commit()
        store.accept(invitation, "spouseAccount")
        profiles.applyPolicy(policy, spouse); store.revision(1)
        assertEquals(library, context.getSharedPreferences("xmusic_library_$spouse", Context.MODE_PRIVATE).getString("data", ""))
        assertEquals(library, context.getSharedPreferences("xmusic_library_default", Context.MODE_PRIVATE).getString("data", ""))
        assertFalse(store.owner()); assertFalse(profiles.saveProfile(spouse, "Fatma", "admin", "", "", "1357", 60, true))
        assertTrue(profiles.change("select", kid, "", "", ""))
        assertNotNull(profiles.playbackBlockReason("experimental:abcdefghijk"))
        assertFalse(profiles.change("select", spouse, "", "", ""))
        assertTrue(profiles.change("select", spouse, "", "1357", ""))
        assertFalse(profiles.setParentPin("1357", "2468"))
    }
    @Test fun childRequestsMergeOnceAndRemoteUsageIsAddedWithoutDoubleCounting() {
        val profiles = ProfileStore(context); assertTrue(profiles.setParentPin("", "1357"))
        assertTrue(profiles.saveProfile("", "Çocuk", "child", "", "", "1357", 1, false))
        val kid = JSONObject(profiles.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        val store = FamilySyncStore(context)
        val request = JSONObject().put("id", "experimental:abcdefghijk").put("title", "Test").put("requestKey", "experimental:abcdefghijk").put("reason", "Onay gerekli")
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
        val own = profiles.deviceSnapshot("test", store.deviceId())
        val remote = JSONObject().put("device", "other-device").put("day", day).put("usage", JSONObject().put(kid, 60000))
            .put("requests", JSONObject().put(kid, JSONArray().put(request)))
        val devices = JSONArray().put(own).put(remote)
        store.cache(devices); profiles.mergeRemoteRequests(devices); profiles.mergeRemoteRequests(devices)
        assertEquals(1, JSONObject(profiles.childReview(kid)).getJSONArray("pending").length())
        assertEquals(60000L, profiles.usedMillis(kid)); assertEquals(0L, profiles.localUsedMillis(kid))
        assertTrue(profiles.approve(kid, request.toString(), false, "1357"))
        profiles.mergeRemoteRequests(devices)
        assertEquals(0, JSONObject(profiles.childReview(kid)).getJSONArray("pending").length())
        assertTrue(profiles.change("select", kid, "", "", ""))
        assertEquals("Günlük dinleme süresi doldu", profiles.playbackBlockReason("experimental:abcdefghijk"))
    }
    @Test fun remoteMixUsesOnlyCanonicalAdultsAndPreservesConsent() {
        val profiles = ProfileStore(context)
        assertTrue(profiles.saveProfile("", "Fatma", "adult", "", "", "", 60, false))
        val spouse = JSONObject(profiles.publicState()).getJSONArray("profiles").getJSONObject(1).getString("id")
        val store = FamilySyncStore(context)
        val track = JSONObject().put("id", "experimental:abcdefghijk").put("title", "Ortak favori")
        val rows = JSONArray().put(JSONObject().put("id", spouse).put("favorites", JSONArray().put(track)))
            .put(JSONObject().put("id", "unknown-person").put("favorites", JSONArray().put(track)))
        store.cache(JSONArray().put(JSONObject().put("device", "spouse-phone").put("profiles", rows)))
        assertEquals(1, store.remoteMix("favorites").length())
        assertTrue(profiles.mixProfileIds().isEmpty())
        store.setSharing(spouse, true); assertEquals(listOf(spouse), profiles.mixProfileIds())
        store.setSharing(spouse, false); assertTrue(profiles.mixProfileIds().isEmpty())
    }
}
