package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchStatsStoreTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        listOf("one", "two").forEach { context.getSharedPreferences("xmusic_library_$it", Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @Test fun frequenciesSurviveRecreationAndAreProfileSpecific() {
        val one=SearchStatsStore(context,"one")
        one.record("Tarkan");one.record(" Mabel   Matiz ");one.record("TARKAN")
        val rows=SearchStatsStore(context,"one").read()
        assertEquals(2,rows.length())
        assertEquals("TARKAN",rows.getJSONObject(0).getString("query"))
        assertEquals(2,rows.getJSONObject(0).getInt("count"))
        assertEquals(0,SearchStatsStore(context,"two").read().length())
    }
    @Test fun invalidQueriesDoNotCreateCounts() {
        val store=SearchStatsStore(context,"one")
        store.record(" ");store.record("a");store.record("x".repeat(101))
        assertEquals(0,store.read().length())
    }
}
