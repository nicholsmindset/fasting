package com.fastingshift.app.di

import com.fastingshift.data.settings.SettingsRepository
import com.fastingshift.domain.service.FastRepository
import com.fastingshift.domain.service.FastService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Access to singletons from Glance widgets and workers, which Hilt cannot inject directly. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun fastRepository(): FastRepository
    fun fastService(): FastService
    fun settingsRepository(): SettingsRepository
    fun clock(): Clock
}
