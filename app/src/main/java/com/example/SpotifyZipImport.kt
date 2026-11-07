package com.example

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** ZIP yalnızca bellekte okunur, hesap ve ödeme dosyaları işlenmez. */
object SpotifyZipImport {
    fun read(input: InputStream): JSONObject {
        val files = JSONObject()
        var total = 0
        var count = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 2000) { "ZIP içinde çok fazla dosya var." }
                val name = entry.name.substringAfterLast('/')
                val supported = name == "YourLibrary.json" ||
                    Regex("Playlist\\d+\\.json").matches(name) ||
                    Regex("StreamingHistory_(music|podcast)_\\d+\\.json").matches(name)
                if (!entry.isDirectory && supported) {
                    require(!files.has(name)) { "ZIP içinde yinelenen dosya adı var." }
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = zip.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= 8_000_000) { "Desteklenen veri boyutu 8 MB sınırını aşıyor." }
                        output.write(buffer, 0, size)
                    }
                    val text = output.toString("UTF-8").removePrefix("\uFEFF")
                    files.put(name, if (name.startsWith("StreamingHistory")) JSONArray(text) else JSONObject(text))
                }
                if (!entry.isDirectory && !supported) {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = zip.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= 8_000_000) { "ZIP açılmış boyutu 8 MB sınırını aşıyor." }
                    }
                }
                zip.closeEntry()
            }
        }
        require(files.length() > 0) { "Desteklenen Spotify hesap verileri bulunamadı." }
        return files
    }
}
