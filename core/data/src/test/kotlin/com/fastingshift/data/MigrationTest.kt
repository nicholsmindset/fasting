package com.fastingshift.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fastingshift.data.db.FastingDatabase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Existing users' data survives the schema upgrade (v1 → v2 adds the optional mood column). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FastingDatabase::class.java)

    @Test fun v4ToV5KeepsTheRunningTimerItsConstraintAndAnExistingEnrollment() {
        val name = "friend-plan-migration"
        val active = "INSERT INTO fast(start_at,target_end_at,protocol_id,status) VALUES (1000,2000,'window_16_8','ACTIVE')"
        helper.createDatabase(name, 4).use { db ->
            FastingDatabase.Constraints.onCreate(db)
            db.execSQL(active)
            db.execSQL(
                "INSERT INTO challenge(id,type,protocol_id,start_date,length_days,name,joined_at) " +
                    "VALUES (7,'DAILY_PROTOCOL','window_16_8','2026-11-23',7,'My family',1000)",
            )
        }
        val room = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FastingDatabase::class.java, name)
            .addCallback(FastingDatabase.Constraints).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.writableDatabase
            db.query("SELECT name, follows_own_plan FROM challenge WHERE id = 7").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals("My family", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT start_at, target_end_at FROM fast WHERE ended_at IS NULL").use { c ->
                assertEquals(true, c.moveToFirst())
                assertEquals(1000L, c.getLong(0))
                assertEquals(2000L, c.getLong(1))
            }
            assertFailsWith<SQLiteConstraintException> { db.execSQL(active) }
        } finally {
            room.close()
        }
    }

    @Test fun v1ToV2KeepsFastsAndAddsMood() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL(
                "INSERT INTO fast (id, start_at, target_end_at, ended_at, protocol_id, status, note) " +
                    "VALUES (1, 1000, 2000, 1500, 'window_16_8', 'ENDED_EARLY', 'kept')",
            )
        }
        helper.runMigrationsAndValidate("migration-test", 2, true).use { db ->
            db.query("SELECT note, mood FROM fast WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("kept", c.getString(0))
                assertEquals(true, c.isNull(1))
            }
        }
    }

    @Test fun v3ToV4KeepsDebtAndLeavesLegacyFastsUnassigned() {
        helper.createDatabase("qada-migration", 3).use { db ->
            db.execSQL("INSERT INTO qada_debt(hijri_year, days_owed) VALUES (1446, 7)")
            db.execSQL(
                "INSERT INTO fast(id, start_at, target_end_at, ended_at, protocol_id, status, kind) " +
                    "VALUES (1, 1000, 2000, 2000, 'window_16_8', 'COMPLETED', 'QADA')",
            )
        }
        helper.runMigrationsAndValidate("qada-migration", 4, true).use { db ->
            db.query("SELECT days_owed FROM qada_debt WHERE hijri_year = 1446").use { c ->
                c.moveToFirst()
                assertEquals(7, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM qada_repayment").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT kind FROM fast WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("QADA", c.getString(0))
            }
        }
    }

    @Test fun v2ToV3KeepsFastsAsRegularAndAddsTables() {
        helper.createDatabase("migration-test-3", 2).use { db ->
            db.execSQL(
                "INSERT INTO fast (id, start_at, target_end_at, ended_at, protocol_id, status, note, mood) " +
                    "VALUES (1, 1000, 2000, 2500, 'window_16_8', 'COMPLETED', 'kept', 4)",
            )
        }
        helper.runMigrationsAndValidate("migration-test-3", 3, true).use { db ->
            db.query("SELECT note, mood, kind FROM fast WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("kept", c.getString(0))
                assertEquals(4, c.getInt(1))
                assertEquals("REGULAR", c.getString(2))
            }
            db.query("SELECT COUNT(*) FROM log_metric").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM qada_debt").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM challenge").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
