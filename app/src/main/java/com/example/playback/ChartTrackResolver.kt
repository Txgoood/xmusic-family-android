package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.example.CoverArtStore
import com.example.data.drive.DriveArchiveIndex
import com.example.data.source.ExperimentalAudioResolver
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal data class ChartTrack(val title: String, val artist: String, val duration: Long, val genre: String, val sourceId: String = "")

internal data class ChartDriveSource(val scope: String, val token: String)

internal class ChartTrackResolver(private val context: Context) {
    data class Resolved(val item: MediaItem, val url: String, val headers: Map<String, String>)

    private val prepared = LinkedHashMap<String, Pair<Long, Resolved>>()
    private val failedUntil = HashMap<String, Long>()
    fun key(item: MediaItem, drive: ChartDriveSource?): String = (drive?.scope ?: "") + "|" +
        ChartTrackMatch.normalize(item.mediaMetadata.artist.toString()) + "|" + ChartTrackMatch.normalize(item.mediaMetadata.title.toString())

    fun resolve(item: MediaItem, drive: ChartDriveSource?, excluded: Set<String> = emptySet(), retry: Boolean = false): Resolved? {
        val cacheKey = key(item, drive)

        val title = item.mediaMetadata.title.toString()
        val artist = item.mediaMetadata.artist.toString()
        val match = drive?.let { source ->
            runCatching { DriveArchiveIndex(context).use { index ->
                val sourceId = item.mediaMetadata.extras?.getString("chartSourceId").orEmpty()
                val direct = if (sourceId.startsWith("drive:")) index.get(source.scope, sourceId.removePrefix("drive:")) else null
                direct ?: index.exact(source.scope, title, artist)
            } }.getOrNull()
        }
        val fileId = match?.optString("id").orEmpty()
        if (drive != null && "drive:$fileId" !in excluded && fileId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val coverKey = CoverArtStore.key(drive.scope, fileId, match?.optString("modified").orEmpty())
            val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(CoverArtStore.EXTRA, coverKey) }
            val metadata = item.mediaMetadata.buildUpon().setExtras(extras)
            CoverArtStore(context).nativeUri(coverKey)?.let { metadata.setArtworkUri(it) }
            return Resolved(item.buildUpon().setMediaId("drive:$fileId").setUri(url)
                .setMediaMetadata(metadata.build()).build(), url, mapOf("Authorization" to "Bearer ${drive.token}"))
        }

        synchronized(prepared) {
            if (!retry) prepared[cacheKey]?.let { (time, resolved) ->
                if (android.os.SystemClock.elapsedRealtime() - time < 60_000L && resolved.item.mediaId !in excluded)
                    return resolved.copy(item = resolved.item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(resolved.item.mediaMetadata.artworkUri).build()).build())
            }
            if (!retry && (failedUntil[cacheKey] ?: 0L) > android.os.SystemClock.elapsedRealtime()) return null
        }
        val saved = context.getSharedPreferences("xmusic_track_matches", Context.MODE_PRIVATE)
        val stableKey = java.security.MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray()).joinToString("") { "%02x".format(it) }
        val preferred = item.mediaMetadata.extras?.getString("chartSourceId").orEmpty()
            .takeIf { it.startsWith("experimental:") || it.startsWith("drive-source:") }?.substringAfter(':')
        val remembered = preferred ?: saved.getString(stableKey, null)
        val candidates = linkedSetOf<String>()
        fun resolved(id: String): Resolved? {
            if (!id.matches(Regex("[A-Za-z0-9_-]{11}")) || "experimental:$id" in excluded) return null
            return runCatching {
                if (retry) ExperimentalAudioResolver.invalidate("https://www.youtube.com/watch?v=$id")
                val stream = ExperimentalAudioResolver(context).resolve("https://www.youtube.com/watch?v=$id")
                context.getSharedPreferences("xmusic_auto_archive", Context.MODE_PRIVATE)
                    .edit().putBoolean("require_license:$id", true).apply()
                val metadata = item.mediaMetadata.buildUpon().setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/mqdefault.jpg")).build()
                val result = Resolved(item.buildUpon().setMediaId("experimental:$id").setUri(stream.url).setMediaMetadata(metadata).build(), stream.url, stream.headers)
                saved.edit().putString(stableKey, id).apply()
                synchronized(prepared) {
                    if (prepared.size >= 24) prepared.remove(prepared.keys.first())
                    prepared[cacheKey] = android.os.SystemClock.elapsedRealtime() to result; failedUntil.remove(cacheKey)
                }
                result
            }.getOrNull()
        }
        if (remembered != null) {
            candidates.add(remembered)
            resolved(remembered)?.let { return it }
        }
        try {
            val engine = YoutubeDL.getInstance(); engine.init(context.applicationContext)
            val request = YoutubeDLRequest("ytsearch10:$artist - $title")
            request.addOption("--flat-playlist"); request.addOption("--dump-single-json")
            request.addOption("--socket-timeout", "8"); request.addOption("--retries", "0")
            val response = engine.execute(request, "chart-match:" + java.util.UUID.randomUUID().toString()) { _, _, _ -> }
            val rows = JSONObject(response.out).getJSONArray("entries")
            val duration = item.mediaMetadata.extras?.getLong("chartDuration") ?: 0L
            var tried = if (remembered != null) 1 else 0
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (!ChartTrackMatch.matches(title, artist, row.optString("title"), row.optString("uploader", row.optString("channel"))) ||
                    (duration > 0 && row.optDouble("duration").let { it.isFinite() && kotlin.math.abs(it * 1000 - duration) > 35_000 })) continue
                if (!candidates.add(id)) continue
                resolved(id)?.let { return it }
                if (++tried >= 3) break
            }
        } catch (_: Exception) { }
        synchronized(prepared) {
            prepared.remove(cacheKey)
            if (failedUntil.size >= 100) failedUntil.clear()
            failedUntil[cacheKey] = android.os.SystemClock.elapsedRealtime() + 60_000L
        }
        return null
    }
}
