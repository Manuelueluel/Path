package it.traccia.feature.planner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.traccia.feature.planner.draft.DraftStore
import it.traccia.feature.planner.draft.FileDraftStore

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlannerModule {
    @Binds
    abstract fun bindDraftStore(impl: FileDraftStore): DraftStore
}
