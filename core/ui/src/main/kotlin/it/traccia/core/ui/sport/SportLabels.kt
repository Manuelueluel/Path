package it.traccia.core.ui.sport

import androidx.annotation.StringRes
import it.traccia.core.common.Sport
import it.traccia.core.ui.R

@StringRes
fun Sport.label(): Int =
    when (this) {
        Sport.HIKING -> R.string.sport_hiking
        Sport.TRAIL_RUNNING -> R.string.sport_trail_running
        Sport.RUNNING -> R.string.sport_running
        Sport.ROAD_CYCLING -> R.string.sport_road_cycling
        Sport.GRAVEL -> R.string.sport_gravel
        Sport.MTB -> R.string.sport_mtb
        Sport.SKI_TOURING -> R.string.sport_ski_touring
    }
