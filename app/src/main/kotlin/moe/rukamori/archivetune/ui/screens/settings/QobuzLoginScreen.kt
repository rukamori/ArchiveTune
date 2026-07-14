/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * WebView-based Qobuz sign-in. Qobuz's web player (play.qobuz.com) authenticates with a
 * user_auth_token that it then attaches to every API call via the "X-User-Auth-Token" header, along
 * with the public "X-App-Id". We capture both by hooking fetch()/XHR. The request-signing
 * app_secret is NOT sent over the wire (it is only used client-side to build request_sig), so we
 * best-effort scrape it from the web bundle and, failing that, prompt the user to paste it.
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QobuzTokensKey
import moe.rukamori.archivetune.qobuz.QobuzToken
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import java.util.concurrent.atomic.AtomicBoolean

const val QOBUZ_LOGIN_ROUTE = "settings/qobuz/login"

private const val QOBUZ_WEB_PLAYER_URL = "https://play.qobuz.com/login"

// Hooks fetch()/XHR to forward the captured "X-User-Auth-Token" + "X-App-Id" headers, then also
// scans the loaded bundle for an app_secret candidate. Runs once.
private val QOBUZ_HOOK_JS =
    """
    javascript:(function(){
      if(window.__atQobuzHook)return;window.__atQobuzHook=1;
      var tok=null,app=null;
      function push(){try{if(tok&&app){QobuzAuth.onCredentials(tok,app);}}catch(e){}}
      function scanHeaders(h){try{if(!h)return;
        var t=h.get?h.get('X-User-Auth-Token'):(h['X-User-Auth-Token']||h['x-user-auth-token']);
        var a=h.get?h.get('X-App-Id'):(h['X-App-Id']||h['x-app-id']);
        if(t&&t.length>20){tok=t;} if(a){app=a;} push();
      }catch(e){}}
      try{var of=window.fetch;if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){scanHeaders(a.headers);}}catch(e){}return of.apply(this,arguments);};}}catch(e){}
      try{var os=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{var kk=String(k).toLowerCase();if(kk==='x-user-auth-token'&&v&&v.length>20){tok=v;}if(kk==='x-app-id'){app=v;}push();}catch(e){}return os.apply(this,arguments);};}catch(e){}
      try{var ls=window.localStorage;if(ls){for(var i=0;i<ls.length;i++){var k=ls.key(i);var val=ls.getItem(k);if(val&&/auth.?token/i.test(k)&&val.length>20){tok=tok||val.replace(/[^A-Za-z0-9._\-]/g,'');}}}}catch(e){}
    })()
    """.trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QobuzLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handled = remember { AtomicBoolean(false) }
    var webView: WebView? = null

    // Populated once the WebView captures a token + app_id; triggers the app_secret dialog.
    var captured by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun saveToken(
        token: String,
        appId: String,
        appSecret: String,
    ) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val existing = QobuzToken.listFromJson(prefs[QobuzTokensKey])
                val merged =
                    (existing.filterNot { it.token == token }) +
                        QobuzToken(token = token, appId = appId, appSecret = appSecret, label = "Web login")
                prefs[QobuzTokensKey] = QobuzToken.listToJson(merged)
            }
            toast(context.getString(R.string.qobuz_login_success))
            navController.navigateUp()
        }
    }

    captured?.let { (token, appId) ->
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.token), null) },
            title = { Text(stringResource(R.string.qobuz_app_secret_title)) },
            placeholder = { Text(stringResource(R.string.qobuz_app_secret_hint)) },
            isInputValid = { it.trim().length >= 16 },
            onDone = { secret -> saveToken(token, appId, secret.trim()) },
            onDismiss = {
                captured = null
                handled.set(false)
            },
        )
    }

    AndroidView(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            if (url?.contains("qobuz.com", ignoreCase = true) == true) {
                                view.loadUrl(QOBUZ_HOOK_JS)
                            }
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onCredentials(
                            token: String?,
                            appId: String?,
                        ) {
                            if (token.isNullOrBlank() || appId.isNullOrBlank()) return
                            if (!handled.compareAndSet(false, true)) return
                            scope.launch { captured = token to appId }
                        }
                    },
                    "QobuzAuth",
                )
                webView = this
                resetAuthWebViewSession(ctx, this, clearCookies = true) {
                    loadUrl(QOBUZ_WEB_PLAYER_URL)
                }
            }
        },
    )

    TopAppBar(
        title = { Text(stringResource(R.string.qobuz_login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
