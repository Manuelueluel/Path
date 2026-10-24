package it.traccia.core.storage

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.traccia.core.common.Sport
import it.traccia.core.storage.route.RouteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDaoTest {
    private lateinit var db: TracciaDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TracciaDatabase::class.java)
                .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertAndReadBack() =
        runTest {
            val id = db.routeDao().upsert(route("Anello del Resegone", updatedAt = 1))
            assertEquals("Anello del Resegone", db.routeDao().findById(id)?.name)
        }

    @Test
    fun routesAreOrderedByLastUpdate() =
        runTest {
            db.routeDao().upsert(route("Vecchio", updatedAt = 1))
            db.routeDao().upsert(route("Nuovo", updatedAt = 2))
            assertEquals(
                listOf("Nuovo", "Vecchio"),
                db
                    .routeDao()
                    .observeAll()
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun deleteRemovesRoute() =
        runTest {
            val id = db.routeDao().upsert(route("Da cancellare", updatedAt = 1))
            db.routeDao().delete(route("Da cancellare", updatedAt = 1).copy(id = id))
            assertNull(db.routeDao().findById(id))
        }

    private fun route(
        name: String,
        updatedAt: Long,
    ) = RouteEntity(name = name, sport = Sport.HIKING, createdAtMillis = 0, updatedAtMillis = updatedAt)
}
