package moe.rukamori.archivetune.utils.potoken

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generates cryptographically valid PoTokens by running YouTube's BotGuard
 * challenge inside a headless [WebView].
 *
 * ## Lifecycle
 *
 * 1. [initialize] — called once from `Application.onCreate()` with the app context.
 * 2. [preWarm] — optional, boots the WebView at startup to avoid first-call delay.
 * 3. [mintToken] — suspend function, called per video. Reuses the engine until
 *    it expires (~50 min) or the session changes.
 * 4. [onAppBackgrounded] — releases the WebView to free ~50 MB of memory.
 *
 * ## Optimizations
 *
 * - **Suspend API** — [mintToken] is a suspend function, never blocks the calling thread.
 * - **Pre-warm** — [preWarm] bootstraps the engine at app startup so the first
 *   real mint is instant.
 * - **Player token cache** — Caches per-video tokens (LRU, max 200) to avoid
 *   redundant mints when the same video is played multiple times.
 * - **Session token reuse** — The session token is minted once per engine and
 *   reused for all videos until the engine expires or the session changes.
 * - **Background cleanup** — [onAppBackgrounded] releases the WebView to free memory.
 *   The engine is recreated on the next [mintToken] call.
 */
object BotGuardTokenGenerator {
    private const val TAG = "BotGuardTokenGen"
    private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
    private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val WV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
    private const val JS_BRIDGE = "BotGuardBridge"

    /** Timeout for first call (cold start: WebView boot + BotGuard bootstrap). */
    private const val COLD_START_TIMEOUT_MS = 45_000L

    /** Timeout for subsequent calls (warm: just mint a token). */
    private const val WARM_TIMEOUT_MS = 5_000L

    /** Maximum number of cached player tokens. */
    private const val PLAYER_TOKEN_CACHE_SIZE = 200

    private val httpClient =
        OkHttpClient
            .Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    // ── state ────────────────────────────────────────────────────────
    private var appContext: Context? = null
    private var permanentlyBroken = false

    private val mutex = Mutex()
    private var engine: BotGuardEngine? = null
    private var engineSessionId: String? = null
    private var cachedSessionToken: String? = null
    private var engineReady = false

