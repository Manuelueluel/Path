package it.traccia.feature.library.di

import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.gpx.GpxImporter
import it.traccia.feature.gpx.backup.LibraryBackup
import it.traccia.feature.library.RouteBackup
import it.traccia.feature.library.RouteImporter

@Module
@InstallIn(SingletonComponent::class)
internal object LibraryModule {
    @Provides
    fun provideImporter(importer: GpxImporter): RouteImporter = RouteImporter { importer.import(it) }

    @Provides
    fun provideBackup(backup: LibraryBackup): RouteBackup =
        object : RouteBackup {
            override suspend fun export(uri: Uri) = backup.export(uri)

            override suspend fun restore(uri: Uri) = backup.restore(uri)
        }
}
