package com.example.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    companion object { var activeService: PlaybackService? = null; private set }
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var sleepDeadline = 0L
        private set
    var sleepAtEnd = false
        private set
    private val stopPlayback = Runnable { player?.pause(); sleepDeadline = 0L; sleepAtEnd = false }
    fun setSleepTimer(minutes: Int) {
        timerHandler.removeCallbacks(stopPlayback)
        sleepDeadline = 0L
        sleepAtEnd = minutes == -1
        if (minutes in 1..120) {
            sleepDeadline = android.os.SystemClock.elapsedRealtime() + minutes * 60000L
            timerHandler.postDelayed(stopPlayback, minutes * 60000L)
        }
    }
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player!!.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                if (sleepAtEnd && (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) stopPlayback.run()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (sleepAtEnd && state == androidx.media3.common.Player.STATE_ENDED) stopPlayback.run()
            }
        })
        // Uygulamaya dönüş ve kilit ekranı aynı MediaSession'ı kullanır.
        val intent = android.content.Intent(this, com.example.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val activity = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSession.Builder(this, player!!).setSessionActivity(activity).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        timerHandler.removeCallbacks(stopPlayback)
        activeService = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
