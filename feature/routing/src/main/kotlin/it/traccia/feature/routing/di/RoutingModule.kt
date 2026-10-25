package it.traccia.feature.routing.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.routing.BRouterSegmentRouter
import it.traccia.feature.routing.RoutingOptions
import it.traccia.feature.routing.SegmentRouter

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RoutingModule {
    @Binds
    abstract fun bindRouter(impl: BRouterSegmentRouter): SegmentRouter

    @Binds
    abstract fun bindOptions(impl: BRouterSegmentRouter): RoutingOptions
}
