package com.fastingshift.domain

import com.fastingshift.domain.backup.Backup
import com.fastingshift.domain.backup.BackupCodec
import com.fastingshift.domain.backup.BackupFormatException
import com.fastingshift.domain.backup.CsvExport
import com.fastingshift.domain.backup.FastRecord
import com.fastingshift.domain.backup.ScheduleRecord
import com.fastingshift.domain.backup.SettingsRecord
import com.fastingshift.domain.backup.WeightRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupHardeningTest {
    private val fast = FastRecord(1, 1000, 2000, 1500, "window_16_8", "ENDED_EARLY")

    @Test fun `duplicates are rejected`() {
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, fasts = listOf(fast, fast.copy(startAt = 5000, targetEndAt = 6000, endedAt = 5500))))) }
        val w = WeightRecord(1, 1, 80.0, "KG", "MANUAL")
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, weights = listOf(w, w)))) }
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, streakFreezes = listOf("2026-11-19", "2026-11-19")))) }
    }

    @Test fun `bad versions and schedule times are rejected`() {
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, version = 0))) }
        val bad = ScheduleRecord(1, "weekly_5_2", listOf("MONDAY"), 24 * 60, true)
        assertFailsWith<BackupFormatException> { BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1, schedules = listOf(bad)))) }
    }

    @Test fun `v1 backups without settings still load, v2 carries settings`() {
        val v1 = BackupCodec.encode(Backup(exportedAt = 1, version = 1, fasts = listOf(fast)))
        assertNull(BackupCodec.decode(v1).settings)
        val v2 = Backup(exportedAt = 1, settings = SettingsRecord(hideWeight = true, milestoneHours = listOf(12, 16)))
        assertEquals(v2, BackupCodec.decode(BackupCodec.encode(v2)))
        assertEquals(4, Backup.VERSION)
    }

    @Test fun `csv neutralises formulas but keeps numbers`() {
        assertEquals("'=HYPERLINK(1)", CsvExport.escape("=HYPERLINK(1)"))
        assertEquals("'@cmd", CsvExport.escape("@cmd"))
        assertEquals("-5.5", CsvExport.escape("-5.5"))
        val files = CsvExport.files(Backup(exportedAt = 1, fasts = listOf(fast.copy(note = "=1+1"))))
        assertTrue("'=1+1" in files.getValue("fasts.csv"))
        assertTrue("protocols.csv" in files && "schedules.csv" in files)
    }
}
