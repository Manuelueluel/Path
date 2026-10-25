package it.traccia.feature.heatmap.tiles

/** What to do with a heatmap tile request, before touching the network. */
enum class TileAction {
    /** Cached copy is recent enough: serve it, no network. */
    SERVE_CACHE,

    /** Ask Strava (cache, if any, is the fallback). */
    FETCH,

    /** No usable session: serve the cache if present, nothing otherwise. */
    CACHE_ONLY,
}

/**
 * Cache-first policy (M3.8): a tile is downloaded only when it is missing or older than the
 * refresh threshold, and only with a valid session.
 */
fun decideTileAction(
    cachedAtMillis: Long?,
    nowMillis: Long,
    refreshAfterMillis: Long,
    hasSession: Boolean,
): TileAction =
    when {
        cachedAtMillis != null && nowMillis - cachedAtMillis < refreshAfterMillis -> TileAction.SERVE_CACHE
        hasSession -> TileAction.FETCH
        else -> TileAction.CACHE_ONLY
    }
