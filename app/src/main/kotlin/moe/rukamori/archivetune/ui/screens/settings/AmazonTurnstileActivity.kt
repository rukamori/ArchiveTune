/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiosource.AmazonAudioProvider
import moe.rukamori.archivetune.ui.component.IconButton
import timber.log.Timber

/**
 * Solves the Cloudflare Turnstile challenge that gates the Amazon Music instance and exchanges the
 * resulting token for the instance's access JWT.
 *
 * Turnstile sitekeys are domain-locked, so the widget must run on the web player's own origin
 * (monochrome.tf). We can't serve a page from that origin, but we can make the WebView *believe* it
 * is on that origin by loading our challenge HTML via [WebView.loadDataWithBaseURL] with the origin
 * as the base URL — this sets `window.location`/document origin to monochrome.tf, which is what the
 * Turnstile script validates against.
 *
 * Flow: render invisible widget -> execute -> callback returns cf_turnstile_response ->
 * [AmazonAudioProvider.exchangeTurnstileToken] -> access JWT -> returned to caller via Intent, which
 * persists it (with a ~1h expiry) for use as the X-Turnstile-JWT header on track/stream requests.
 */
class AmazonTurnstileActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INSTANCE_URL = "instance_url"
        const val EXTRA_JWT = "jwt"
        const val EXTRA_EXPIRY = "expiry"
        const val EXTRA_ERROR = "error"
    }

    private var activeWebView: WebView? = null
    private var isCompleted = false

    private val instanceUrl: String
        get() =
            intent
                .getStringExtra(EXTRA_INSTANCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?: AmazonAudioProvider.DEFAULT_INSTANCE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurnstileContent() }
    }

    override fun onDestroy() {
        activeWebView?.stopLoading()
        activeWebView?.loadUrl("about:blank")
        activeWebView?.destroy()
        activeWebView = null
        super.onDestroy()
    }

    private fun finishWithError(message: String) {
        if (isCompleted || isFinishing) return
        isCompleted = true
        Timber.tag("AmazonTurnstile").w("Turnstile authorization failed: %s", message)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private fun onTurnstileToken(cfToken: String) {
        if (isCompleted || isFinishing) return
        lifecycleScope.launch {
            val jwt = AmazonAudioProvider.exchangeTurnstileToken(cfToken, instanceUrl)
            if (isCompleted || isFinishing) return@launch
            if (jwt.isNullOrBlank()) {
                finishWithError("token-exchange-failed")
                return@launch
            }
            isCompleted = true
            val expiry =
                System.currentTimeMillis() +
                    AmazonAudioProvider.TURNSTILE_JWT_TTL_MS -
                    AmazonAudioProvider.TURNSTILE_JWT_SAFETY_MS
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(EXTRA_JWT, jwt)
                    putExtra(EXTRA_EXPIRY, expiry)
                },
            )
            finish()
        }
    }

    private fun challengeHtml(): String {
        val sitekey = AmazonAudioProvider.TURNSTILE_SITEKEY
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" async defer></script>
              <style>html,body{margin:0;padding:0;background:transparent;}#cf{display:flex;justify-content:center;padding:12px;}</style>
            </head>
            <body>
              <div id="cf"></div>
              <script>
                var attempts = 0;
                var usedVisible = false;
                function report(err){ try { Android.onError(String(err)); } catch(e){} }
                function done(token){ try { Android.onToken(token); } catch(e){} }
                function renderWidget(visible){
                  var opts = {
                    sitekey: '$sitekey',
                    theme: 'auto',
                    callback: function(token){ done(token); },
                    'error-callback': function(){
                      if(!usedVisible){ usedVisible = true; try{ document.getElementById('cf').innerHTML=''; }catch(e){}; renderWidget(true); }
                      else { report('turnstile-error'); }
                    },
                    'expired-callback': function(){ report('turnstile-expired'); }
                  };
                  if(!visible){ opts.size = 'invisible'; opts.execution = 'execute'; }
                  try {
                    var id = window.turnstile.render('#cf', opts);
                    if(!visible){ window.turnstile.execute(id); }
                  } catch(e){ report('render-exception: ' + e); }
                }
                function start(){
                  attempts++;
                  if(window.turnstile && window.turnstile.render){ renderWidget(false); return; }
                  if(attempts > 80){ report('turnstile-script-timeout'); return; }
                  setTimeout(start, 150);
                }
                start();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun TurnstileContent() {
        var status by remember { mutableStateOf("Verifying with Cloudflare…") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Authorize Amazon Music") },
                    navigationIcon = {
                        IconButton(
                            onClick = { finishWithError("cancelled") },
                            onLongClick = { finishWithError("cancelled") },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // The WebView is effectively invisible (transparent, behind the status UI) unless
                // Cloudflare escalates to a visible interactive challenge, in which case it shows.
                AndroidView(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(0)

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onToken(token: String?) {
                                        if (!token.isNullOrBlank()) {
                                            runOnUiThread {
                                                status = "Signing in…"
                                                onTurnstileToken(token)
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onError(message: String?) {
                                        runOnUiThread {
                                            finishWithError(message?.takeIf { it.isNotBlank() } ?: "turnstile-failed")
                                        }
                                    }
                                },
                                "Android",
                            )

                            loadDataWithBaseURL(
                                AmazonAudioProvider.TURNSTILE_ORIGIN,
                                challengeHtml(),
                                "text/html",
                                "utf-8",
                                null,
                            )

                            activeWebView = this
                        }
                    },
                )
            }
        }
    }
}
