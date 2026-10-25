package it.traccia.feature.routing

import it.traccia.core.common.Sport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Sport to BRouter profile mapping, from `assets/routing_profiles.json`. */
@Serializable
data class RoutingProfiles(
    val profiles: Map<String, Profile>,
) {
    @Serializable
    data class Profile(
        /** Profile file name in `assets/brouter`, without `.brf`. */
        val brf: String,
        /** Fixed parameters of this sport. */
        val params: Map<String, String> = emptyMap(),
        /** User options (M4.4) and the profile parameters they set. */
        val options: Map<String, Map<String, String>> = emptyMap(),
    )

    fun profile(sport: Sport): Profile = profiles[sport.name] ?: profiles.getValue(Sport.HIKING.name)

    /** Options the user can toggle for [sport]. */
    fun optionsFor(sport: Sport): List<String> = profile(sport).options.keys.toList()

    /** Parameters passed to BRouter for [sport] with the [enabled] options. */
    fun parameters(
        sport: Sport,
        enabled: Set<String>,
    ): Map<String, String> {
        val profile = profile(sport)
        return profile.params + enabled.flatMap { profile.options[it].orEmpty().entries }.associate { it.key to it.value }
    }

    companion object {
        const val ASSET = "routing_profiles.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RoutingProfiles = json.decodeFromString(serializer(), text)
    }
}
