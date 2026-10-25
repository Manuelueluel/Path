package it.traccia.feature.library.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.gpx.GpxImporter
import it.traccia.feature.library.RouteImporter

@Module
@InstallIn(SingletonComponent::class)
internal object LibraryModule {
    @Provides
    fun provideImporter(importer: GpxImporter): RouteImporter = RouteImporter { importer.import(it) }
}
