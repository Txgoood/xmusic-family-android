package com.example.playback

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TrackPreparationQueueTest {
    @Test fun coalescesTheSameTrackAndBoundsConcurrentWork() {
        val queue = TrackPreparationQueue<String, String> { it() }
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val delivered = CountDownLatch(2); val calls = AtomicInteger()
        try {
            queue.request("same", false, { calls.incrementAndGet(); entered.countDown(); release.await(2, TimeUnit.SECONDS); "ready" }) { assertEquals("ready", it); delivered.countDown() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            queue.request("same", true, { error("Second search must not run") }) { assertEquals("ready", it); delivered.countDown() }
            release.countDown(); assertTrue(delivered.await(2, TimeUnit.SECONDS)); assertEquals(1, calls.get())
        } finally { release.countDown(); queue.close() }
    }
    @Test fun userSelectionRunsBeforeWaitingBackgroundSearches() {
        val queue = TrackPreparationQueue<String, String> { it() }
        val started = CountDownLatch(2); val releaseFirst = CountDownLatch(1); val releaseSecond = CountDownLatch(1)
        val urgent = CountDownLatch(1); val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        try {
            queue.request("block1", false, { started.countDown(); releaseFirst.await(3, TimeUnit.SECONDS); "a" }) { }
            queue.request("block2", false, { started.countDown(); releaseSecond.await(3, TimeUnit.SECONDS); "b" }) { }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            queue.request("background", false, { order.add("background"); "x" }) { }
            queue.request("selected", true, { order.add("selected"); "y" }) { urgent.countDown() }
            releaseFirst.countDown(); assertTrue(urgent.await(2, TimeUnit.SECONDS)); assertEquals("selected", order.first())
        } finally { releaseFirst.countDown(); releaseSecond.countDown(); queue.close() }
    }
}
