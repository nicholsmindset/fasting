package com.fastingshift.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fastingshift.data.db.FastingDatabase
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Existing users' data survives the schema upgrade (v1 → v2 adds the optional mood column). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FastingDatabase::class.java)

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
