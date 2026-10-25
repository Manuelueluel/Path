package it.traccia.feature.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests to the planner from other screens (library, GPX import): "edit this saved route".
 * A pending request survives until the planner consumes it.
 */
@Singleton
class PlannerCommands
    @Inject
    constructor() {
        private val _openRoute = MutableStateFlow<Long?>(null)
        val openRoute: StateFlow<Long?> = _openRoute.asStateFlow()

        fun open(routeId: Long) {
            _openRoute.value = routeId
        }

        fun consumed(routeId: Long) {
            _openRoute.compareAndSet(routeId, null)
        }
    }
