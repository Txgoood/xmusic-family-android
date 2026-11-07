package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.drive.DriveArchiveIndex
import com.example.data.drive.DriveFolderClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveArchiveIndexTest {
    @Test fun uploadedTrackIsImmediatelySearchableAndPreservedOnScan() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("xmusic_drive_index.db")
        DriveArchiveIndex(context).use { index ->
            val scope = DriveArchiveIndex.scope("account", "folder")
            index.addUploaded(scope, "file123", "Deneme.m4a", "abcdefghijk", "Deneme", "Sanatçı", "folder")
            assertEquals("file123", index.exact(scope, "Deneme", "Sanatçı")?.optString("id"))
            assertEquals(1, index.search(scope, "Deneme").length())
            index.addUploaded(scope, "file123", "Deneme.m4a", "abcdefghijk", "Deneme", "Sanatçı", "folder")
            assertEquals(1, index.status(scope).getInt("count"))
            index.replace(scope, listOf(DriveFolderClient.Track("file123", "Deneme.m4a", "abcdefghijk", modified = "2026-11-07T00:00:00Z")))
            assertEquals("file123", index.exact(scope, "Deneme", "Sanatçı")?.optString("id"))
        }
    }
}
