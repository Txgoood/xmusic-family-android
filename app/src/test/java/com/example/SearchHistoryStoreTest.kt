package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        listOf("test_one", "test_two").forEach {
            context.getSharedPreferences("xmusic_library_$it", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun historySurvivesRecreationAndDoesNotLeakToAnotherProfile() {
        SearchHistoryStore(context, "test_one").update("add", "Tarkan")
        assertEquals("Tarkan", JSONArray(SearchHistoryStore(context, "test_one").update("read", "")).getString(0))
        assertEquals(0, JSONArray(SearchHistoryStore(context, "test_two").update("read", "")).length())
    }

    @Test fun turkishCaseAndSpacesDeduplicateAndReorderLatestSearch() {
        val store = SearchHistoryStore(context, "test_one")
        store.update("add", "  Barış   Manço  ")
        store.update("add", "Tarkan")
        val rows = JSONArray(store.update("add", "BARIŞ MANÇO"))
        assertEquals(2, rows.length())
        assertEquals("BARIŞ MANÇO", rows.getString(0))
        assertEquals("Tarkan", rows.getString(1))
    }

    @Test fun onlyTwelveRecentQueriesAreRetainedAndInvalidQueriesAreIgnored() {
        val store = SearchHistoryStore(context, "test_one")
        repeat(20) { store.update("add", "Sanatçı $it") }
        assertEquals(12, JSONArray(store.update("read", "")).length())
        assertEquals("Sanatçı 19", JSONArray(store.update("read", "")).getString(0))
        assertEquals("Sanatçı 8", JSONArray(store.update("read", "")).getString(11))
        store.update("add", " ")
        store.update("add", "T")
        store.update("add", "x".repeat(101))
        assertEquals("Sanatçı 19", JSONArray(store.update("read", "")).getString(0))
    }

    @Test fun removeAndClearPersistWithoutChangingLibraryOrOtherProfile() {
        val store = SearchHistoryStore(context, "test_one")
        context.getSharedPreferences("xmusic_library_test_one", Context.MODE_PRIVATE).edit().putString("data", "library").commit()
        store.update("add", "Tarkan")
        store.update("add", "Mabel Matiz")
        SearchHistoryStore(context, "test_two").update("add", "Barış Manço")
        assertEquals("Mabel Matiz", JSONArray(store.update("remove", "TARKAN")).getString(0))
        store.update("clear", "")
        assertEquals(0, JSONArray(SearchHistoryStore(context, "test_one").update("read", "")).length())
        assertEquals("library", context.getSharedPreferences("xmusic_library_test_one", Context.MODE_PRIVATE).getString("data", ""))
        assertEquals(1, JSONArray(SearchHistoryStore(context, "test_two").update("read", "")).length())
    }
}
