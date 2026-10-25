package it.traccia.feature.map.style

/**
 * Available base map styles. [asset] is the style JSON in `assets/styles`, generated with the
 * OpenMapTiles schema; contour colors are applied at runtime because contours are computed in-app.
 */
enum class MapStyle(
    val asset: String,
    val contourColor: String,
    val contourMajorColor: String,
    val contourLabelColor: String,
    val labelHaloColor: String,
) {
    /** Full topographic map: trail difficulty, huts, water, peaks. */
    TOPO(
        asset = "styles/topo.json",
        contourColor = "#b08655",
        contourMajorColor = "#946232",
        contourLabelColor = "#7d5228",
        labelHaloColor = "#fbf9f3",
    ),

    /** Muted grayscale map, to read the heatmap on top of it. */
    MINIMAL(
        asset = "styles/minimal.json",
        contourColor = "#c6c6c2",
        contourMajorColor = "#acaca8",
        contourLabelColor = "#8a8a86",
        labelHaloColor = "#f6f6f4",
    ),
}
