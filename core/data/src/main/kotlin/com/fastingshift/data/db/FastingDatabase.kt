package com.fastingshift.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.nio.file.Files

@Database(
    entities = [
        FastEntity::class,
        ProtocolEntity::class,
        ScheduleEntity::class,
        WeightLogEntity::class,
        WaterLogEntity::class,
        ElectrolyteLogEntity::class,
        SymptomCheckinEntity::class,
        ScreeningEntity::class,
        ProgramProgressEntity::class,
        StreakFreezeEntity::class,
        MetricLogEntity::class,
        QadaDebtEntity::class,
        QadaRepaymentEntity::class,
        ChallengeEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(Converters::class)
abstract class FastingDatabase : RoomDatabase() {
    abstract fun fastDao(): FastDao
    abstract fun screeningDao(): ScreeningDao
    abstract fun streakFreezeDao(): StreakFreezeDao
    abstract fun logDao(): LogDao
    abstract fun symptomCheckinDao(): SymptomCheckinDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun programProgressDao(): ProgramProgressDao
    abstract fun backupDao(): BackupDao
    abstract fun metricDao(): MetricDao
    abstract fun qadaDao(): QadaDao
    abstract fun challengeDao(): ChallengeDao

    companion object {
        const val NAME = "fasting.db"

        /**
         * Opens the encrypted database (SR-9: health data encrypted at rest). The passphrase is
         * generated on first launch and wrapped by a key in the Android Keystore.
         */
        fun createEncrypted(context: Context): FastingDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyProvider(context).getOrCreatePassphrase()
            return Room.databaseBuilder(context, FastingDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addCallback(Constraints)
                .build()
        }

        /** Whether the encrypted database can be opened, without opening it (SR-9 recovery). */
        fun keyStatus(context: Context): DatabaseKeyProvider.Status = DatabaseKeyProvider(context).status(NAME)

        /** Deletes the database and its key: the user's last resort after the key is lost. */
        fun resetEncrypted(context: Context) {
            val file = context.getDatabasePath(NAME)
            context.deleteDatabase(NAME)
            // Some devices leave journal files after an interrupted deletion. Retry idempotently.
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                Files.deleteIfExists(File(file.path + suffix).toPath())
            }
            DatabaseKeyProvider(context).reset()
        }
    }

    /**
     * Rules Room annotations can't express. At most one active fast, enforced by the database
     * itself (a partial unique index), so no race between the app, widget and notification
     * actions can ever create two. Applied on create and on every open (idempotent).
     */
    object Constraints : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = apply(db)
        override fun onOpen(db: SupportSQLiteDatabase) = apply(db)
        private fun apply(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fast_single_active ON fast(status) WHERE ended_at IS NULL")
        }
    }
}
