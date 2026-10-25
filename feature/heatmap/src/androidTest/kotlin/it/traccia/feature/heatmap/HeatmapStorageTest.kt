package it.traccia.feature.heatmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.traccia.feature.heatmap.cache.CachedTile
import it.traccia.feature.heatmap.cache.SqliteHeatmapCache
import it.traccia.feature.heatmap.cache.TileRange
import it.traccia.feature.heatmap.session.KeystoreCookieCipher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HeatmapStorageTest {
    private val dir =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "heatmap-test",
        ).apply { deleteRecursively() }
    private val cache = SqliteHeatmapCache(dir)

    @After
    fun tearDown() {
        cache.clear("hike")
        dir.deleteRecursively()
    }

    @Test
    fun storesTilesPerSportAndColorWithTheirDate() {
        cache.put("hike", "hot", 13, 4311, 2918, CachedTile(byteArrayOf(1, 2), 100))
        val tile = checkNotNull(cache.get("hike", "hot", 13, 4311, 2918))
        assertArrayEquals(byteArrayOf(1, 2), tile.data)
        assertEquals(100, tile.downloadedAtMillis)
        assertNull(cache.get("hike", "blue", 13, 4311, 2918))
        assertNull(cache.get("mtb", "hot", 13, 4311, 2918))
    }

    @Test
    fun ageAndRefreshOfAnArea() {
        cache.put("hike", "hot", 13, 4311, 2918, CachedTile(byteArrayOf(1), 100))
        cache.put("hike", "hot", 13, 4312, 2918, CachedTile(byteArrayOf(1), 300))
        cache.put("hike", "hot", 13, 4400, 2918, CachedTile(byteArrayOf(1), 50))
        val area = listOf(TileRange(13, 4310, 4313, 2917, 2919))
        val age = checkNotNull(cache.age("hike", "hot", area))
        assertEquals(100, age.oldestMillis)
        assertEquals(300, age.newestMillis)
        assertEquals(2, age.tiles)
        cache.markStale("hike", "hot", area)
        assertEquals(0, checkNotNull(cache.get("hike", "hot", 13, 4311, 2918)).downloadedAtMillis)
        assertEquals(50, checkNotNull(cache.get("hike", "hot", 13, 4400, 2918)).downloadedAtMillis)
        assertNull(cache.age("hike", "hot", area))
    }

    @Test
    fun sizesAndClear() {
        cache.put("hike", "hot", 1, 0, 0, CachedTile(ByteArray(1000), 1))
        assertTrue(checkNotNull(cache.sizes()["hike"]) > 0)
        cache.clear("hike")
        assertFalse("hike" in cache.sizes())
        assertNull(cache.get("hike", "hot", 1, 0, 0))
    }

    @Test
    fun keystoreCipherRoundTrip() {
        val cipher = KeystoreCookieCipher()
        val secret = "CloudFront-Signature=S".toByteArray()
        val encrypted = cipher.encrypt(secret)
        assertFalse(String(encrypted).contains("CloudFront"))
        assertArrayEquals(secret, cipher.decrypt(encrypted))
    }
}
