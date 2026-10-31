package com.example

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import org.json.JSONObject

/** Media3 tek doğruluk kaynağıdır; WebView yalnızca durumunu gösterir. */
class PlaybackWebBridge(private val webView: WebView) {
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var active = false
    private val coverArt = CoverArtStore(webView.context)
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            publish()
            handler.postDelayed(this, 500)
        }
    }

    fun attach(value: MediaController) {
        controller?.removeListener(listener)
        controller = value
        value.addListener(listener)
        publish()
    }

    fun start() { active = true; handler.removeCallbacks(ticker); handler.post(ticker) }
    fun stop() { active = false; handler.removeCallbacks(ticker) }
    fun close() { stop(); controller?.removeListener(listener); controller = null }

    fun publish() {
        if (!active) return
        val player = controller
        val item = player?.currentMediaItem
        val duration = (player?.duration ?: 0L).coerceAtLeast(0L)
        val state = JSONObject().apply {
            val queue = org.json.JSONArray()
            if (player != null) for (i in 0 until player.mediaItemCount) {
                val track = player.getMediaItemAt(i)
                queue.put(JSONObject().put("id", track.mediaId)
                    .put("title", track.mediaMetadata.title?.toString().orEmpty())
                    .put("artist", track.mediaMetadata.artist?.toString().orEmpty())
                    .put("artwork", coverArt.webArtwork(track.mediaMetadata)))
            }
            put("queue", queue)
            put("index", player?.currentMediaItemIndex ?: -1)
            put("shuffle", player?.shuffleModeEnabled == true)
            put("repeat", player?.repeatMode ?: 0)
            put("speed", player?.playbackParameters?.speed ?: 1f)
            val service = com.example.playback.PlaybackService.activeService
            put("sleepRemaining", if ((service?.sleepDeadline ?: 0L) > 0L)
                (service!!.sleepDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) else 0L)
            put("sleepAtEnd", service?.sleepAtEnd == true)
            put("familyBlock", service?.familyBlockMessage.orEmpty())
            put("ready", player != null)
            put("id", item?.mediaId.orEmpty())
            put("title", item?.mediaMetadata?.title?.toString().orEmpty())
            put("artist", item?.mediaMetadata?.artist?.toString().orEmpty())
            put("artwork", item?.let { coverArt.webArtwork(it.mediaMetadata) }.orEmpty())
            put("playing", player?.isPlaying == true)
            put("pending", player?.playWhenReady == true && player.playbackState == Player.STATE_BUFFERING)
            put("ended", player?.playbackState == Player.STATE_ENDED)
            put("position", (player?.currentPosition ?: 0L).coerceAtLeast(0L))
            put("duration", duration)
            put("seekable", player?.isCurrentMediaItemSeekable == true && duration > 0)
            put("hasNext", player?.hasNextMediaItem() == true)
            put("error", player?.playerError?.let {
                var cause: Throwable? = it
                var httpCode: Int? = null
                while (cause != null) {
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }
                "Ses açılamadı. Kaynak erişimini kontrol et. (" + it.errorCodeName +
                    (httpCode?.let { code -> ", HTTP $code" } ?: "") + ")"
            }.orEmpty())
        }
        webView.evaluateJavascript("window.__xmusicPlayback && window.__xmusicPlayback($state)", null)
    }
}


