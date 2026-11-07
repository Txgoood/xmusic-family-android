package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.drive.DriveCoverResolver
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverArtStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("xmusic_cover_art", Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(context.cacheDir, "xmusic-covers").deleteRecursively()
    }
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.GREEN)
        return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it); bitmap.recycle() }.toByteArray()
    }
    @Test fun cachedCoversAreSmallLocalImmutableAndHigherPriorityWins() {
        val store = CoverArtStore(context); val key = CoverArtStore.key("account1", "track1", "version1")
        assertTrue(store.save(key, image(), 1))
        assertTrue(CoverArtStore.isWebCover(store.webUri(key)))
        val file = store.webFile(android.net.Uri.parse(store.webUri(key)).path!!)!!
        assertTrue(file.length() <= 300000)
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath); assertTrue(decoded.width <= 384 && decoded.height <= 384); decoded.recycle()
        assertNotNull(store.nativeUri(key))
        assertTrue(store.save(key, image(), 3)); assertEquals(3, store.priority(key))
        assertFalse(store.save(key, image(), 2)); assertEquals(3, store.priority(key))
        assertEquals("", store.webUri(CoverArtStore.key("account2", "track1", "version1")))
        assertNull(store.webFile("/artwork/../../secret.jpg"))
        assertFalse(store.save(key, ByteArray(2_000_001), 3))
        file.delete(); assertEquals("", store.webUri(key))
    }
    @Test fun failedLookupsAreThrottledAndWrongCatalogueArtistsAreRejected() {
        val store = CoverArtStore(context); val key = CoverArtStore.key("account", "track", "v1")
        assertTrue(store.retryAllowed(key)); store.retryAfter(key, 600000); assertFalse(store.retryAllowed(key))
        val item = JSONObject().put("artistName", "Duman").put("trackName", "Her Şeyi Yak").put("collectionName", "Duman 1")
        assertTrue(DriveCoverResolver.matches(item, "Her Şeyi Yak", "Duman", ""))
        assertFalse(DriveCoverResolver.matches(item, "Her Şeyi Yak", "Başka Sanatçı", ""))
        assertFalse(DriveCoverResolver.matches(item, "Başka Şarkı", "Duman", "Başka Albüm"))
        assertTrue(DriveCoverResolver.rankName("cover.JPG") < DriveCoverResolver.rankName("random.jpg"))
    }
    @Test fun albumFolderCoverIsFetchedOnceAndSharedAcrossTracks() {
        val picture = image(); val calls = mutableListOf<String>()
        val resolver = object : DriveCoverResolver(context) {
            override fun read(address: String, token: String?, max: Int): ByteArray {
                calls.add(address)
                return if (address.contains("files?q=")) """{"files":[{"id":"cover123","name":"cover.jpg","size":"1000","parents":["album123"]}]}""".toByteArray()
                else picture
            }
        }
        val first = CoverArtStore.key("scope", "song1", "v1"); val second = CoverArtStore.key("scope", "song2", "v1")
        resolver.resolve(first, "scope", "song1", "album123", "Her Şeyi Yak", "Duman", "Album", "token") { true }
        assertEquals(2, calls.size); assertEquals(2, CoverArtStore(context).priority(first))
        resolver.resolve(second, "scope", "song2", "album123", "Başka Şarkı", "Duman", "Album", "token") { true }
        assertEquals(2, calls.size); assertEquals(CoverArtStore(context).webUri(first), CoverArtStore(context).webUri(second))
        resolver.resolve("bad", "scope", "song3", "album123", "", "", "", "token") { false }
        assertEquals(2, calls.size)
    }
}
