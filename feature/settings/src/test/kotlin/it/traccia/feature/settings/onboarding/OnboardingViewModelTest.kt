package it.traccia.feature.settings.onboarding

import it.traccia.core.storage.prefs.InMemoryUserPreferences
import it.traccia.feature.settings.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OnboardingViewModelTest {
    @RegisterExtension
    val main = MainDispatcherExtension()

    @Test
    fun `leaving the onboarding marks it as done`() {
        val preferences = InMemoryUserPreferences()
        OnboardingViewModel(preferences).complete()
        assertTrue(preferences.preferences.value.onboardingDone)
    }
}
