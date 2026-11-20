package com.fastingshift.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
    ],
    version = 1,
    exportSchema = true,
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
                .build()
        }
    }
}
