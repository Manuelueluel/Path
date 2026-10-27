package it.traccia.feature.settings.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.storage.prefs.UserPreferencesRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the user goes when leaving the onboarding. */
enum class OnboardingExit { MAP, OFFLINE_MAPS, STRAVA_LOGIN }

/** First-run screen (M8.3): shown once, whatever the user picks. */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val preferences: UserPreferencesRepository,
    ) : ViewModel() {
        fun complete() {
            viewModelScope.launch { preferences.update { it.copy(onboardingDone = true) } }
        }
    }
