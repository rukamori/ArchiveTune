/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import moe.rukamori.archivetune.viewmodels.AccountChannelUiModel
import moe.rukamori.archivetune.viewmodels.LoginScreenState
import moe.rukamori.archivetune.viewmodels.LoginViewModel


const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 10

private const val LOGIN_CONTEXT_SCRIPT =
    "(function(){try{var c=window.ytcfg;var y=window.yt&&window.yt.config_;var s=document.querySelectorAll('script');var v=c&&c.get&&c.get('VISITOR_DATA')||y&&y.VISITOR_DATA;var d=c&&c.get&&c.get('DATASYNC_ID')||y&&y.DATASYNC_ID;var g=c&&c.get&&c.get('GVS_PO_TOKEN')||c&&c.get&&c.get('PO_TOKEN_GVS')||y&&y.GVS_PO_TOKEN||y&&y.PO_TOKEN_GVS;for(var i=0;i<s.length&&(!v||!d||!g);i++){var x=s[i].textContent;if(!v){var vm=x.match(/[\"']VISITOR_DATA[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(vm)v=vm[1]}if(!d){var dm=x.match(/[\"'](?:DATASYNC_ID|dataSyncId)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(dm)d=dm[1]}if(!g){var gm=x.match(/[\"'](?:GVS_PO_TOKEN|PO_TOKEN_GVS)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(gm)g=gm[1]}}if(v)Android.onRetrieveVisitorData(v);if(d)Android.onRetrieveDataSyncId(d);if(g)Android.onRetrieveGvsPoToken(g)}catch(e){}})();"

