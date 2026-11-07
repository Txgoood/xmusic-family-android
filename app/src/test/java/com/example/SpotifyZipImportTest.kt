package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotifyZipImportTest {
    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
    @Test fun nestedExportReadsOnlySupportedFiles() {
        val result = SpotifyZipImport.read(archive(
            "Account/YourLibrary.json" to "{\"tracks\":[]}",
            "Account/StreamingHistory_music_0.json" to "[]",
            "Account/Payments.json" to "private"
        ).inputStream())
        assertEquals(2, result.length())
        assertFalse(result.has("Payments.json"))
    }
    @Test fun unsupportedArchiveIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyZipImport.read(archive("Unknown.json" to "{}").inputStream())
        }
    }
    @Test fun oversizedSkippedEntryIsBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyZipImport.read(archive("Ignore.txt" to "x".repeat(8_000_001)).inputStream())
        }
    }
}
