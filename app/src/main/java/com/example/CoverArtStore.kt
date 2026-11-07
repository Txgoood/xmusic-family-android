package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import java.io.File
import java.util.concurrent.Executors

/** WebView'a büyük resim/base64 yerine küçük, cihaz içi bir URL verilir. */
class CoverArtStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("xmusic_cover_art", Context.MODE_PRIVATE)
    private val directory = File(app.cacheDir, "xmusic-covers").apply { mkdirs() }
    companion object {
        const val EXTRA = "xmusicCoverKey"
        const val HOST = "xmusic.local"
        private val decoder = Executors.newSingleThreadExecutor()
        private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun digest(value: String) = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun key(scope: String, id: String, version: String) = digest("$scope\u0000$id\u0000$version")
        fun isWebCover(value: String): Boolean = Regex("https://xmusic\\.local/artwork/[a-f0-9]{64}\\.jpg").matches(value)
        fun embedded(context: Context, key: String, bytes: ByteArray?) {
            if (bytes == null || bytes.isEmpty() || bytes.size > 2_000_000 || !key.matches(Regex("[a-f0-9]{64}")) || !pending.add(key)) return
            decoder.execute {
                try { val cache = CoverArtStore(context); if (cache.priority(key) < 3) cache.save(key, bytes, 3) }
                catch (_: Exception) { } finally { pending.remove(key) }
            }
        }
    }
    fun priority(key: String): Int = if (fileForKey(key) != null) prefs.getInt("priority_$key", 0) else 0
    private fun fileForKey(key: String): File? {
        if (!key.matches(Regex("[a-f0-9]{64}"))) return null
        val name = prefs.getString(key, "").orEmpty()
        if (!name.matches(Regex("[a-f0-9]{64}\\.jpg"))) return null
        return File(directory, name).takeIf { it.isFile && it.length() in 1..300000 }
    }
    fun nativeUri(key: String): Uri? = fileForKey(key)?.let(Uri::fromFile)
    fun webUri(key: String): String = fileForKey(key)?.let { "https://$HOST/artwork/${it.name}" }.orEmpty()
    fun webArtwork(metadata: MediaMetadata): String {
        val key = metadata.extras?.getString(EXTRA).orEmpty()
        val local = webUri(key); if (local.isNotEmpty()) return local
        val value = metadata.artworkUri?.toString().orEmpty()
        return if (value.startsWith("https://") && !value.startsWith("https://$HOST/")) value else ""
    }
    fun webFile(path: String): File? {
        if (!path.matches(Regex("/artwork/[a-f0-9]{64}\\.jpg"))) return null
        return File(directory, path.substringAfterLast('/')).takeIf { it.isFile && it.length() in 1..300000 }
    }
    @Synchronized fun save(key: String, bytes: ByteArray, rank: Int): Boolean {
        if (!key.matches(Regex("[a-f0-9]{64}")) || bytes.isEmpty() || bytes.size > 2_000_000 || rank !in 1..3 || priority(key) >= rank) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 20000 || bounds.outHeight > 20000 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return false
        var sample = 1
        while (bounds.outWidth / sample > 384 || bounds.outHeight / sample > 384) sample *= 2
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val output = java.io.ByteArrayOutputStream()
        try { if (!image.compress(Bitmap.CompressFormat.JPEG, 82, output)) return false } finally { image.recycle() }
        val encoded = output.toByteArray(); if (encoded.size > 300000) return false
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it.toInt() and 255) } + ".jpg"
        val target = File(directory, name)
        if (!target.isFile) {
            val temporary = File.createTempFile("cover", ".tmp", directory)
            try { temporary.writeBytes(encoded); if (!temporary.renameTo(target) && !target.isFile) return false } finally { temporary.delete() }
        }
        // Gömülü kapak daha geç geldiyse düşük öncelikli internet resmi üzerine yazılmaz.
        synchronized(CoverArtStore::class.java) {
            if (priority(key) > rank) return false
            prefs.edit().putString(key, name).putInt("priority_$key", rank).apply()
        }
        prune(target)
        return true
    }
    private fun prune(keep: File) {
        val files = directory.listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.lastModified() } ?: return
        var bytes = files.sumOf { it.length() }
        for (file in files) { if (bytes <= 32L * 1024 * 1024) break; if (file != keep) { val size = file.length(); if (file.delete()) bytes -= size } }
    }
    fun retryAllowed(key: String): Boolean = System.currentTimeMillis() >= prefs.getLong("retry_$key", 0L)
    fun retryAfter(key: String, delay: Long) { prefs.edit().putLong("retry_$key", System.currentTimeMillis() + delay).apply() }
    fun copy(from: String, to: String, rank: Int): Boolean {
        val file = fileForKey(from) ?: return false
        synchronized(CoverArtStore::class.java) {
            if (priority(to) >= rank) return false
            prefs.edit().putString(to, file.name).putInt("priority_$to", rank).apply()
        }
        return true
    }
}