private val YOUTUBE_COOKIE_URLS =
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
    onLoginComplete: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val loginSuccessMessage = stringResource(R.string.login_success)
    var webView: WebView? = null

    LaunchedEffect(screenState, loginSuccessMessage) {
        if (screenState is LoginScreenState.Success) {
            Toast.makeText(context, loginSuccessMessage, Toast.LENGTH_SHORT).show()
            if (onLoginComplete != null) {
                onLoginComplete()
            } else {
                navController.navigateUp()
            }
        }
    }

    AndroidView(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val cookieManager = CookieManager.getInstance()
                webViewClient =
                    YouTubeLoginWebViewClient(
                        cookieManager = cookieManager,
                        onCookiesCaptured = viewModel::onCookiesCaptured,
                    )
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                val loginWebView = this
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            loginWebView.post {
                                viewModel.onVisitorDataExtracted(newVisitorData)
                            }
                        }

                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncId: String?) {
                            loginWebView.post {
                                viewModel.onDataSyncIdExtracted(newDataSyncId)
                            }
                        }

                        @JavascriptInterface
                        fun onRetrieveGvsPoToken(newGvsPoToken: String?) {
                            loginWebView.post {
                                viewModel.onGvsPoTokenExtracted(newGvsPoToken)
                            }
                        }
                    },
                    "Android",
                )
                webView = this
                resetAuthWebViewSession(context, this, clearCookies = true) {
                    loadUrl(startUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGIN_URL)
                }
            }
        },
        onRelease = { releasedWebView ->
            (releasedWebView.webViewClient as? YouTubeLoginWebViewClient)?.release(releasedWebView)
            releasedWebView.removeJavascriptInterface("Android")
            releasedWebView.stopLoading()
            releasedWebView.destroy()
            if (webView === releasedWebView) {
                webView = null
            }
        },
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (onNavigateBack != null) {
                        onNavigateBack()
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (onNavigateBack != null) {
                        onNavigateBack()
                    } else {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

    BackHandler(enabled = onNavigateBack != null || webView?.canGoBack() == true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onNavigateBack?.invoke()
        }
    }

    if (screenState is LoginScreenState.ChannelSelection) {
        val channels = (screenState as LoginScreenState.ChannelSelection).channels
        ModalBottomSheet(onDismissRequest = { /* Cannot dismiss without selection here */ }) {
            Text(
                text = stringResource(R.string.youtube_channels),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(
                    items = channels,
                    key = { index, channel -> "${channel.dataSyncId}:${channel.name}:$index" },
                    contentType = { _, _ -> "channel" },
                ) { index, channel ->
                    SegmentedListItem(
                        selected = channel.isSelected,
                        onClick = { viewModel.selectChannel(channel) },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = channels.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        leadingContent = {
                            LoginChannelAvatar(
                                imageUrl = channel.thumbnailUrl,
                                fallbackIcon = painterResource(R.drawable.account),
                            )
                        },
                        trailingContent = {
                            if (channel.isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        supportingContent = {
                            val subtitle = channel.channelHandle.ifBlank { channel.byline }
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    ) {
                        Text(
                            text = channel.name,
                            fontWeight = if (channel.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginChannelAvatar(
    imageUrl: String?,
    fallbackIcon: Painter,
) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = fallbackIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private class YouTubeLoginWebViewClient(
    private val cookieManager: CookieManager,
    private val onCookiesCaptured: (String) -> Unit,
) : WebViewClient() {
    private var extractionRunnable: Runnable? = null
    private var lastCapturedCookie: String? = null

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val targetUrl = request.url.toString()
        if (!request.isForMainFrame && !targetUrl.isWebViewLoadableUrl()) return true
        return handleUrlLoading(view, targetUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean = handleUrlLoading(view, url)

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        scheduleLoginContextExtraction(view, url)
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String?,
        isReload: Boolean,
    ) {
        super.doUpdateVisitedHistory(view, url, isReload)
        scheduleLoginContextExtraction(view, url)
    }

    fun release(view: WebView) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null
    }

    private fun scheduleLoginContextExtraction(
        view: WebView,
        url: String?,
    ) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null

        if (!url.isYouTubeUrl()) return

        var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
        val runnable =
            object : Runnable {
                override fun run() {
                    if (!view.url.isYouTubeUrl()) return

                    view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
                    captureCookies(view.url)

                    remainingAttempts -= 1
                    if (remainingAttempts > 0) {
                        view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
                    }
                }
            }
        extractionRunnable = runnable
        view.post(runnable)
    }

    private fun captureCookies(currentUrl: String?) {
        val mergedCookie = mergeYouTubeCookies(cookieManager, currentUrl) ?: return
        if (!hasYouTubeLoginCookie(mergedCookie) || mergedCookie == lastCapturedCookie) return

        lastCapturedCookie = mergedCookie
        onCookiesCaptured(mergedCookie)
    }

    private fun handleUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean {
        val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
        if (targetUrl.isWebViewLoadableUrl()) return false

        targetUrl.intentWebViewUrl()?.let(view::loadUrl)
        return true
    }
}

private fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" ||
        scheme == "https" ||
        scheme == "javascript" ||
        scheme == "data" ||
        scheme == "blob"
}

private fun String.intentWebViewUrl(): String? {
    val parsedIntent =
        runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }.getOrNull()
    return sequenceOf(
        parsedIntent?.getStringExtra("browser_fallback_url"),
        parsedIntent?.dataString,
        intentUriAsHttpsUrl(),
    ).firstOrNull { it?.isHttpUrl() == true }
}

private fun String.intentUriAsHttpsUrl(): String? {
    val intentUri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    if (!intentUri.scheme.equals("intent", ignoreCase = true)) return null

    val authority = intentUri.encodedAuthority ?: return null
    val path = intentUri.encodedPath.orEmpty()
    val query = intentUri.encodedQuery?.let { "?$it" }.orEmpty()
    return "https://$authority$path$query"
}

private fun String.isHttpUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}

private fun String?.isYouTubeUrl(): Boolean {
    val host = this?.let(Uri::parse)?.host?.lowercase() ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()

    currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

    cookieManager.flush()

    candidateUrls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }

    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
    val parsed = this?.let(Uri::parse) ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

    val scheme =
        parsed.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"

    return "$scheme://$host"
}
