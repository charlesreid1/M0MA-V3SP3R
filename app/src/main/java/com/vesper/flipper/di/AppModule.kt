package com.vesper.flipper.di

import android.content.Context
import com.vesper.flipper.data.database.AuditDao
import com.vesper.flipper.data.database.ChatDao
import com.vesper.flipper.data.database.VesperDatabase
import com.vesper.flipper.data.database.VulnDao
import com.vesper.flipper.data.db.DeviceDao
import com.vesper.flipper.data.db.DeviceDatabase
import com.vesper.flipper.data.db.NoteDao
import com.vesper.flipper.data.db.SightingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Vesper Database ====================

    @Provides
    @Singleton
    fun provideVesperDatabase(@ApplicationContext context: Context): VesperDatabase {
        return VesperDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideAuditDao(database: VesperDatabase): AuditDao {
        return database.auditDao()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: VesperDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideVulnDao(database: VesperDatabase): VulnDao {
        return database.vulnDao()
    }

    // ==================== Device Tracker Database ====================

    @Provides
    @Singleton
    fun provideDeviceDatabase(@ApplicationContext context: Context): DeviceDatabase {
        return DeviceDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideDeviceDao(database: DeviceDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    @Singleton
    fun provideSightingDao(database: DeviceDatabase): SightingDao {
        return database.sightingDao()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: DeviceDatabase): NoteDao {
        return database.noteDao()
    }
}
