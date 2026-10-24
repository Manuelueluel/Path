package it.traccia.core.storage.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.traccia.core.storage.TracciaDatabase
import it.traccia.core.storage.TracciaMigrations
import it.traccia.core.storage.route.RouteDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TracciaDatabase =
        Room
            .databaseBuilder(context, TracciaDatabase::class.java, TracciaDatabase.NAME)
            .addMigrations(*TracciaMigrations.ALL)
            .build()

    @Provides
    fun provideRouteDao(database: TracciaDatabase): RouteDao = database.routeDao()
}
