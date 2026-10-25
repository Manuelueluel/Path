package it.traccia.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LruCacheTest {
    @Test
    fun `evicts the least recently used entry`() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache["a"] // touch a: b becomes the eldest
        cache.put("c", 3)
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }
}
