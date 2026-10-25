package it.traccia.core.storage.offline

/** A tile read from an installed region pack. */
class LocalTile(
    val data: ByteArray,
    /** True when the pack covers the whole tile; low-zoom tiles are usually only partially covered. */
    val coversTile: Boolean,
)

/** Read access to the tiles of the installed offline regions. */
interface LocalTileSource {
    /** @param layer pack file type: `basemap`, `trails` or `dem`. */
    fun find(
        layer: String,
        z: Int,
        x: Int,
        y: Int,
    ): LocalTile?
}
