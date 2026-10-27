package it.traccia.feature.settings.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import it.traccia.core.ui.theme.TracciaTheme
import it.traccia.feature.settings.R

@Composable
internal fun OnboardingScreen(
    onExit: (OnboardingExit) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingContent { exit ->
        viewModel.complete()
        onExit(exit)
    }
}

@Composable
private fun OnboardingContent(onExit: (OnboardingExit) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge)
            Step(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.onboarding_region_title),
                text = stringResource(R.string.onboarding_region_text),
            ) {
                Button(onClick = { onExit(OnboardingExit.OFFLINE_MAPS) }) { Text(stringResource(R.string.onboarding_region_action)) }
            }
            Step(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.onboarding_strava_title),
                text = stringResource(R.string.onboarding_strava_text),
            ) {
                OutlinedButton(onClick = { onExit(OnboardingExit.STRAVA_LOGIN) }) {
                    Text(stringResource(R.string.onboarding_strava_action))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onExit(OnboardingExit.MAP) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun Step(
    icon: ImageVector,
    title: String,
    text: String,
    action: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}

@Preview
@Composable
private fun OnboardingPreview() {
    TracciaTheme { OnboardingContent {} }
}
