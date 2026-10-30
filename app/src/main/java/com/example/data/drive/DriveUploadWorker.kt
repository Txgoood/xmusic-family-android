package com.example.data.drive

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/** Kayıtlı dosya izniyle çalışır; OAuth anahtarlarını iş verisine yazmaz. */
class DriveUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val source = inputData.getString("uri") ?: return Result.failure()
        val account = inputData.getString("account") ?: return Result.failure()
        val file = java.io.File(applicationContext.cacheDir, "drive-work-$id.audio")
        return try {
            val auth = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(
                AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
            ), 30, TimeUnit.SECONDS)
            if (auth.hasResolution() || auth.accessToken.isNullOrBlank())
                return Result.failure(workDataOf("error" to "Drive izni gerekiyor; hesabı yeniden bağla ve dosyayı tekrar seç."))
            val token = auth.accessToken!!
            val client = DriveFolderClient()
            if (client.accountId(token) != account)
                return Result.failure(workDataOf("error" to "Drive hesabı değişti; yükleme yapılmadı."))
            val uri = Uri.parse(source)
            var name = "Müzik dosyası"
            val resolver = applicationContext.contentResolver
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0).orEmpty().take(255).ifBlank { "Müzik dosyası" }
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val stream = resolver.openInputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
            stream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    if (isStopped) throw InterruptedException()
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 250L * 1024 * 1024) { "Dosya sınırı 250 MB" }
                    digest.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                }
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val (fileId, duplicate) = synchronized(archiveLock) {
                val folder = client.getOrCreate(token)
                val existing = client.findByHash(token, folder, hash)
                Pair(existing ?: DriveUploadClient().upload(token, folder, file, name,
                    resolver.getType(uri) ?: "application/octet-stream", hash).id, existing != null)
            }
            Result.success(workDataOf("name" to name, "fileId" to fileId, "duplicate" to duplicate))
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to (e.message ?: "Drive bağlantı hatası")))
        } catch (_: java.util.concurrent.TimeoutException) {
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to "Google izin isteği zaman aşımına uğradı"))
        } catch (_: Exception) {
            Result.failure(workDataOf("error" to "Yükleme yapılamadı. Dosya erişimini ve Drive hesabını kontrol et."))
        } finally { file.delete() }
    }

    companion object {
        private val archiveLock = Any()
        fun enqueue(context: Context, uri: Uri, account: String) {
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest((account + ":" + uri).toByteArray()).joinToString("") { "%02x".format(it) }
            val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                .setInputData(workDataOf("uri" to uri.toString(), "account" to account))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("xmusic-drive-upload").build()
            // Aynı dosya adresi için eşzamanlı işleri önler.
            WorkManager.getInstance(context).enqueueUniqueWork("xmusic-drive-" + key, ExistingWorkPolicy.KEEP, request)
        }
    }
}
