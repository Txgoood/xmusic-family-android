package com.example.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.ProfileStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContinuousPlaybackTest {
    private fun current(service: PlaybackService): ExoPlayer = PlaybackService::class.java.getDeclaredField("player").let {
        it.isAccessible = true; it.get(service) as ExoPlayer
    }
    private fun drainUntil(done: () -> Boolean) {
        repeat(150) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
        assertTrue("Asynchronous preparation did not finish", done())
    }
    @Test fun missingSelectedTrackLeavesTheExistingQueueAndPlaybackIntentIntact() {
        val host = Robolectric.buildService(PlaybackService::class.java).create()
        val service = host.get(); val release = CountDownLatch(1); val entered = CountDownLatch(1)
        try {
            val old = current(service)
            old.setMediaItem(MediaItem.Builder().setMediaId("existing").setUri("https://example.org/existing.mp3").build())
            old.playWhenReady = true
            service.chartSourceResolver = { _, _, _, _ -> entered.countDown(); release.await(2, TimeUnit.SECONDS); null }
            service.playChartQueue(listOf(ChartTrack("Missing", "Artist", 0L, "")), 0, ProfileStore(service).activeId(), null)
            assertTrue(entered.await(1, TimeUnit.SECONDS)); assertEquals("existing", old.currentMediaItem?.mediaId)
            assertTrue(old.playWhenReady); assertTrue(service.selectionBusy)
            release.countDown(); drainUntil { !service.selectionBusy }
            assertSame(old, current(service)); assertEquals("existing", old.currentMediaItem?.mediaId)
            assertEquals(1, old.mediaItemCount); assertTrue(old.playWhenReady)
            assertTrue(service.selectionMessage.contains("Şimdilik"))
        } finally { release.countDown(); host.destroy() }
    }
    @Test fun pausingWhileASelectionIsPreparingCannotStartItLater() {
        val host = Robolectric.buildService(PlaybackService::class.java).create()
        val service = host.get(); val release = CountDownLatch(1); val entered = CountDownLatch(1)
        try {
            val old = current(service)
            old.setMediaItem(MediaItem.Builder().setMediaId("existing").setUri("https://example.org/existing.mp3").build())
            old.playWhenReady = true
            service.chartSourceResolver = { item, _, _, _ ->
                entered.countDown(); release.await(2, TimeUnit.SECONDS)
                ChartTrackResolver.Resolved(item.buildUpon().setMediaId("experimental:abcdefghijk").setUri("https://example.org/new.mp3").build(), "https://example.org/new.mp3", emptyMap())
            }
            service.playChartQueue(listOf(ChartTrack("New", "Artist", 0L, "")), 0, ProfileStore(service).activeId(), null)
            assertTrue(entered.await(1, TimeUnit.SECONDS)); old.pause()
            assertFalse(service.selectionBusy); release.countDown()
            repeat(20) { Thread.sleep(10); shadowOf(android.os.Looper.getMainLooper()).idle() }
            assertSame(old, current(service)); assertEquals("existing", old.currentMediaItem?.mediaId)
            assertFalse(old.playWhenReady)
        } finally { release.countDown(); host.destroy() }
    }
}
