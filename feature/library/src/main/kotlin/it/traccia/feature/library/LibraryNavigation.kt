package it.traccia.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(onBack: () -> Unit) {
    composable<LibraryRoute> { LibraryScreen(onBack = onBack) }
}
