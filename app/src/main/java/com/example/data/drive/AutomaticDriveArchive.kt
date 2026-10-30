package com.example.data.drive

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.util.concurrent.TimeUnit

/** Çalma başlamasını engellemeden seçilmiş kaydı arşivler. */
class AutomaticDriveArchive(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val source = inputData.getString("source") ?: return Result.failure()
        val account = inputData.getString("account") ?: return Result.failure()
        if (!source.matches(Regex("[A-Za-z0-9_-]{11}"))) return Result.failure()
        val name = inputData.getString("title").orEmpty().take(180).ifBlank { source } + ".m4a"
        val prefs = applicationContext.getSharedPreferences("xmusic_auto_archive", Context.MODE_PRIVATE)
        val dir = java.io.File(applicationContext.cacheDir, "archive-$id")
        return try {
            if (!prefs.getBoolean("enabled", true)) return Result.success()
            val auth = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(
                AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
            ), 30, TimeUnit.SECONDS)
            val token = auth.accessToken
            if (auth.hasResolution() || token.isNullOrBlank()) return Result.failure(workDataOf("error" to "Drive hesabını yeniden bağla."))
            val client = DriveFolderClient()
            if (client.accountId(token) != account) return Result.failure(workDataOf("error" to "Drive hesabı değişti; arşivlenmedi."))
            synchronized(downloadLock) {
                val folder = client.getOrCreate(token)
                val existing = client.findBySource(token, folder, source)
                if (existing != null) {
                    prefs.edit().putString(account + ":" + source, existing).commit()
                    return Result.success(workDataOf("name" to name, "duplicate" to true))
                }
                dir.mkdirs()
                val engine = YoutubeDL.getInstance()
                engine.init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$source")
                request.addOption("--no-playlist")
                request.addOption("--match-filter", "!is_live")
                request.addOption("--extractor-args", "youtube:player_client=web_safari")
                request.addOption("--socket-timeout", "15")
                request.addOption("--retries", "1")
                request.addOption("--fragment-retries", "1")
                request.addOption("--max-filesize", "250M")
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "m4a")
                request.addOption("-o", java.io.File(dir, "audio.%(ext)s").absolutePath)
                engine.execute(request, id.toString()) { _, _, _ -> }
                if (isStopped) return Result.failure()
                val audio = java.io.File(dir, "audio.m4a")
                require(audio.isFile && audio.length() in 1..(250L * 1024 * 1024)) { "Ses dosyası hazırlanamadı." }
                val file = DriveUploadClient().upload(token, folder, audio, name, "audio/mp4", youtubeId = source)
                prefs.edit().putString(account + ":" + source, file.id).commit()
                Result.success(workDataOf("name" to name, "fileId" to file.id))
            }
        } catch (e: Exception) {
            val detail = (e.message ?: e.javaClass.simpleName).replace(Regex("https?://\\S+"), "[bağlantı]").take(600)
            if (e is java.io.IOException && runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf("name" to name, "error" to detail))
        } finally { dir.deleteRecursively() }
    }

    override fun onStopped() {
        super.onStopped()
        try { YoutubeDL.getInstance().destroyProcessById(id.toString()) } catch (_: Exception) { }
    }

    companion object {
        private val downloadLock = Any()
        fun enqueue(context: Context, source: String, title: String) {
            val prefs = context.getSharedPreferences("xmusic_auto_archive", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("enabled", true)) return
            val account = prefs.getString("account", null) ?: return
            if (prefs.contains(account + ":" + source)) return
            val work = OneTimeWorkRequestBuilder<AutomaticDriveArchive>()
                .setInputData(workDataOf("source" to source, "title" to title, "account" to account))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("xmusic-drive-upload").addTag("xmusic-auto-archive").build()
            WorkManager.getInstance(context).enqueueUniqueWork("auto-drive-$account-$source", ExistingWorkPolicy.KEEP, work)
        }
    }
}
