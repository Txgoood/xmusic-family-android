package com.example.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import com.example.ProfileStore
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
    private var fadeEnabled = false
    private var originalVolume: Float? = null
    private fun restoreVolume() { originalVolume?.let { player?.volume = it }; originalVolume = null }
    private val stopPlayback = Runnable { player?.pause(); sleepDeadline = 0L; sleepAtEnd = false; restoreVolume() }
    private val fadeTicker = object : Runnable {
        override fun run() {
            val p = player
            if (fadeEnabled && p != null && (sleepDeadline > 0L || sleepAtEnd)) {
                val remaining = if (sleepAtEnd) {
                    if (p.isPlaying && p.duration > 0) (p.duration - p.currentPosition).coerceAtLeast(0L) else Long.MAX_VALUE
                } else (sleepDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining <= 30000L) {
                    if (originalVolume == null) originalVolume = p.volume
                    p.volume = (originalVolume!! * remaining / 30000f).coerceIn(0f, 1f)
                } else restoreVolume()
            }
            timerHandler.postDelayed(this, 250L)
        }
    }
    fun setSleepFade(enabled: Boolean) { fadeEnabled = enabled; if (!enabled) restoreVolume() }
    fun setSleepTimer(minutes: Int) {
        timerHandler.removeCallbacks(stopPlayback)
        restoreVolume()
        sleepDeadline = 0L
        sleepAtEnd = minutes == -1
        if (minutes in 1..120) {
            sleepDeadline = android.os.SystemClock.elapsedRealtime() + minutes * 60000L
            timerHandler.postDelayed(stopPlayback, minutes * 60000L)
        }
    }
    private val experimentalHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    fun registerExperimentalStream(url: String, headers: Map<String, String>) {
        // Başlıklar yalnızca çözülen akış adresi için geçerlidir.
        experimentalHeaders.clear()
        experimentalHeaders[url] = headers
    }

    fun audioSessionId(): Int = player?.audioSessionId ?: 0

    private var player: ExoPlayer? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private val chartResolver by lazy { ChartTrackResolver(applicationContext) }
    internal var chartSourceResolver: (MediaItem, ChartDriveSource?, Set<String>, Boolean) -> ChartTrackResolver.Resolved? = { item, drive, excluded, retry -> chartResolver.resolve(item, drive, excluded, retry) }
    private val chartHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val preparations = TrackPreparationQueue<String, ChartTrackResolver.Resolved> { action -> timerHandler.post { action() } }
    private var chartGeneration = 0L
    private var selectionSerial = 0L
    private var chartProfile = ""
    private var chartDrive: ChartDriveSource? = null
    private val preparingSlots = mutableSetOf<String>()
    private val retriedSlots = mutableSetOf<String>()
    private var warmingPlayer: ExoPlayer? = null
    private var warmingTimeout: Runnable? = null
    private var closing = false
    private var ownChartChange = false
    private var countedItem = false
    var selectionBusy = false
        private set
    var selectionTitle = ""
        private set
    var selectionArtist = ""
        private set
    var selectionMessage = ""
        private set

    private fun rememberHeaders(result: ChartTrackResolver.Resolved) {
        chartHeaders[result.url] = result.headers
        if (chartHeaders.size > 128) {
            val keep = mutableSetOf(result.url)
            listOfNotNull(player, warmingPlayer).forEach { p ->
                for (i in 0 until p.mediaItemCount) p.getMediaItemAt(i).localConfiguration?.uri?.toString()?.let { keep.add(it) }
            }
            chartHeaders.keys.filter { it !in keep }.forEach { chartHeaders.remove(it) }
        }
    }
    private fun chartItem(track: ChartTrack, generation: Long, position: Int): MediaItem {
        val id = "chart-pending:$generation:$position"
        val extras = android.os.Bundle().apply {
            putLong("chartDuration", track.duration); putString("chartGenre", track.genre)
            putString("chartSourceId", track.sourceId); putString("chartSlot", id)
        }
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setTitle(track.title).setArtist(track.artist).setExtras(extras).build()).build()
    }
    private fun placeholder(item: MediaItem) = SilenceMediaSource(600_000_000L).apply { updateMediaItem(item) }
    private fun preparationKey(item: MediaItem, drive: ChartDriveSource?, retry: Boolean = false) = chartResolver.key(item, drive) + if (retry) "|retry" else ""
    private fun prepareTrack(item: MediaItem, drive: ChartDriveSource?, urgent: Boolean, retry: Boolean = false, excluded: Set<String> = emptySet(), callback: (ChartTrackResolver.Resolved?) -> Unit) {
        preparations.request(preparationKey(item, drive, retry), urgent, { chartSourceResolver(item, drive, excluded, retry) }, callback)
    }
    internal fun warmChartQueue(tracks: List<ChartTrack>, profile: String, drive: ChartDriveSource?) {
        if (ProfileStore(this).activeId() != profile || ProfileStore(this).isChild()) return
        tracks.take(3).forEachIndexed { index, track -> prepareTrack(chartItem(track, -1L, index), drive, false) { } }
    }
    internal fun cancelPendingSelection() = cancelSelection()
    private fun cancelSelection(clearMessage: Boolean = true) {
        selectionSerial++; selectionBusy = false
        warmingTimeout?.let(timerHandler::removeCallbacks); warmingTimeout = null
        warmingPlayer?.release(); warmingPlayer = null
        if (clearMessage) selectionMessage = ""
    }
    private fun selectionValid(serial: Long, profile: String) = selectionBusy && serial == selectionSerial &&
        ProfileStore(this).activeId() == profile && !ProfileStore(this).isChild() && activeService === this

    internal fun playChartQueue(tracks: List<ChartTrack>, index: Int, profile: String, drive: ChartDriveSource?) {
        if (player == null || index !in tracks.indices) return
        cancelSelection(); val serial = selectionSerial
        selectionBusy = true
        val generation = chartGeneration + serial + 1L
        val selectionDeadline = android.os.SystemClock.elapsedRealtime() + 90_000L
        val items = tracks.mapIndexed { position, track -> chartItem(track, generation, position) }
        fun attempt(position: Int, failed: Set<Int>, retry: Boolean = false, excluded: Set<String> = emptySet()) {
            if (!selectionValid(serial, profile)) return
            val item = items[position]
            selectionTitle = item.mediaMetadata.title.toString(); selectionArtist = item.mediaMetadata.artist.toString()
            selectionMessage = "Kaynak hazırlanıyor"
            fun unavailable() {
                if (!selectionValid(serial, profile)) return
                val failures = failed + position
                if (tracks.size > 1 && position + 1 < tracks.size && android.os.SystemClock.elapsedRealtime() < selectionDeadline) attempt(position + 1, failures)
                else { selectionBusy = false; selectionMessage = "Şimdilik kullanılamıyor · daha sonra tekrar denenecek" }
            }
            prepareTrack(item, drive, true, retry, excluded) { result ->
                if (!selectionValid(serial, profile)) return@prepareTrack
                if (result == null) { unavailable(); return@prepareTrack }
                rememberHeaders(result)
                val old = player ?: return@prepareTrack
                // Yeni kayıt gerçekten READY olmadan mevcut oynatıcıya dokunma.
                val warm = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
                    .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()
                warmingPlayer = warm; warm.volume = 0f
                warm.playbackParameters = old.playbackParameters
                if (old.audioSessionId > 0) warm.setAudioSessionId(old.audioSessionId)
                val readyExtras = android.os.Bundle(item.mediaMetadata.extras ?: android.os.Bundle()).apply { putBoolean("chartUnavailable", false) }
                val ready = result.item.buildUpon().setMediaMetadata(result.item.mediaMetadata.buildUpon().setExtras(readyExtras).build()).build()
                val sources = items.mapIndexed { n, pending ->
                    if (n == position) mediaSourceFactory.createMediaSource(ready) else {
                        val extras = android.os.Bundle(pending.mediaMetadata.extras ?: android.os.Bundle()).apply { putBoolean("chartUnavailable", n in failed) }
                        placeholder(pending.buildUpon().setMediaMetadata(pending.mediaMetadata.buildUpon().setExtras(extras).build()).build())
                    }
                }
                var finished = false
                lateinit var listener: Player.Listener
                fun failedWarmup() {
                    if (finished) return; finished = true
                    warmingTimeout?.let(timerHandler::removeCallbacks); warmingTimeout = null
                    warm.removeListener(listener); warm.release(); if (warmingPlayer === warm) warmingPlayer = null
                    if (!selectionValid(serial, profile)) return
                    if (!retry) attempt(position, failed, true, excluded + ready.mediaId) else unavailable()
                }
                listener = object : Player.Listener {
                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        val coverKey = warm.currentMediaItem?.mediaMetadata?.extras?.getString(com.example.CoverArtStore.EXTRA) ?: return
                        com.example.CoverArtStore.embedded(this@PlaybackService, coverKey, metadata.artworkData)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_READY || finished) return
                        if (!selectionValid(serial, profile)) { failedWarmup(); return }
                        finished = true; warmingTimeout?.let(timerHandler::removeCallbacks); warmingTimeout = null
                        warm.removeListener(this); warmingPlayer = null
                        accountListening(); saveResume(); restoreVolume()
                        val volume = old.volume
                        old.removeListener(playbackListener); old.pause()
                        chartGeneration = generation; chartProfile = profile; chartDrive = drive
                        preparingSlots.clear(); retriedSlots.clear()
                        selectionBusy = false; selectionMessage = ""; countedItem = false
                        player = warm; warm.volume = volume
                        warm.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                        warm.addListener(playbackListener)
                        mediaSession?.setPlayer(warm); old.release()
                        resumeProfileId = profile; warm.play(); prefetchUpcoming()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failedWarmup() }
                }
                warm.addListener(listener)
                warmingTimeout = Runnable { failedWarmup() }.also { timerHandler.postDelayed(it, 20_000L) }
                warm.setMediaSources(sources, position, 0L); warm.prepare()
            }
        }
        attempt(index, emptySet())
    }
    private fun slot(item: MediaItem?) = item?.mediaMetadata?.extras?.getString("chartSlot").orEmpty()
    private fun unavailable(item: MediaItem) = item.mediaMetadata.extras?.getBoolean("chartUnavailable") == true
    private fun slotIndex(p: ExoPlayer, id: String) = (0 until p.mediaItemCount).firstOrNull { slot(p.getMediaItemAt(it)) == id }
    private fun replaceSlot(p: ExoPlayer, index: Int, item: MediaItem, real: Boolean) {
        val current = p.currentMediaItemIndex == index
        val continuePlaying = p.playWhenReady
        ownChartChange = true
        try {
            p.addMediaSource(index + 1, if (real) mediaSourceFactory.createMediaSource(item) else placeholder(item))
            if (current) p.seekTo(index + 1, 0L)
            p.removeMediaItem(index)
            if (current && real) { p.prepare(); if (continuePlaying) p.play() }
        } finally { ownChartChange = false }
        timerHandler.post { if (player === p) { loadCurrentChartTrack(); prefetchUpcoming() } }
    }
    private fun markUnavailable(p: ExoPlayer, index: Int) {
        val item = p.getMediaItemAt(index)
        val extras = android.os.Bundle(item.mediaMetadata.extras ?: android.os.Bundle()).apply { putBoolean("chartUnavailable", true) }
        val pending = item.buildUpon().setMediaId(slot(item)).setUri(null as android.net.Uri?)
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
        replaceSlot(p, index, pending, false)
        if (p.currentMediaItemIndex == index && p.playWhenReady) skipChartTrack(p)
    }
    private fun resolveSlot(p: ExoPlayer, item: MediaItem, retry: Boolean = false) {
        val id = slot(item); if (id.isBlank() || !preparingSlots.add(id)) return
        val generation = chartGeneration; val drive = chartDrive
        prepareTrack(item, drive, p.currentMediaItem?.let(::slot) == id, retry, if (retry) setOf(item.mediaId) else emptySet()) { result ->
            preparingSlots.remove(id)
            if (player !== p || chartGeneration != generation || ProfileStore(this).activeId() != chartProfile) return@prepareTrack
            val index = slotIndex(p, id) ?: return@prepareTrack
            if (result == null) { markUnavailable(p, index); return@prepareTrack }
            rememberHeaders(result)
            val extras = android.os.Bundle(item.mediaMetadata.extras ?: android.os.Bundle()).apply { putBoolean("chartUnavailable", false) }
            val ready = result.item.buildUpon().setMediaMetadata(result.item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
            replaceSlot(p, index, ready, true)
        }
    }
    private fun loadCurrentChartTrack() {
        val p = player ?: return; val item = p.currentMediaItem ?: return
        if (!p.playWhenReady || !item.mediaId.startsWith("chart-pending:")) return
        if (ProfileStore(this).isChild() || ProfileStore(this).activeId() != chartProfile) { p.pause(); return }
        if (unavailable(item)) skipChartTrack(p) else resolveSlot(p, item)
    }
    private fun prefetchUpcoming() {
        val p = player ?: return
        if (closing || ownChartChange || !p.playWhenReady || slot(p.currentMediaItem).isBlank() || ProfileStore(this).activeId() != chartProfile || ProfileStore(this).isChild()) return
        val indices = ((p.currentMediaItemIndex + 1) until minOf(p.mediaItemCount, p.currentMediaItemIndex + 5)).filter { !unavailable(p.getMediaItemAt(it)) }.take(2)
        indices.forEach { index -> val item = p.getMediaItemAt(index); if (item.mediaId.startsWith("chart-pending:")) resolveSlot(p, item) }
    }
    private fun skipChartTrack(p: ExoPlayer) {
        if (!p.playWhenReady) return
        val following = ((p.currentMediaItemIndex + 1) until p.mediaItemCount).filter { !unavailable(p.getMediaItemAt(it)) }
        val ready = following.firstOrNull { !p.getMediaItemAt(it).mediaId.startsWith("chart-pending:") }
        val next = ready ?: following.firstOrNull()
        ownChartChange = true
        try { if (next == null) p.pause() else { p.seekToDefaultPosition(next); p.prepare() } }
        finally { ownChartChange = false }
    }
    internal fun retryQueuedChart(index: Int): Boolean {
        val p = player ?: return false
        if (index !in 0 until p.mediaItemCount) return false
        val item = p.getMediaItemAt(index)
        if (!item.mediaId.startsWith("chart-pending:") && !unavailable(item)) return false
        if (slot(item).isBlank()) return false
        val tracks = (0 until p.mediaItemCount).map { n -> val t = p.getMediaItemAt(n); val extras = t.mediaMetadata.extras
            ChartTrack(t.mediaMetadata.title.toString(), t.mediaMetadata.artist.toString(), extras?.getLong("chartDuration") ?: 0L,
                extras?.getString("chartGenre").orEmpty(), extras?.getString("chartSourceId").orEmpty()) }
        playChartQueue(tracks, index, chartProfile, chartDrive); return true
    }

    private var mediaSession: MediaSession? = null
    private var resumeProfileId: String? = null
    var familyBlockMessage: String = ""
        private set
    private var checkingFamily = false
    private var familyTickAt = 0L
    private var listeningProfile: String? = null
    private var wasPlaying = false
    private fun checkFamilyPlayback(): Boolean {
        val p = player ?: return false
        if (checkingFamily || p.currentMediaItem == null) return true
        val family = com.example.ProfileStore(this)
        val item = p.currentMediaItem!!
        val message = family.playbackBlockReason(item.mediaId)
        if (message != null && family.isChild()) family.requestReview(family.activeId(), org.json.JSONObject()
            .put("id", item.mediaId).put("title", item.mediaMetadata.title?.toString().orEmpty())
            .put("artist", item.mediaMetadata.artist?.toString().orEmpty()).toString())
        if (message != null) {
            familyBlockMessage = message
            checkingFamily = true
            try { p.pause(); p.clearMediaItems() } finally { checkingFamily = false }
            return false
        }
        familyBlockMessage = ""
        return true
    }
    private fun accountListening() {
        val now = android.os.SystemClock.elapsedRealtime()
        val store = com.example.ProfileStore(this)
        val id = store.activeId()
        if (wasPlaying && listeningProfile == id && familyTickAt > 0) store.recordListening(id, now - familyTickAt)
        familyTickAt = now; listeningProfile = id; wasPlaying = player?.isPlaying == true && player?.currentMediaItem?.mediaId?.startsWith("chart-pending:") != true
    }
    private val familyTicker = object : Runnable {
        override fun run() {
            accountListening()
            checkFamilyPlayback()
            timerHandler.postDelayed(this, 1000L)
        }
    }


    private fun saveResume() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        if (item.mediaId.isBlank() || item.mediaId.startsWith("chart-pending:")) return
        val position = if (p.playbackState == androidx.media3.common.Player.STATE_ENDED) 0L else p.currentPosition.coerceAtLeast(0L)
        val saved = org.json.JSONObject().put("id", item.mediaId)
            .put("title", item.mediaMetadata.title?.toString().orEmpty())
            .put("artist", item.mediaMetadata.artist?.toString().orEmpty())
            .put("artwork", com.example.CoverArtStore(this).webArtwork(item.mediaMetadata))
            .put("feed", item.mediaMetadata.extras?.getString("feed").orEmpty())
            .put("position", position).put("duration", p.duration.coerceAtLeast(0L))
            .put("updatedAt", System.currentTimeMillis())
        com.example.PlaybackResumeStore(this).save(resumeProfileId ?: com.example.ProfileStore(this).activeId(), saved)
    }

    private val resumeTicker = object : Runnable {
        override fun run() {
            if (player?.isPlaying == true) saveResume()
            prefetchUpcoming()
            timerHandler.postDelayed(this, 5000L)
        }
    }


    private val playbackListener = object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!ownChartChange && !closing) { loadCurrentChartTrack(); prefetchUpcoming() }
            }
            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                val key = player?.currentMediaItem?.mediaMetadata?.extras?.getString(com.example.CoverArtStore.EXTRA) ?: return
                com.example.CoverArtStore.embedded(this@PlaybackService, key, metadata.artworkData)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                accountListening()
                if (isPlaying && !checkFamilyPlayback()) return
                if (!isPlaying) { saveResume(); return }
                val item = player?.currentMediaItem ?: return
                if (item.mediaId.startsWith("chart-pending:")) return
                if (!countedItem) {
                    com.example.MusicListeningStats(this@PlaybackService).record(item.mediaId, item.mediaMetadata.extras?.getString("chartGenre"))
                    countedItem = true
                }
                if (item.mediaId.startsWith("experimental:") && !com.example.ProfileStore(this@PlaybackService).isChild()) {
                    com.example.data.drive.AutomaticDriveArchive.enqueue(
                        this@PlaybackService, item.mediaId.removePrefix("experimental:"),
                        item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty()
                    )
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val id = player?.currentMediaItem?.mediaId.orEmpty()
                val p = player ?: return
                val item = p.currentMediaItem
                val tag = slot(item)
                if (item != null && tag.isNotBlank()) {
                    timerHandler.post {
                        if (p.currentMediaItem == item && p.playerError == error) {
                            if (retriedSlots.add(tag)) resolveSlot(p, item, true)
                            else slotIndex(p, tag)?.let { markUnavailable(p, it) }
                        }
                    }
                }
                if (id.startsWith("experimental:")) {
                    com.example.data.source.ExperimentalAudioResolver.invalidate(
                        "https://www.youtube.com/watch?v=" + id.removePrefix("experimental:")
                    )
                }
            }
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                if (selectionBusy && !ownChartChange && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) cancelSelection()
                if (item != null) resumeProfileId = com.example.ProfileStore(this@PlaybackService).activeId()
                countedItem = false
                if (!checkFamilyPlayback()) return
                if (sleepAtEnd && (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) stopPlayback.run()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && selectionBusy && !ownChartChange) cancelSelection()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (sleepAtEnd && state == androidx.media3.common.Player.STATE_ENDED) stopPlayback.run()
            }
        }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        val resolver = androidx.media3.datasource.ResolvingDataSource.Resolver { spec ->
            val headers = experimentalHeaders[spec.uri.toString()] ?: chartHeaders[spec.uri.toString()]
            if (headers == null) spec else spec.withAdditionalHeaders(headers)
        }
        val dataSource = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            androidx.media3.datasource.ResolvingDataSource.Factory(
                androidx.media3.datasource.DefaultHttpDataSource.Factory(), resolver
            )
        )
        mediaSourceFactory = DefaultMediaSourceFactory(dataSource)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player!!.addListener(playbackListener)
        timerHandler.post(resumeTicker)
        timerHandler.post(fadeTicker)
        timerHandler.post(familyTicker)
        // Uygulamaya dönüş ve kilit ekranı aynı MediaSession'ı kullanır.
        val intent = android.content.Intent(this, com.example.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val activity = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSession.Builder(this, player!!).setSessionActivity(activity).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        closing = true
        cancelSelection(); preparations.close()
        accountListening()
        saveResume()
        timerHandler.removeCallbacks(familyTicker)
        timerHandler.removeCallbacks(resumeTicker)
        timerHandler.removeCallbacks(fadeTicker)
        restoreVolume()
        timerHandler.removeCallbacks(stopPlayback)
        activeService = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}




