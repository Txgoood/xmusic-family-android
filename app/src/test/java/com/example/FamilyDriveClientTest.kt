package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** İki telefon ayrı Android tercihleriyle, aynı sahte Drive üzerinde çalıştırılır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyDriveClientTest {
    @Test fun twoPhonesExchangeMixesChildRequestsAndSignedApprovalsWithoutOverwritingData() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val files = linkedMapOf<String, String>(); val kinds = linkedMapOf<String, String>()
        val client = object : FamilyDriveClient(context) {
            override fun request(url: String, token: String, method: String, body: String?, type: String): String {
                if (method == "POST") {
                    val parts = body!!.split("\r\n")
                    val metadata = JSONObject(parts.first { it.startsWith("{\"name\"") })
                    val data = parts.last { it.startsWith("{") }
                    val id = "file" + (files.size + 1); files[id] = data; kinds[id] = metadata.getJSONObject("appProperties").getString("xmusicFamilyKind")
                    return JSONObject().put("id", id).toString()
                }
                if (url.contains("/files?q=")) return JSONObject().put("files", JSONArray().also { rows -> kinds.filterValues { it == "device" }.keys.forEach { rows.put(JSONObject().put("id", it)) } }).toString()
                val id = url.substringAfter("/files/").substringBefore('?')
                if (method == "PATCH") { require(files.containsKey(id)); files[id] = body!!; return JSONObject().put("id", id).toString() }
                return files[id] ?: error("not found")
            }
        }
        val names = mutableSetOf("xmusic_profiles", "xmusic_family_sync", "xmusic_library_default", "xmusic_library", "xmusic_profile_avatars", "xmusic_playback_resume")
        fun capture(): Map<String, Map<String, *>> = names.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        fun restore(state: Map<String, Map<String, *>>) {
            for (name in names) {
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                state[name]?.forEach { (key, value) -> when (value) { is String -> edit.putString(key, value); is Long -> edit.putLong(key, value); is Int -> edit.putInt(key, value); is Boolean -> edit.putBoolean(key, value) } }
                edit.commit()
            }
        }
        restore(emptyMap())
        val profiles = ProfileStore(context); val family = FamilySyncStore(context)
        assertTrue(profiles.setParentPin("", "1357"))
        assertTrue(profiles.saveProfile("", "Fatma", "adult", "", "", "1357", 60, false))
        assertTrue(profiles.saveProfile("", "Çocuk", "child", "", "", "1357", 30, false))
        client.createFamily("owner-token", "folder123", "owner-account")
        val members = JSONObject(profiles.publicState()).getJSONArray("profiles")
        val owner = members.getJSONObject(0).getString("id"); val spouse = members.getJSONObject(1).getString("id"); val kid = members.getJSONObject(2).getString("id")
        names.addAll(listOf("xmusic_library_$owner", "xmusic_library_$spouse", "xmusic_library_$kid"))
        family.setSharing(owner, true)
        context.getSharedPreferences("xmusic_library_$owner", Context.MODE_PRIVATE).edit().putString("data", """{"favorites":[{"id":"experimental:aaaaaaaaaaa","title":"Tolga favorisi"}]}""").commit()
        client.sync("owner-token")
        val invitation = family.parseInvitation(family.invitation()); var ownerPhone = capture()
        restore(emptyMap())
        context.getSharedPreferences("xmusic_library_default", Context.MODE_PRIVATE).edit().putString("data", """{"favorites":[{"id":"experimental:bbbbbbbbbbb","title":"Fatma favorisi"}]}""").commit()
        client.join("spouse-token", "spouse-account", invitation, spouse, client.preview("spouse-token", invitation))
        assertEquals("Tolga favorisi", family.remoteMix("favorites").getJSONObject(0).getString("title"))
        assertTrue(context.getSharedPreferences("xmusic_library_$spouse", Context.MODE_PRIVATE).getString("data", "")!!.contains("Fatma favorisi"))
        assertTrue(profiles.change("select", kid, "", "", ""))
        val requested = """{"id":"experimental:ccccccccccc","title":"Çocuk isteği"}"""
        assertTrue(profiles.requestReview(kid, requested)); client.sync("spouse-token")
        var spousePhone = capture()
        restore(ownerPhone); client.sync("owner-token")
        assertEquals("Fatma favorisi", family.remoteMix("favorites").getJSONObject(0).getString("title"))
        assertEquals(1, JSONObject(profiles.childReview(kid)).getJSONArray("pending").length())
        assertTrue(profiles.approve(kid, requested, false, "1357")); client.sync("owner-token"); ownerPhone = capture()
        restore(spousePhone); client.sync("spouse-token")
        assertTrue(profiles.isChild()); assertNull(profiles.playbackBlockReason("experimental:ccccccccccc"))
        assertEquals(0, JSONObject(profiles.childReview(kid)).getJSONArray("pending").length())
        assertFalse(profiles.change("select", spouse, "", "", ""))
        assertFalse(profiles.approve(kid, """{"id":"experimental:ddddddddddd","title":"İzinsiz"}""", false, "1357"))
        client.sync("spouse-token"); spousePhone = capture()
        restore(ownerPhone); client.sync("owner-token")
        assertEquals(0, JSONObject(profiles.childReview(kid)).getJSONArray("pending").length())
        assertTrue(context.getSharedPreferences("xmusic_library_$owner", Context.MODE_PRIVATE).getString("data", "")!!.contains("Tolga favorisi"))
        assertEquals(3, files.size) // Bir ayar dosyası, iki bağımsız cihaz dosyası.
    }
}