    /**
     * LRU cache for per-video player tokens.
     * Key: videoId, Value: token string.
     * Avoids redundant mints when the same video is played multiple times.
     */
    private val playerTokenCache: LinkedHashMap<String, String> =
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > PLAYER_TOKEN_CACHE_SIZE
        }

    // ── public API ───────────────────────────────────────────────────

    /** Call once from `Application.onCreate()`. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        Timber.tag(TAG).d("Initialized")
    }

    /**
     * Pre-warm the BotGuard engine at app startup.
     * This bootstraps the WebView and generates the session token so that
     * the first real [mintToken] call is instant.
     *
     * Safe to call from a background coroutine. No-op if already warmed.
     */
    suspend fun preWarm(sessionId: String) {
        val ctx = appContext ?: return
        if (permanentlyBroken) return
        if (sessionId.isBlank()) return

        try {
            withTimeout(COLD_START_TIMEOUT_MS) {
                ensureEngineReady(ctx, sessionId)
            }
            Timber.tag(TAG).d("Pre-warm complete")
        } catch (timeout: TimeoutCancellationException) {
            Timber.tag(TAG).w("Pre-warm timed out after ${COLD_START_TIMEOUT_MS}ms")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Pre-warm failed (non-fatal)")
        }
    }

    /**
     * Mint a PoToken pair for the given [videoId] and [sessionId].
     *
     * Returns `null` when:
     * - [initialize] was never called
     * - The system has no usable WebView
     * - BotGuard bootstrap timed out
     * - The WebView is permanently broken
     *
     * This is a **suspend function** — never blocks the calling thread.
     * Call from a coroutine context (e.g. `Dispatchers.IO`).
     */
    suspend fun mintToken(
        videoId: String,
        sessionId: String,
    ): PoTokenResult? {
        val ctx =
            appContext ?: run {
                Timber.tag(TAG).w("initialize() not called")
                return null
            }
        if (permanentlyBroken) return null

        val cachedResult =
            mutex.withLock {
                if (!isEngineReadyForSession(sessionId)) return@withLock null
                val cachedPlayer = playerTokenCache[videoId] ?: return@withLock null
                val sessionToken = cachedSessionToken ?: return@withLock null
                PoTokenResult(playerToken = cachedPlayer, sessionToken = sessionToken)
            }
        if (cachedResult != null) {
            Timber.tag(TAG).d("Cache hit for $videoId")
            return cachedResult
        }

        val requiresColdStart =
            mutex.withLock {
                !isEngineReadyForSession(sessionId)
            }
        val timeout = if (requiresColdStart) COLD_START_TIMEOUT_MS else WARM_TIMEOUT_MS

        return try {
            withTimeout(timeout) {
                val result = mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = false)
                // Cache the player token
                mutex.withLock {
                    playerTokenCache[videoId] = result.playerToken
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            Timber.tag(TAG).w("Timed out after ${timeout}ms — proceeding without PoToken")
            mutex.withLock { destroyEngine() }
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: BrokenWebViewException) {
            Timber.tag(TAG).e(e, "Permanently broken WebView")
            permanentlyBroken = true
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "mintToken failed: ${e.message}")
            null
        }
    }

    /**
     * Release the WebView to free memory (~50 MB).
     * Call from `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` or similar.
     * The engine is recreated on the next [mintToken] call.
     */
    suspend fun onAppBackgrounded() {
        mutex.withLock {
            if (engine != null) {
                Timber.tag(TAG).d("Releasing engine (app backgrounded)")
                destroyEngine()
            }
        }
    }

    /**
     * Invalidate the player token cache for a specific video.
     * Useful when the user's auth state changes.
     */
    suspend fun invalidatePlayerToken(videoId: String) {
        mutex.withLock {
            playerTokenCache.remove(videoId)
        }
    }

    /**
     * Invalidate all cached tokens.
     * Call when the user logs out or auth state changes.
     */
    suspend fun invalidateAll() {
        mutex.withLock {
            playerTokenCache.clear()
            destroyEngine()
        }
    }

    // ── internal ─────────────────────────────────────────────────────

    private suspend fun ensureEngineReady(
        ctx: Context,
        sessionId: String,
    ) {
        getOrCreateEngine(
            ctx = ctx,
            sessionId = sessionId,
            forceNewEngine = false,
        )
    }

    private suspend fun mintTokenInternal(
        ctx: Context,
        videoId: String,
        sessionId: String,
        forceNewEngine: Boolean,
    ): PoTokenResult {
        val (eng, sessionTok, wasNew) =
            getOrCreateEngine(
                ctx = ctx,
                sessionId = sessionId,
                forceNewEngine = forceNewEngine,
            )

        val playerTok =
            try {
                eng.mint(videoId)
            } catch (e: Throwable) {
                if (wasNew) throw e
                Timber.tag(TAG).w(e, "mint failed, retrying with fresh engine")
                return mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = true)
            }

        return PoTokenResult(playerToken = playerTok, sessionToken = sessionTok)
    }

    private suspend fun getOrCreateEngine(
        ctx: Context,
        sessionId: String,
        forceNewEngine: Boolean,
    ): Triple<BotGuardEngine, String, Boolean> =
        mutex.withLock {
            val needsNew = forceNewEngine || !isEngineReadyForSession(sessionId)
            if (needsNew) {
                withContext(Dispatchers.Main) {
                    engine?.close()
                }
                engine = null
                engineSessionId = null
                cachedSessionToken = null
                engineReady = false
                playerTokenCache.clear()

                val newEngine = BotGuardEngine.create(ctx)
                val newSessionToken =
                    try {
                        newEngine.mint(sessionId)
                    } catch (error: Throwable) {
                        withContext(NonCancellable + Dispatchers.Main) {
                            newEngine.close()
                        }
                        throw error
                    }
                engine = newEngine
                engineSessionId = sessionId
                cachedSessionToken = newSessionToken
                engineReady = true
            }

            Triple(
                requireNotNull(engine),
                requireNotNull(cachedSessionToken),
                needsNew,
            )
        }

    private fun isEngineReadyForSession(sessionId: String): Boolean =
        engineReady &&
            engineSessionId == sessionId &&
            cachedSessionToken != null &&
            engine?.isExpired == false

    private suspend fun destroyEngine() {
        withContext(Dispatchers.Main) {
            engine?.close()
        }
        engine = null
        engineSessionId = null
        cachedSessionToken = null
        engineReady = false
    }

    // ── WebView wrapper ──────────────────────────────────────────────

    private class BotGuardEngine private constructor(
        private val webView: WebView,
        private val readySignal: CancellableContinuation<BotGuardEngine>,
    ) {
        private val scope = MainScope()
        private val closed = AtomicBoolean(false)
        private val readyCompleted = AtomicBoolean(false)
        private val pendingMints =
            Collections.synchronizedMap(
                ArrayMap<String, CancellableContinuation<String>>(),
            )
        private lateinit var expiry: Instant

        val isExpired: Boolean get() = Instant.now().isAfter(expiry)

        fun startBootstrap() {
            scope.launch(exceptionHandler) {
                val html =
                    withContext(Dispatchers.IO) {
                        webView.context.assets
                            .open("po_token.html")
                            .bufferedReader()
                            .use { it.readText() }
                    }
                val patched = html.replaceFirst("</script>", "\n$JS_BRIDGE.onPageLoaded()</script>")
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    patched,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onPageLoaded() {
            Timber.tag(TAG).d("Page loaded — requesting challenge from Create")
            postToBotGuard(CREATE_URL, "[ \"$REQUEST_KEY\" ]") { body ->
                val challengeJson = parseCreateChallenge(body)
                webView.evaluateJavascript(
                    """
                    try {
                        var data = $challengeJson;
                        runBotGuard(data).then(function(r) {
                            this.webPoSignalOutput = r.webPoSignalOutput;
                            $JS_BRIDGE.onBotGuardReady(r.botguardResponse);
                        }, function(e) {
                            $JS_BRIDGE.onFatalError(e + "\n" + e.stack);
                        });
                    } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                    """.trimIndent(),
                    null,
                )
            }
        }

        @JavascriptInterface
        fun onBotGuardReady(botguardResponse: String) {
            Timber.tag(TAG).d("BotGuard executed — requesting integrity token from GenerateIT")
            postToBotGuard(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
                try {
                    val (tokenU8, lifetimeSec) = parseIntegrityToken(body)
                    expiry = Instant.now().plusSeconds(lifetimeSec).minus(10, ChronoUnit.MINUTES)

                    webView.evaluateJavascript(
                        """
                        try {
                            this.integrityToken = $tokenU8;
                            createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                                $JS_BRIDGE.onMinterReady();
                            }).catch(function(e) {
                                $JS_BRIDGE.onFatalError(e + "\n" + (e.stack || ''));
                            });
                        } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                        """.trimIndent(),
                        null,
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "parseIntegrityToken failed")
                    signalError(PoTokenException("GenerateIT parse failed: ${e.message}"))
                }
            }
        }

        @JavascriptInterface
        fun onMinterReady() {
            Timber.tag(TAG).d("Minter ready")
            resumeReady(this@BotGuardEngine)
        }

        @JavascriptInterface
        fun onFatalError(error: String) {
            Timber.tag(TAG).e("Fatal JS error: $error")
            signalError(classifyJsError(error))
        }

        suspend fun mint(identifier: String): String =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    pendingMints[identifier] = cont
                    cont.invokeOnCancellation {
                        synchronized(pendingMints) {
                            if (pendingMints[identifier] === cont) {
                                pendingMints.remove(identifier)
                            }
                        }
                    }
                    val u8Arg = stringToJsUint8Array(identifier)
                    webView.evaluateJavascript(
                        """
                        try {
                            obtainPoToken($u8Arg).then(function(u8) {
                                $JS_BRIDGE.onMintOk("$identifier", u8.join(","));
                            }).catch(function(e) {
                                $JS_BRIDGE.onMintErr("$identifier", e + "\n" + (e.stack || ''));
                            });
                        } catch(e) { $JS_BRIDGE.onMintErr("$identifier", e + "\n" + e.stack); }
                        """.trimIndent(),
                        null,
                    )
                }
            }

        @JavascriptInterface
        fun onMintOk(
            identifier: String,
            csvBytes: String,
        ) {
            val base64 = commaSeparatedBytesToBase64(csvBytes)
            Timber.tag(TAG).d("Minted token for $identifier (${base64.length} chars)")
            pendingMints
                .remove(identifier)
                ?.let { continuation ->
                    if (continuation.isActive) {
                        continuation.resume(base64)
                    }
                }
        }

        @JavascriptInterface
        fun onMintErr(
            identifier: String,
            error: String,
        ) {
            Timber.tag(TAG).e("Mint failed for $identifier: $error")
            pendingMints
                .remove(identifier)
                ?.let { continuation ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(classifyJsError(error))
                    }
                }
        }

        private val exceptionHandler = CoroutineExceptionHandler { _, t -> signalError(t) }

        private fun signalError(error: Throwable) {
            close()
            resumeReadyWithException(error)
        }

        private fun resumeReady(engine: BotGuardEngine) {
            if (!readyCompleted.compareAndSet(false, true)) return
            if (!readySignal.isActive) return
            runCatching {
                readySignal.resume(engine)
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Ready signal was already completed")
            }
        }

        private fun resumeReadyWithException(error: Throwable) {
            if (!readyCompleted.compareAndSet(false, true)) return
            if (!readySignal.isActive) return
            runCatching {
                readySignal.resumeWithException(error)
            }.onFailure { resumeError ->
                Timber.tag(TAG).w(resumeError, "Ready signal was already completed")
            }
        }

        private fun postToBotGuard(
            url: String,
            jsonBody: String,
            onSuccess: (String) -> Unit,
        ) {
            scope.launch(exceptionHandler) {
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(url)
                        .post(jsonBody.toRequestBody())
                        .headers(
                            mapOf(
                                "User-Agent" to WV_USER_AGENT,
                                "Accept" to "application/json",
                                "Content-Type" to "application/json+protobuf",
                                "x-goog-api-key" to API_KEY,
                                "x-user-agent" to "grpc-web-javascript/0.1",
                            ).toHeaders(),
                        ).build()

                val response =
                    withContext(Dispatchers.IO) {
                        httpClient.newCall(request).execute()
                    }

                if (response.code != 200) {
                    signalError(PoTokenException("BotGuard HTTP ${response.code} from $url"))
                } else {
                    val body = withContext(Dispatchers.IO) { response.body!!.string() }
                    onSuccess(body)
                }
            }
        }

        @MainThread
        fun close() {
            if (!closed.compareAndSet(false, true)) return
            scope.cancel()
            val continuations =
                synchronized(pendingMints) {
                    pendingMints.values.toList().also {
                        pendingMints.clear()
                    }
                }
            continuations.forEach { continuation ->
                continuation.cancel(CancellationException("BotGuard engine closed"))
            }
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }

        companion object {
            suspend fun create(context: Context): BotGuardEngine {
                return withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        lateinit var engine: BotGuardEngine
                        val wv =
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.userAgentString = WV_USER_AGENT
                                settings.blockNetworkLoads = true
                                webChromeClient =
                                    object : WebChromeClient() {
                                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                            if (m.message().contains("Uncaught")) {
                                                val err = "\"${m.message()}\", ${m.sourceId()} (${m.lineNumber()})"
                                                engine.signalError(BrokenWebViewException(err))
                                            }
                                            return super.onConsoleMessage(m)
                                        }
                                    }
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun onRenderProcessGone(
                                            view: WebView,
                                            detail: android.webkit.RenderProcessGoneDetail,
                                        ): Boolean {
                                            Timber.tag(TAG).w("WebView renderer gone (crashed=${detail.didCrash()})")
                                            engine.signalError(PoTokenException("WebView renderer process gone"))
                                            return true
                                        }
                                    }
                            }
                        engine = BotGuardEngine(wv, cont)
                        cont.invokeOnCancellation {
                            wv.post(engine::close)
                        }
                        wv.addJavascriptInterface(engine, JS_BRIDGE)
                        engine.startBootstrap()
                    }
                }
            }
        }
    }
}
