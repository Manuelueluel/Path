package it.traccia.feature.settings.about

import androidx.annotation.StringRes
import it.traccia.feature.settings.R

/** Data and software the app is built on, with the attribution their licenses ask for (M8.7). */
internal data class Credit(
    val name: String,
    @param:StringRes val role: Int,
    /** Attribution or copyright line, shown verbatim. */
    val notice: String,
    val license: String,
    val url: String,
)

internal val dataCredits =
    listOf(
        Credit(
            name = "OpenStreetMap",
            role = R.string.about_role_osm,
            notice = "© OpenStreetMap contributors",
            license = "Open Database License (ODbL) 1.0",
            url = "https://www.openstreetmap.org/copyright",
        ),
        Credit(
            name = "OpenMapTiles",
            role = R.string.about_role_openmaptiles,
            notice = "© OpenMapTiles",
            license = "CC-BY 4.0 (schema), BSD 3-Clause (code)",
            url = "https://openmaptiles.org/",
        ),
        Credit(
            name = "Copernicus DEM GLO-30",
            role = R.string.about_role_copernicus,
            notice =
                "© DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018, " +
                    "provided under COPERNICUS by the European Union and ESA; all rights reserved",
            license = "Copernicus DEM licence",
            url = "https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model",
        ),
        Credit(
            name = "Natural Earth",
            role = R.string.about_role_natural_earth,
            notice = "Made with Natural Earth",
            license = "Public domain",
            url = "https://www.naturalearthdata.com/",
        ),
        Credit(
            name = "OpenFreeMap · Mapterhorn",
            role = R.string.about_role_online,
            notice = "OpenFreeMap, © Mapterhorn",
            license = "OpenFreeMap: MIT · Mapterhorn: see attribution",
            url = "https://mapterhorn.com/attribution",
        ),
    )

internal val softwareCredits =
    listOf(
        Credit(
            name = "BRouter",
            role = R.string.about_role_brouter,
            notice = "© Arndt Brenschede and contributors",
            license = "MIT",
            url = "https://github.com/abrensch/brouter",
        ),
        Credit(
            name = "MapLibre Native",
            role = R.string.about_role_maplibre,
            notice = "© MapLibre contributors, © Mapbox",
            license = "BSD 2-Clause",
            url = "https://github.com/maplibre/maplibre-native",
        ),
        Credit(
            name = "Planetiler",
            role = R.string.about_role_planetiler,
            notice = "© Planetiler contributors",
            license = "Apache 2.0",
            url = "https://github.com/onthegomap/planetiler",
        ),
        Credit(
            name = "OkHttp",
            role = R.string.about_role_okhttp,
            notice = "© Square, Inc.",
            license = "Apache 2.0",
            url = "https://square.github.io/okhttp/",
        ),
        Credit(
            name = "Kotlin, AndroidX, Jetpack Compose, Room, Hilt",
            role = R.string.about_role_android,
            notice = "© JetBrains s.r.o., © The Android Open Source Project, © Google LLC",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx",
        ),
    )
