package com.fastingshift.app

import com.fastingshift.app.trust.deleteDirectoryContents
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeletePrivateFilesTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun clearsTheReportCacheOnRepeatedWipesWithoutFollowingLinks() {
        val cache = files.newFolder("cache")
        val reports = File(cache, "shared").apply { mkdir() }
        File(reports, "doctor.pdf").writeText("sample")
        val outside = files.newFolder("user-export")
        val exported = File(outside, "copy.pdf").apply { writeText("keep") }
        Files.createSymbolicLink(File(cache, "link").toPath(), outside.toPath())

        repeat(2) { deleteDirectoryContents(cache) }

        assertTrue(cache.listFiles()!!.isEmpty())
        assertEquals("keep", exported.readText())
    }

    @Test fun treatsMissingDirectoryAsEmptyButReportsAnUnreadableDirectoryEntry() {
        val cache = File(files.root, "cache")
        deleteDirectoryContents(cache)
        cache.writeText("not a directory")
        assertFailsWith<IOException> { deleteDirectoryContents(cache) }
    }

    @Test fun leavesTheLiveSettingsFileForDataStoreToClear() {
        val store = files.newFolder("datastore")
        val settings = File(store, "settings.preferences_pb").apply { writeText("settings") }
        val widget = File(store, "widget.preferences_pb").apply { writeText("widget") }
        deleteDirectoryContents(store, keep = settings.name)
        assertEquals("settings", settings.readText())
        assertFalse(widget.exists())
    }
}
