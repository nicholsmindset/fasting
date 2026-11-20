package com.fastingshift.app

import android.content.Context
import androidx.room.Room
import com.fastingshift.app.di.DatabaseModule
import com.fastingshift.data.db.FastingDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** SQLCipher's native library doesn't load under Robolectric; use plain in-memory Room. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): FastingDatabase =
        Room.inMemoryDatabaseBuilder(context, FastingDatabase::class.java).allowMainThreadQueries().build()
}
