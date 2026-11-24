package com.fastingshift.app.di

import android.app.AlarmManager
import android.content.Context
import com.fastingshift.app.alarm.AlarmGateway
import com.fastingshift.app.alarm.AndroidAlarmGateway
import com.fastingshift.data.db.FastingDatabase
import com.fastingshift.data.repo.BackupRepository
import com.fastingshift.data.repo.LogRepository
import com.fastingshift.data.repo.ProgramRepository
import com.fastingshift.data.repo.RoomFastRepository
import com.fastingshift.data.repo.ScheduleRepository
import com.fastingshift.data.repo.SymptomCheckinRepository
import com.fastingshift.data.repo.RoomScreeningRepository
import com.fastingshift.data.repo.RoomStreakFreezeRepository
import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import com.fastingshift.domain.service.ScreeningRepository
import com.fastingshift.domain.service.StreakFreezeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** The encrypted database, in its own module so tests can replace it with an in-memory one. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): FastingDatabase = FastingDatabase.createEncrypted(context)
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    /** UTC clock. Display code converts to the device's current zone at render time. */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun fastRepository(db: FastingDatabase): FastRepository = RoomFastRepository(db.fastDao())

    @Provides @Singleton
    fun screeningRepository(db: FastingDatabase): ScreeningRepository = RoomScreeningRepository(db.screeningDao())

    @Provides @Singleton
    fun streakFreezeRepository(db: FastingDatabase): StreakFreezeRepository = RoomStreakFreezeRepository(db.streakFreezeDao())

    @Provides @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository = SettingsRepository(context)

    @Provides @Singleton
    fun logRepository(db: FastingDatabase): LogRepository = LogRepository(db.logDao())

    @Provides @Singleton
    fun scheduleRepository(db: FastingDatabase, settings: SettingsRepository, clock: Clock): ScheduleRepository =
        ScheduleRepository(db.scheduleDao(), settings, clock)

    @Provides @Singleton
    fun symptomCheckinRepository(db: FastingDatabase): SymptomCheckinRepository = SymptomCheckinRepository(db.symptomCheckinDao())

    @Provides @Singleton
    fun programRepository(db: FastingDatabase): ProgramRepository = ProgramRepository(db.programProgressDao())

    @Provides @Singleton
    fun backupRepository(db: FastingDatabase, settings: SettingsRepository): BackupRepository = BackupRepository(db, settings)

    @Provides @Singleton
    fun metricRepository(db: FastingDatabase): com.fastingshift.data.repo.MetricRepository = com.fastingshift.data.repo.MetricRepository(db.metricDao())

    @Provides @Singleton
    fun qadaRepository(db: FastingDatabase): com.fastingshift.data.repo.QadaRepository = com.fastingshift.data.repo.QadaRepository(db.qadaDao())

    @Provides @Singleton
    fun challengeRepository(db: FastingDatabase): com.fastingshift.data.repo.ChallengeRepository = com.fastingshift.data.repo.ChallengeRepository(db)

    @Provides @Singleton
    fun fastService(fasts: FastRepository, screening: ScreeningRepository, clock: Clock): FastService =
        FastService(fasts, screening, clock)

    @Provides @Singleton
    fun alarmGateway(@ApplicationContext context: Context): AlarmGateway =
        AndroidAlarmGateway(context, context.getSystemService(AlarmManager::class.java))

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
