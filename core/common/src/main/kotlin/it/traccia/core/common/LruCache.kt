package it.traccia.core.common

/** Small thread-safe LRU cache, usable from pure Kotlin modules and unit tests. */
class LruCache<K : Any, V : Any>(
    private val maxSize: Int,
) {
    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    private val map = LinkedHashMap<K, V>(maxSize, LOAD_FACTOR, true)

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        map[key] = value
        if (map.size > maxSize) map.remove(map.keys.first())
    }

    @Synchronized
    fun clear() = map.clear()

    val size: Int
        @Synchronized get() = map.size

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
