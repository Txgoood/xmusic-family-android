package com.example.playback

import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** En fazla iki iş çalışır; aynı kayda ait istekler birleşir, kullanıcı seçimi öne geçer. */
internal class TrackPreparationQueue<K, V>(private val deliver: ((() -> Unit) -> Unit)) {
    private val lock = Any()
    private var serial = 0L
    private val jobs = LinkedHashMap<K, Job>()
    private val pool = ThreadPoolExecutor(2, 2, 20L, TimeUnit.SECONDS, PriorityBlockingQueue<Runnable>()).apply { allowCoreThreadTimeOut(true) }
    private inner class Job(val key: K, var priority: Int, val order: Long, val load: () -> V?) : Runnable, Comparable<Job> {
        val callbacks = mutableListOf<(V?) -> Unit>()
        override fun compareTo(other: Job): Int = compareValues(priority, other.priority).takeIf { it != 0 } ?: other.order.compareTo(order)
        override fun run() {
            val result = runCatching(load).getOrNull()
            val listeners = synchronized(lock) { jobs.remove(key); callbacks.toList() }
            deliver { listeners.forEach { it(result) } }
        }
    }
    fun request(key: K, urgent: Boolean, load: () -> V?, callback: (V?) -> Unit) {
        synchronized(lock) {
            if (pool.isShutdown) { deliver { callback(null) }; return }
            jobs[key]?.let { job ->
                job.callbacks.add(callback)
                if (urgent && pool.remove(job)) { job.priority = 0; pool.execute(job) }
                return
            }
            if (jobs.size >= 8) {
                if (!urgent) { deliver { callback(null) }; return }
                val old = jobs.values.firstOrNull { pool.remove(it) }
                if (old != null) { jobs.remove(old.key); deliver { old.callbacks.forEach { it(null) } } }
            }
            val job = Job(key, if (urgent) 0 else 1, ++serial, load)
            job.callbacks.add(callback); jobs[key] = job; pool.execute(job)
        }
    }
    fun close() { pool.shutdownNow(); synchronized(lock) { jobs.clear() } }
}
