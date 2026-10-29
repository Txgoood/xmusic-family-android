package com.example.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var downloadManager: DownloadManager? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player!!).build()

        val databaseProvider = StandaloneDatabaseProvider(this)
        val downloadCache = SimpleCache(File(cacheDir, "downloads"), LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), databaseProvider)
        
        // Basit DownloadManager kurulumu
        // downloadManager = DownloadManager(this, databaseProvider, downloadCache, ...)
    }

    fun prepareTrack(url: String) {
        val mediaItem = androidx.media3.common.MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
