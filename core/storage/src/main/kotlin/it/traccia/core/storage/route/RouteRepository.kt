package it.traccia.core.storage.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Saved routes, shared by planner, library and GPX import/export. */
interface RouteRepository {
    val summaries: Flow<List<RouteSummary>>

    /** Routes the user chose to show together on the map (M7.9). */
    val shownOnMap: StateFlow<Set<Long>>

    val shownRoutes: Flow<List<Route>>

    suspend fun get(id: Long): Route?

    /** Inserts ([Route.id] = 0) or updates; returns the id. */
    suspend fun save(route: Route): Long

    suspend fun rename(
        id: Long,
        name: String,
    )

    suspend fun duplicate(
        id: Long,
        name: String,
    ): Long?

    suspend fun delete(id: Long)

    fun setShownOnMap(ids: Set<Long>)
}

@Singleton
class RoomRouteRepository
    @Inject
    constructor(
        private val dao: RouteDao,
    ) : RouteRepository {
        private val clock: () -> Long = System::currentTimeMillis
        private val shown = MutableStateFlow<Set<Long>>(emptySet())

        override val summaries: Flow<List<RouteSummary>> =
            dao.observeAll().map { list -> list.map(RouteCodec::toSummary) }.flowOn(Dispatchers.Default)

        override val shownOnMap: StateFlow<Set<Long>> = shown.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        override val shownRoutes: Flow<List<Route>> =
            shown
                .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else dao.observeByIds(ids) }
                .map { list -> list.map(RouteCodec::toRoute) }
                .flowOn(Dispatchers.Default)

        override suspend fun get(id: Long): Route? = dao.findById(id)?.let { withContext(Dispatchers.Default) { RouteCodec.toRoute(it) } }

        override suspend fun save(route: Route): Long {
            val now = clock()
            val stamped = route.copy(createdAtMillis = route.createdAtMillis.takeIf { it > 0 } ?: now, updatedAtMillis = now)
            val entity = withContext(Dispatchers.Default) { RouteCodec.toEntity(stamped) }
            val id = dao.upsert(entity)
            // Upsert returns -1 when it updated an existing row.
            return if (id == -1L) route.id else id
        }

        override suspend fun rename(
            id: Long,
            name: String,
        ) = dao.rename(id, name, clock())

        override suspend fun duplicate(
            id: Long,
            name: String,
        ): Long? = get(id)?.let { save(it.copy(id = 0, name = name, createdAtMillis = 0)) }

        override suspend fun delete(id: Long) {
            dao.deleteById(id)
            shown.update { it - id }
        }

        override fun setShownOnMap(ids: Set<Long>) {
            shown.value = ids
        }
    }
