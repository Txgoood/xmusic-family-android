package com.example.data.drive

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * İzin verilmiş yerel dosyayı kullanıcının Drive klasörüne yükler.
 * Yetkilendirme ve arka plan kuyruğu ayrı olarak bağlanmalıdır.
 */
class DriveUploadClient {
    data class UploadedFile(val id: String, val name: String)

    fun upload(accessToken: String, folderId: String, file: File, displayName: String, mimeType: String): UploadedFile {
        require(accessToken.isNotBlank() && !accessToken.contains('\n') && !accessToken.contains('\r'))
        require(folderId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        require(file.isFile && file.length() > 0)
        require(displayName.isNotBlank() && displayName.length <= 255)
        require(mimeType.matches(Regex("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")))

        val metadata = JSONObject().put("name", displayName)
            .put("parents", org.json.JSONArray().put(folderId))
        val endpoint = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name")
        val create = endpoint.openConnection() as HttpURLConnection
        val sessionUrl: String
        try {
            create.requestMethod = "POST"
            create.connectTimeout = 15000
            create.readTimeout = 30000
            create.instanceFollowRedirects = false
            create.doOutput = true
            create.setRequestProperty("Authorization", "Bearer " + accessToken)
            create.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            create.setRequestProperty("X-Upload-Content-Type", mimeType)
            create.setRequestProperty("X-Upload-Content-Length", file.length().toString())
            create.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            val code = create.responseCode
            if (code !in 200..299) throw IOException("Drive yükleme oturumu açılamadı (HTTP $code)")
            sessionUrl = create.getHeaderField("Location") ?: throw IOException("Drive yükleme adresi alınamadı")
        } finally { create.disconnect() }

        // Kimlik bilgisini yalnızca Google Drive'a gönder; sunucu yönlendirmelerini izleme.
        val url = URL(sessionUrl)
        require(url.protocol == "https" && url.host.equals("www.googleapis.com", ignoreCase = true))
        val upload = url.openConnection() as HttpURLConnection
        try {
            upload.requestMethod = "PUT"
            upload.connectTimeout = 15000
            upload.readTimeout = 60000
            upload.instanceFollowRedirects = false
            upload.doOutput = true
            upload.setRequestProperty("Authorization", "Bearer " + accessToken)
            upload.setRequestProperty("Content-Type", mimeType)
            upload.setFixedLengthStreamingMode(file.length())
            upload.outputStream.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
            val code = upload.responseCode
            if (code !in 200..299) throw IOException("Drive yüklemesi tamamlanamadı (HTTP $code)")
            val result = JSONObject(upload.inputStream.bufferedReader().use { it.readText() })
            val id = result.optString("id")
            if (id.isBlank()) throw IOException("Drive dosya kimliği dönmedi")
            return UploadedFile(id, result.optString("name", displayName))
        } finally { upload.disconnect() }
    }
}
