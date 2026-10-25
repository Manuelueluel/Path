package it.traccia.feature.heatmap

import it.traccia.feature.heatmap.config.HeatmapConfig
import java.io.File

/** The bundled configuration, so tests break if the asset becomes invalid. */
internal val bundledConfig: HeatmapConfig by lazy { HeatmapConfig.parse(File("src/main/assets/heatmap.json").readText()) }
