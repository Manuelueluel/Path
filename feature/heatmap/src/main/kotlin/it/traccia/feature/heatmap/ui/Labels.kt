package it.traccia.feature.heatmap.ui

import androidx.annotation.StringRes
import it.traccia.feature.heatmap.R

/** Italian label of a configured sport id; unknown ids (added in config) are shown as they are. */
@StringRes
fun heatmapSportLabel(id: String): Int? =
    when (id) {
        "all" -> R.string.heatmap_sport_all
        "ride" -> R.string.heatmap_sport_ride
        "run" -> R.string.heatmap_sport_run
        "trail_run" -> R.string.heatmap_sport_trail_run
        "hike" -> R.string.heatmap_sport_hike
        "mtb" -> R.string.heatmap_sport_mtb
        "gravel" -> R.string.heatmap_sport_gravel
        "winter" -> R.string.heatmap_sport_winter
        "water" -> R.string.heatmap_sport_water
        else -> null
    }

@StringRes
fun heatmapColorLabel(id: String): Int? =
    when (id) {
        "hot" -> R.string.heatmap_color_hot
        "blue" -> R.string.heatmap_color_blue
        "purple" -> R.string.heatmap_color_purple
        "gray" -> R.string.heatmap_color_gray
        "bluered" -> R.string.heatmap_color_bluered
        else -> null
    }
