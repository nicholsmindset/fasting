package com.fastingshift.app.export

import android.content.Context
import android.net.Uri
import com.fastingshift.data.repo.BackupRepository
import com.fastingshift.domain.backup.BackupCodec
import com.fastingshift.domain.backup.CsvExport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export and backup to user-chosen files via the Storage Access Framework (PRD F8). Nothing
 * is uploaded anywhere.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupRepository,
    private val clock: Clock,
) {
    /** Writes the full JSON backup. The same file restores everything. */
    suspend fun writeBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val json = BackupCodec.encode(backups.export(clock.instant()))
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
    }

    /** Writes a zip with backup.json plus one CSV per table, for spreadsheets. */
    suspend fun writeExportZip(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = backups.export(clock.instant())
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(BackupCodec.encode(backup).toByteArray())
                zip.closeEntry()
                CsvExport.files(backup).forEach { (name, csv) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(csv.toByteArray())
                    zip.closeEntry()
                }
            }
        }
    }

    /** Replaces all data with the backup. Throws BackupFormatException for invalid files. */
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        backups.restore(BackupCodec.decode(text), clock.instant())
    }
}
