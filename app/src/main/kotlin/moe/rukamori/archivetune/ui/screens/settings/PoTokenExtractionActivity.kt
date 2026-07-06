/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.resetAuthWebViewSession

class PoTokenExtractionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_GVS_TOKEN = "gvs_token"
        const val EXTRA_PLAYER_TOKEN = "player_token"
        const val EXTRA_VISITOR_DATA = "visitor_data"
        const val EXTRA_ERROR = "error"

        private const val DEFAULT_EXTRACT_URL = "https://youtube.com/account"
    }

    private var activeWebView: WebView? = null
    private var extractedVisitorData: String? = null
    private var extractedGvsToken: String? = null
    private var isExtracting = false
    private var isCompletionStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl =
            intent
                .getStringExtra(EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_EXTRACT_URL

        setContent {
            PoTokenExtractionContent(targetUrl)
        }
    }

    override fun onDestroy() {
        activeWebView?.stopLoading()
        activeWebView?.loadUrl("about:blank")
        activeWebView?.destroy()
        activeWebView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PoTokenExtractionContent(targetUrl: String) {
        val context = LocalContext.current
        var webView by remember { mutableStateOf<WebView?>(null) }
        var currentUrl by remember { mutableStateOf("") }
        var showAccountDialog by rememberSaveable { mutableStateOf(true) }
        var hasStartedSession by rememberSaveable { mutableStateOf(false) }
        var initialPageLoaded by rememberSaveable { mutableStateOf(false) }

        fun normalizeHost(url: String): String =
            Uri
                .parse(url)
                .host
                .orEmpty()
                .removePrefix("www.")

        fun normalizePath(url: String): String {
            val path =
                Uri
                    .parse(url)
                    .path
                    .orEmpty()
                    .trimEnd('/')
            return if (path.isBlank()) "/" else path
        }

        fun isAtDestination(
            current: String,
            destination: String,
        ): Boolean {
            if (current.isBlank() || destination.isBlank()) return false
            val currentHost = normalizeHost(current)
            val destinationHost = normalizeHost(destination)
            if (currentHost != destinationHost) return false

            val currentPath = normalizePath(current)
            val destinationPath = normalizePath(destination)
            return currentPath == destinationPath || currentPath.startsWith("$destinationPath/")
        }

        LaunchedEffect(hasStartedSession, webView) {
            val currentWebView = webView ?: return@LaunchedEffect
            if (!hasStartedSession || initialPageLoaded) return@LaunchedEffect

            initialPageLoaded = true
            resetAuthWebViewSession(context, currentWebView) {
                currentWebView.loadUrl(targetUrl)
            }
        }

        fun parseJsResult(raw: String?): String {
            val text = raw?.trim().orEmpty()
            if (text.isBlank() || text == "null") return ""
            val unwrapped =
                if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
                    text.substring(1, text.length - 1)
                } else {
                    text
                }
            return unwrapped
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .trim()
        }

        fun closeCanceled(error: String? = null) {
            isExtracting = false
            val data =
                Intent().apply {
                    if (!error.isNullOrBlank()) {
                        putExtra(EXTRA_ERROR, error)
                    }
                }
            setResult(Activity.RESULT_CANCELED, data)
            finish()
        }

        fun completeIfReady() {
            if (isCompletionStarted || isFinishing) return
            val visitorData = extractedVisitorData?.takeIf(String::isNotBlank) ?: return
            val gvsToken = extractedGvsToken?.takeIf(String::isNotBlank) ?: return
            isCompletionStarted = true

            this@PoTokenExtractionActivity.lifecycleScope.launch {
                val playerToken =
                    BotGuardTokenGenerator
                        .mintToken("player", visitorData)
                        ?.playerToken
                        .orEmpty()
                if (playerToken.isBlank()) {
                    closeCanceled(context.getString(R.string.token_generation_failed))
                    return@launch
                }

                setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        putExtra(EXTRA_VISITOR_DATA, visitorData)
                        putExtra(EXTRA_GVS_TOKEN, gvsToken)
                        putExtra(EXTRA_PLAYER_TOKEN, playerToken)
                    },
                )
                finish()
            }
        }

        fun triggerExtraction() {
            if (isExtracting || isCompletionStarted || !isAtDestination(currentUrl, targetUrl)) return

            isExtracting = true

            extractedVisitorData = null
            extractedGvsToken = null

            webView?.evaluateJavascript(
                "(function(){try{return window.yt?.config_?.VISITOR_DATA || window.ytcfg?.get?.('VISITOR_DATA') || '';}catch(e){return '';}})();",
            ) { result ->
                val visitor = parseJsResult(result)
                if (visitor.isNotBlank()) {
                    extractedVisitorData = visitor
                    completeIfReady()
                }
            }

            webView?.evaluateJavascript(
                "(function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t)return t;}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m)return m[1];}return '';}catch(e){return '';}})();",
            ) { result ->
                val gvs = parseJsResult(result)
                if (gvs.isNotBlank()) {
                    extractedGvsToken = gvs
                    completeIfReady()
                }
            }

            webView?.postDelayed({
                if (isFinishing || isCompletionStarted) return@postDelayed
                val visitor = extractedVisitorData
                if (!visitor.isNullOrBlank() && extractedGvsToken.isNullOrBlank()) {
                    this@PoTokenExtractionActivity.lifecycleScope.launch {
                        val sessionToken =
                            BotGuardTokenGenerator
                                .mintToken("player", visitor)
                                ?.sessionToken
                                .orEmpty()
                        if (isFinishing || isCompletionStarted) return@launch
                        if (sessionToken.isNotBlank()) {
                            extractedGvsToken = sessionToken
                            completeIfReady()
                        } else {
                            closeCanceled(context.getString(R.string.token_generation_failed))
                        }
                    }
                    return@postDelayed
                }
                if (extractedVisitorData.isNullOrBlank() || extractedGvsToken.isNullOrBlank()) {
                    closeCanceled(context.getString(R.string.token_generation_failed))
                }
            }, 4000L)
        }

        BackHandler {
            val wv = webView
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                closeCanceled()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onRetrieveVisitorData(newVisitorData: String?) {
                                    if (!newVisitorData.isNullOrBlank()) {
                                        extractedVisitorData = newVisitorData
                                        runOnUiThread { completeIfReady() }
                                    }
                                }

                                @JavascriptInterface
                                fun onRetrievePoToken(newPoToken: String?) {
                                    if (!newPoToken.isNullOrBlank()) {
                                        extractedGvsToken = newPoToken
                                        runOnUiThread { completeIfReady() }
                                    }
                                }
                            },
                            "Android",
                        )

                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    currentUrl = url.orEmpty()
                                    if (isAtDestination(currentUrl, targetUrl)) {
                                        triggerExtraction()
                                    }
                                }
                            }

                        webView = this
                        activeWebView = this
                    }
                },
                update = { view ->
                    webView = view
                    activeWebView = view
                },
            )

            if (showAccountDialog) {
                AlertDialog(
                    onDismissRequest = { closeCanceled() },
                    title = {
                        Text(stringResource(R.string.po_token_account_notice_title))
                    },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.po_token_account_notice_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.po_token_account_notice_same_account),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.po_token_account_notice_mismatch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAccountDialog = false
                                hasStartedSession = true
                            },
                        ) {
                            Text(stringResource(R.string.got_it))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { closeCanceled() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            TopAppBar(
                title = { Text(stringResource(R.string.extracting_from_url)) },
                navigationIcon = {
                    IconButton(
                        onClick = { closeCanceled() },
                        onLongClick = { closeCanceled() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )

        }
    }
}
