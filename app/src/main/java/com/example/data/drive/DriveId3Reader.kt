package com.example.data.drive

import java.net.HttpURLConnection
import java.net.URL

/** MP3 başlığından yalnızca küçük bir aralık okur; tüm müziği indirmez. */
object DriveId3Reader {
    private const val LIMIT = 262144
    fun read(token: String, id: String): DriveArchiveIndex.Metadata? {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        val conn = URL("https://www.googleapis.com/drive/v3/files/$id?alt=media").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000; conn.readTimeout = 3000; conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer $token"); conn.setRequestProperty("Range", "bytes=0-${LIMIT - 1}")
            conn.setRequestProperty("Accept-Encoding", "identity")
            // Aralık isteği yok sayılırsa büyük yanıt gövdesi okunmaz.
            if (conn.responseCode != 206 || !conn.getHeaderField("Content-Range").orEmpty().startsWith("bytes 0-")) return null
            val data = conn.inputStream.use { input ->
                val header = ByteArray(10); var count = 0
                while (count < header.size) { val n = input.read(header, count, header.size - count); if (n < 0) break; count += n }
                if (count < 10 || String(header, 0, 3, Charsets.US_ASCII) != "ID3") return DriveArchiveIndex.Metadata("")
                val tagSize = ((header[6].toInt() and 127) shl 21) or ((header[7].toInt() and 127) shl 14) or ((header[8].toInt() and 127) shl 7) or (header[9].toInt() and 127)
                val target = minOf(LIMIT, 10 + tagSize)
                val out = java.io.ByteArrayOutputStream(); out.write(header); val buffer = ByteArray(8192)
                while (out.size() < target) { val n = input.read(buffer, 0, minOf(buffer.size, target - out.size())); if (n < 0) break; out.write(buffer, 0, n) }
                out.toByteArray()
            }
            return parse(data)
        } finally { conn.disconnect() }
    }
    fun parse(data: ByteArray): DriveArchiveIndex.Metadata {
        fun unsigned(pos: Int) = data[pos].toInt() and 255
        fun sync(pos: Int): Int = ((unsigned(pos) and 127) shl 21) or ((unsigned(pos + 1) and 127) shl 14) or ((unsigned(pos + 2) and 127) shl 7) or (unsigned(pos + 3) and 127)
        if (data.size < 10 || String(data, 0, 3, Charsets.US_ASCII) != "ID3") return DriveArchiveIndex.Metadata("")
        val version = unsigned(3); if (version !in 2..4) return DriveArchiveIndex.Metadata("")
        val flags = unsigned(5)
        if ((flags and 0x80) != 0 || (version == 2 && (flags and 0x40) != 0)) return DriveArchiveIndex.Metadata("")
        var end = minOf(data.size, 10 + sync(6)); var pos = 10
        if ((flags and 0x40) != 0) {
            if (pos + 4 > end) return DriveArchiveIndex.Metadata("")
            val size = if (version == 4) sync(pos) else ((unsigned(pos) shl 24) or (unsigned(pos + 1) shl 16) or (unsigned(pos + 2) shl 8) or unsigned(pos + 3))
            if (size < 0 || size > end - pos) return DriveArchiveIndex.Metadata("")
            pos += if (version == 4) size else size + 4
        }
        var title = ""; var artist = ""; var album = ""
        while (pos + (if (version == 2) 6 else 10) <= end) {
            val header = if (version == 2) 6 else 10; val length = if (version == 2) 3 else 4
            val id = String(data, pos, length, Charsets.US_ASCII)
            if (!id.matches(Regex("[A-Z0-9]{3,4}"))) break
            val size = if (version == 2) (unsigned(pos + 3) shl 16) or (unsigned(pos + 4) shl 8) or unsigned(pos + 5)
                else if (version == 4) sync(pos + 4)
                else (unsigned(pos + 4) shl 24) or (unsigned(pos + 5) shl 16) or (unsigned(pos + 6) shl 8) or unsigned(pos + 7)
            if (size <= 0 || size > end - pos - header) break
            val frameFlags = if (version == 2) 0 else unsigned(pos + 9)
            if (frameFlags == 0 && id in setOf("TIT2", "TPE1", "TALB", "TT2", "TP1", "TAL")) {
                val start = pos + header; val encoding = unsigned(start)
                val charset = when (encoding) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> null }
                val value = if (charset != null && size > 1) String(data, start + 1, size - 1, charset).trim('\u0000', '\uFEFF', ' ').take(180) else ""
                when (id) { "TIT2", "TT2" -> title = value; "TPE1", "TP1" -> artist = value; else -> album = value }
            }
            pos += header + size
        }
        return DriveArchiveIndex.Metadata(title, artist, album)
    }
}
