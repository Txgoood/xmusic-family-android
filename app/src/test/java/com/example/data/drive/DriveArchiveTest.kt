package com.example.data.drive

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
import java.net.URLDecoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveArchiveTest {
    private lateinit var context: Context
    @Before fun reset() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("xmusic_drive_index.db") }
    @Test fun deepFoldersPaginationCyclesAndDuplicateFilesStayWithinTheSelectedRoot() {
        fun item(id: String, name: String, parent: String, mime: String) = JSONObject().put("id", id).put("name", name).put("parents", JSONArray().put(parent)).put("mimeType", mime)
        val rows = listOf(
            item("artist", "Mabel Matiz", "root", "application/vnd.google-apps.folder"),
            item("album", "Fatih", "artist", "application/vnd.google-apps.folder"),
            item("disc", "Disc 1", "album", "application/vnd.google-apps.folder"),
            item("track1", "01 - Gel.MP3", "disc", "audio/mpeg"),
            item("track2", "02 - Antidepresan.m4a", "disc", "application/octet-stream"),
            item("track3", "03 - Sarışın.flac", "disc", "audio/flac"),
            item("cover", "Cover.jpg", "album", "image/jpeg"),
            item("outside", "Outside.mp3", "unrelated", "audio/mpeg"),
            item("root", "Cycle", "disc", "application/vnd.google-apps.folder"),
            item("track1", "Duplicate.mp3", "disc", "audio/mpeg")
        )
        var calls = 0
        val client = object : DriveFolderClient() {
            override fun request(address: String, token: String, body: JSONObject?): JSONObject {
                calls++; assertTrue(calls < 15)
                val decoded = URLDecoder.decode(address, "UTF-8")
                val parents = Regex("'([A-Za-z0-9_-]+)' in parents").findAll(decoded).map { it.groupValues[1] }.toSet()
                assertFalse(parents.contains("unrelated"))
                val selected = rows.filter { it.getJSONArray("parents").getString(0) in parents }
                val offset = Regex("pageToken=(\\d+)").find(decoded)?.groupValues?.get(1)?.toInt() ?: 0
                val result = JSONObject().put("files", JSONArray(selected.drop(offset).take(2)))
                if (offset + 2 < selected.size) result.put("nextPageToken", (offset + 2).toString())
                return result
            }
        }
        val tracks = client.listAudio("test", "root")
        assertEquals(setOf("track1", "track2", "track3"), tracks.map { it.id }.toSet())
        assertTrue(tracks.all { it.path == "Mabel Matiz/Fatih/Disc 1" })
        assertTrue(calls >= 5)
    }
    @Test fun partialOrRepeatedPagesAreRejected() {
        val repeated = object : DriveFolderClient() {
            override fun request(address: String, token: String, body: JSONObject?) = JSONObject().put("files", JSONArray()).put("nextPageToken", "same")
        }
        assertThrows(Exception::class.java) { repeated.listAudio("test", "root") }
        val incomplete = object : DriveFolderClient() {
            override fun request(address: String, token: String, body: JSONObject?) = JSONObject().put("files", JSONArray()).put("incompleteSearch", true)
        }
        assertThrows(Exception::class.java) { incomplete.listAudio("test", "root") }
    }
    @Test fun filenameFolderAndTagIndexIsScopedAndDoesNotGuessDifferentVersions() {
        DriveArchiveIndex(context).use { index ->
            val scope = DriveArchiveIndex.scope("account1", "root")
            val other = DriveArchiveIndex.scope("account2", "root")
            val studio = DriveFolderClient.Track("studio", "01 - Gel.mp3", path = "Kişisel Müzik Arşivi/Mabel Matiz/Fatih/Disc 1", modified = "one")
            val live = DriveFolderClient.Track("live", "Mabel Matiz - Gel (Live).mp3", path = "Konserler", modified = "one")
            index.replace(scope, listOf(studio, live))
            assertEquals(2, index.status(scope).getInt("count"))
            assertEquals(2, index.search(scope, "mabel matiz gel").length())
            assertEquals(0, index.search(other, "mabel").length())
            assertEquals("studio", index.exact(scope, "Mabel Matiz - Gel (Official Music Video)", "netd müzik")!!.getString("id"))
            assertNull(index.exact(scope, "Gel (Remix)", "Mabel Matiz"))
            assertNull(index.exact(scope, "Gel", "Başka Sanatçı"))
            assertEquals("live", index.exact(scope, "Gel (Live)", "Mabel Matiz")!!.getString("id"))
            index.saveTags(scope, "studio", "one", DriveArchiveIndex.Metadata("Sarı Çiçek", "Mabel Matiz", "Fatih"))
            assertEquals(1, index.search(scope, "sari cicek").length())
            index.replace(scope, listOf(studio))
            assertEquals("Sarı Çiçek", index.get(scope, "studio")!!.getString("title"))
            assertNull(index.get(scope, "live"))
            index.markError(scope); assertEquals(1, index.search(scope, "sari").length())
            index.replace(scope, listOf(studio.copy(modified = "two")))
            assertEquals("Gel", index.get(scope, "studio")!!.getString("title"))
            assertEquals(0, index.search(scope, "' OR 1=1 --").length())
        }
    }
    @Test fun ambiguousCopiesRequireSelectionAndTagsAreReadFromBoundedMp3Headers() {
        DriveArchiveIndex(context).use { index ->
            index.replace("test", listOf(DriveFolderClient.Track("a", "Tarkan - Dudu.mp3"), DriveFolderClient.Track("b", "Tarkan - Dudu.mp3")))
            assertNull(index.exact("test", "Dudu", "Tarkan"))
        }
        fun frame(id: String, text: String): ByteArray {
            val data = byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)
            return id.toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, data.size.toByte(), 0, 0) + data
        }
        val frames = frame("TIT2", "Gülpembe") + frame("TPE1", "Barış Manço") + frame("TALB", "Sözüm Meclisten Dışarı")
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, (frames.size shr 7).toByte(), (frames.size and 127).toByte())
        val tags = DriveId3Reader.parse(header + frames)
        assertEquals("Gülpembe", tags.title); assertEquals("Barış Manço", tags.artist)
        assertEquals("Sözüm Meclisten Dışarı", tags.album)
        assertEquals("", DriveId3Reader.parse(byteArrayOf(1, 2)).title)
        assertEquals("", DriveId3Reader.parse(header + frames.copyOf(6)).title)
    }
}
