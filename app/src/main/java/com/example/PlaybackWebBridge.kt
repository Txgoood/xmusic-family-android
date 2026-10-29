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
            put("ready", player != null)
            put("id", item?.mediaId.orEmpty())
            put("title", item?.mediaMetadata?.title?.toString().orEmpty())
            put("artist", item?.mediaMetadata?.artist?.toString().orEmpty())
            put("artwork", item?.mediaMetadata?.artworkUri?.toString().orEmpty())
            put("playing", player?.isPlaying == true)
            put("pending", player?.playWhenReady == true && player.playbackState == Player.STATE_BUFFERING)
            put("ended", player?.playbackState == Player.STATE_ENDED)
            put("position", (player?.currentPosition ?: 0L).coerceAtLeast(0L))
            put("duration", duration)
            put("seekable", player?.isCurrentMediaItemSeekable == true && duration > 0)
            put("hasNext", player?.hasNextMediaItem() == true)
            put("error", player?.playerError?.let {
                "Ses açılamadı. İnternet bağlantısını ve kaynağın erişimini kontrol et. (" + it.errorCodeName + ")"
            }.orEmpty())
        }
        webView.evaluateJavascript("window.__xmusicPlayback && window.__xmusicPlayback($state)", null)
    }
}
