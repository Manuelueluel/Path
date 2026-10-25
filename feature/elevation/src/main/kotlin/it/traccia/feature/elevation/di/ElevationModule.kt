package it.traccia.feature.elevation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.elevation.DemRouteElevation
import it.traccia.feature.elevation.RouteElevation

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ElevationModule {
    @Binds
    abstract fun bindRouteElevation(impl: DemRouteElevation): RouteElevation
}
