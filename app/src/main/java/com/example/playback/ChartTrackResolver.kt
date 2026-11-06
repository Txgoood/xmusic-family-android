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

internal data class ChartDriveSource(val scope: String, val token: String)

internal class ChartTrackResolver(private val context: Context) {
    data class Resolved(val item: MediaItem, val url: String, val headers: Map<String, String>)

    fun resolve(item: MediaItem, drive: ChartDriveSource?): Resolved? {
        val title = item.mediaMetadata.title.toString()
        val artist = item.mediaMetadata.artist.toString()
        val match = drive?.let { source ->
            runCatching { DriveArchiveIndex(context).use { it.exact(source.scope, title, artist) } }.getOrNull()
        }
        val fileId = match?.optString("id").orEmpty()
        if (drive != null && fileId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val coverKey = CoverArtStore.key(drive.scope, fileId, match?.optString("modified").orEmpty())
            val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(CoverArtStore.EXTRA, coverKey) }
            val metadata = item.mediaMetadata.buildUpon().setExtras(extras)
            CoverArtStore(context).nativeUri(coverKey)?.let { metadata.setArtworkUri(it) }
            return Resolved(item.buildUpon().setMediaId("drive:$fileId").setUri(url)
                .setMediaMetadata(metadata.build()).build(), url, mapOf("Authorization" to "Bearer ${drive.token}"))
        }

        val engine = YoutubeDL.getInstance()
        engine.init(context.applicationContext)
        val request = YoutubeDLRequest("ytsearch10:$artist - $title")
        request.addOption("--flat-playlist")
        request.addOption("--dump-single-json")
        request.addOption("--socket-timeout", "10")
        request.addOption("--retries", "1")
        val response = engine.execute(request, item.mediaId) { _, _, _ -> }
        val rows = JSONObject(response.out).getJSONArray("entries")
        val expected = matchKey("$artist - $title")
        val id = (0 until rows.length()).asSequence().mapNotNull { rows.optJSONObject(it) }
            .firstOrNull { row ->
                matchKey(row.optString("title")) == expected &&
                    row.optString("id").matches(Regex("[A-Za-z0-9_-]{11}"))
            }?.optString("id") ?: return null
        val stream = ExperimentalAudioResolver(context).resolve("https://www.youtube.com/watch?v=$id")
        context.getSharedPreferences("xmusic_auto_archive", Context.MODE_PRIVATE)
            .edit().putBoolean("require_license:$id", true).apply()
        val metadata = item.mediaMetadata.buildUpon()
            .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/mqdefault.jpg")).build()
        return Resolved(item.buildUpon().setMediaId("experimental:$id").setUri(stream.url)
            .setMediaMetadata(metadata).build(), stream.url, stream.headers)
    }

    private fun matchKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).lowercase(Locale.ROOT).replace('ı', 'i')
            .replace(Regex("""[\p{M}]"""), "")
            .replace(Regex("""\s*[\[(](official (music )?video|official audio|resmi video)[\])]\s*$"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
}
