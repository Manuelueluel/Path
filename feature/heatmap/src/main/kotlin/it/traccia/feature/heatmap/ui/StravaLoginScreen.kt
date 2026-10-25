package it.traccia.feature.heatmap.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.feature.heatmap.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StravaLoginScreen(
    onDone: () -> Unit,
    viewModel: StravaLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(uiState.isCompleted) { if (uiState.isCompleted) currentOnDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heatmap_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.heatmap_close)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.heatmap_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LoginWebView(uiState.url, viewModel::onPageStarted, viewModel::onPageFinished, Modifier.fillMaxSize())
        }
    }
}

/** Platform glue: shows [url] and reports page loads; decisions are in the ViewModel. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    url: String,
    onPageStarted: () -> Unit,
    onPageFinished: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val started by rememberUpdatedState(onPageStarted)
    val finished by rememberUpdatedState(onPageFinished)
    val webView =
        remember {
            WebView(context).apply {
                // strava.com needs JavaScript and DOM storage to log in.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: Bitmap?,
                        ) = started()

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) = finished(url.orEmpty())
                    }
            }
        }
    LaunchedEffect(url) { if (webView.url != url) webView.loadUrl(url) }
    AndroidView(factory = { webView }, modifier = modifier)
}
