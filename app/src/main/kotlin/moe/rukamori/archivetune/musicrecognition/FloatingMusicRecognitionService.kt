/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.screens.musicrecognition.FloatingBubbleFieldSize
import moe.rukamori.archivetune.ui.screens.musicrecognition.FloatingRecognitionBubble
import moe.rukamori.archivetune.ui.screens.musicrecognition.FloatingRecognitionDismissTarget
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal sealed interface FloatingRecognitionUiState {
    data object Idle : FloatingRecognitionUiState

    data object Listening : FloatingRecognitionUiState

    data object Processing : FloatingRecognitionUiState

    data class Matched(val track: RecognizedTrack) : FloatingRecognitionUiState

    data class Failed(val failure: MusicRecognitionFailure) : FloatingRecognitionUiState
}

@AndroidEntryPoint
class FloatingMusicRecognitionService :
    Service(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    @Inject
    lateinit var recognizeMusic: RecognizeMusicUseCase

    @Inject
    lateinit var notificationManager: MusicRecognitionNotificationManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val uiState = MutableStateFlow<FloatingRecognitionUiState>(FloatingRecognitionUiState.Idle)
    private val capture = MutableStateFlow(AudioCaptureProgress(level = 0f, fraction = 0f))
    private var recognitionJob: Job? = null
    private var snapAnimator: ValueAnimator? = null
    private var bubbleView: View? = null
    private var dismissView: View? = null
    private var anchoredToEnd = false

    private var dockedToEnd by mutableStateOf(false)
    private var cardSuppressed by mutableStateOf(false)
    private var dragging = false
    private var dismissHovered by mutableStateOf(false)

    private val bubbleParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        notificationManager.createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ActionShow -> showFloating(listen = false)
            ActionListen -> showFloating(listen = true)
            ActionCancel -> cancelRecognition()
            ActionClose -> close()
            else -> if (bubbleView == null) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bubbleView == null || dragging) return
        snapAnimator?.cancel()
        anchorBubble(toEnd = dockedToEnd, x = edgeMarginPx())
        bubbleParams.y = bubbleParams.y.coerceIn(0, maxBubbleY())
        updateBubbleLayout()
        cardSuppressed = false
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        recognitionJob?.cancel()
        removeOverlayViews()
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
        requestTileRefresh()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun showFloating(listen: Boolean) {
        if (!enterForeground()) return
        if (!Settings.canDrawOverlays(this)) {
            Timber.w("Floating music recognition requires the overlay permission")
            close()
            return
        }
        showBubble()
        if (listen) startListening()
    }

    private fun enterForeground(): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                MusicRecognitionNotificationManager.ForegroundNotificationId,
                notificationManager.floating(MusicRecognitionRuntimeState.state),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                },
            )
            true
        } catch (exception: SecurityException) {
            Timber.e(exception, "Floating music recognition could not enter the foreground")
            close()
            false
        }

    private fun showBubble() {
        if (bubbleView != null) return
        val (_, screenHeight) = screenSize()
        anchorBubble(toEnd = dockedToEnd, x = edgeMarginPx())
        bubbleParams.y = (screenHeight * InitialBubbleHeightFraction).roundToInt()
        val view =
            DraggableOverlayLayout(this, ::onDragStart, ::onDrag, ::onDragEnd).apply {
                addView(
                    overlayComposeView {
                        val state by uiState.collectAsStateWithLifecycle()
                        val captureState = capture.collectAsStateWithLifecycle()
                        FloatingRecognitionBubble(
                            state = state,
                            captureLevel = { captureState.value.level },
                            captureProgress = { captureState.value.fraction },
                            dockedToEnd = dockedToEnd,
                            showCard = !cardSuppressed,
                            onBubbleClick = ::onBubbleClick,
                            onOpenResult = ::openResult,
                            onDismissResult = ::dismissResult,
                        )
                    },
                )
            }
        addOverlayView(view, bubbleParams)
        bubbleView = view
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun overlayComposeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            setContent { ArchiveTuneTheme { content() } }
        }

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        windowManager.addView(view, params)
    }

    private fun onBubbleClick() {
        when (uiState.value) {
            FloatingRecognitionUiState.Idle, is FloatingRecognitionUiState.Failed -> startListening()
            FloatingRecognitionUiState.Listening, FloatingRecognitionUiState.Processing -> cancelRecognition()
            is FloatingRecognitionUiState.Matched -> openResult()
        }
    }

    private fun startListening() {
        if (recognitionJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            uiState.value = FloatingRecognitionUiState.Failed(MusicRecognitionFailure.RecordingFailed)
            return
        }
        capture.value = AudioCaptureProgress(level = 0f, fraction = 0f)
        uiState.value = FloatingRecognitionUiState.Listening
        updateRuntimeState(BackgroundRecognitionState.Listening)
        bubbleView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        recognitionJob =
            serviceScope.launch {
                try {
                    recognizeMusic(
                        onPhaseChanged = ::onPhaseChanged,
                        onCaptureProgress = { capture.value = it },
                    ).fold(
                        onSuccess = { track ->
                            uiState.value = FloatingRecognitionUiState.Matched(track)
                            notificationManager.notify(notificationManager.result(track))
                            performResultHaptic(confirm = true)
                        },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) throw throwable
                            Timber.e(throwable, "Floating music recognition failed")
                            uiState.value =
                                FloatingRecognitionUiState.Failed(
                                    (throwable as? MusicRecognitionException)?.failure
                                        ?: MusicRecognitionFailure.RecognitionFailed,
                                )
                            performResultHaptic(confirm = false)
                        },
                    )
                } finally {
                    if (recognitionJob === coroutineContext.job) {
                        recognitionJob = null
                        updateRuntimeState(BackgroundRecognitionState.Idle)
                    }
                }
            }
    }

    private fun onPhaseChanged(phase: RecognitionPhase) {
        when (phase) {
            RecognitionPhase.Listening -> {
                uiState.value = FloatingRecognitionUiState.Listening
                updateRuntimeState(BackgroundRecognitionState.Listening)
            }

            RecognitionPhase.Processing -> {
                uiState.value = FloatingRecognitionUiState.Processing
                updateRuntimeState(BackgroundRecognitionState.Processing)
            }
        }
    }

    private fun cancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        capture.value = AudioCaptureProgress(level = 0f, fraction = 0f)
        uiState.value = FloatingRecognitionUiState.Idle
        if (bubbleView == null) {
            close()
        } else {
            updateRuntimeState(BackgroundRecognitionState.Idle)
        }
    }

    private fun openResult() {
        val track = (uiState.value as? FloatingRecognitionUiState.Matched)?.track ?: return
        uiState.value = FloatingRecognitionUiState.Idle
        startActivity(notificationManager.detailsIntent(track))
    }

    private fun dismissResult() {
        if (uiState.value is FloatingRecognitionUiState.Matched || uiState.value is FloatingRecognitionUiState.Failed) {
            uiState.value = FloatingRecognitionUiState.Idle
        }
    }

    private fun onDragStart() {
        snapAnimator?.cancel()
        anchorBubble(toEnd = false, x = currentBubbleLeft())
        dragging = true
        cardSuppressed = true
        showDismissTarget()
        updateBubbleLayout()
    }

    private fun onDrag(deltaX: Float, deltaY: Float) {
        val (screenWidth, _) = screenSize()
        bubbleParams.x = (bubbleParams.x + deltaX.roundToInt()).coerceIn(0, screenWidth - fieldSizePx())
        bubbleParams.y = (bubbleParams.y + deltaY.roundToInt()).coerceIn(0, maxBubbleY())
        updateBubbleLayout()
        val hovered = isOverDismissTarget()
        if (hovered != dismissHovered) {
            dismissHovered = hovered
            if (hovered) dismissView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun onDragEnd() {
        dragging = false
        removeDismissTarget()
        if (dismissHovered) {
            dismissHovered = false
            close()
            return
        }
        snapToEdge()
    }

    private fun snapToEdge() {
        val (screenWidth, _) = screenSize()
        val margin = edgeMarginPx()
        val fieldSize = fieldSizePx()
        val toEnd = bubbleParams.x + fieldSize / 2 > screenWidth / 2
        val target = if (toEnd) screenWidth - fieldSize - margin else margin
        snapAnimator =
            ValueAnimator.ofInt(bubbleParams.x, target).apply {
                duration = SnapDurationMillis
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    bubbleParams.x = animator.animatedValue as Int
                    updateBubbleLayout()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var canceled = false

                        override fun onAnimationCancel(animation: Animator) {
                            canceled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (canceled) return
                            dockedToEnd = toEnd
                            anchorBubble(toEnd = toEnd, x = margin)
                            updateBubbleLayout()
                            cardSuppressed = false
                        }
                    },
                )
                start()
            }
    }

    private fun anchorBubble(toEnd: Boolean, x: Int) {
        anchoredToEnd = toEnd
        bubbleParams.gravity = Gravity.TOP or if (toEnd) Gravity.END else Gravity.START
        bubbleParams.x = x
    }

    private fun currentBubbleLeft(): Int {
        val (screenWidth, _) = screenSize()
        return if (anchoredToEnd) screenWidth - bubbleParams.x - fieldSizePx() else bubbleParams.x
    }

    private fun isOverDismissTarget(): Boolean {
        val (screenWidth, screenHeight) = screenSize()
        val fieldSize = fieldSizePx()
        val centerX = bubbleParams.x + fieldSize / 2
        val centerY = bubbleParams.y + fieldSize / 2
        val density = resources.displayMetrics.density
        return centerY >= screenHeight - DismissZoneHeightDp * density &&
            abs(centerX - screenWidth / 2) <= DismissZoneHalfWidthDp * density
    }

    private fun showDismissTarget() {
        if (dismissView != null) return
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM }
        val view = overlayComposeView { FloatingRecognitionDismissTarget(hovered = dismissHovered) }
        addOverlayView(view, params)
        dismissView = view
    }

    private fun removeDismissTarget() {
        val view = dismissView ?: return
        dismissView = null
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Timber.w(it, "Failed to remove music recognition dismiss target") }
    }

    private fun updateBubbleLayout() {
        val view = bubbleView ?: return
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, bubbleParams)
    }

    private fun removeOverlayViews() {
        removeDismissTarget()
        val view = bubbleView ?: return
        bubbleView = null
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Timber.w(it, "Failed to remove floating music recognition bubble") }
    }

    private fun close() {
        snapAnimator?.cancel()
        recognitionJob?.cancel()
        recognitionJob = null
        removeOverlayViews()
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
        requestTileRefresh()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateRuntimeState(state: BackgroundRecognitionState) {
        MusicRecognitionRuntimeState.update(state)
        notificationManager.notifyFloating(notificationManager.floating(state))
        requestTileRefresh()
    }

    private fun performResultHaptic(confirm: Boolean) {
        val feedback =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (confirm) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        bubbleView?.performHapticFeedback(feedback)
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }

    private fun fieldSizePx(): Int = (FloatingBubbleFieldSize.value * resources.displayMetrics.density).roundToInt()

    private fun edgeMarginPx(): Int = (EdgeMarginDp * resources.displayMetrics.density).roundToInt()

    private fun maxBubbleY(): Int = (screenSize().second - fieldSizePx()).coerceAtLeast(0)

    private fun requestTileRefresh() {
        TileService.requestListeningState(
            this,
            ComponentName(this, MusicRecognitionTileService::class.java),
        )
    }

    companion object {
        private const val ActionShow = "moe.rukamori.archivetune.action.SHOW_FLOATING_RECOGNITION"
        private const val ActionListen = "moe.rukamori.archivetune.action.LISTEN_FLOATING_RECOGNITION"
        private const val ActionCancel = "moe.rukamori.archivetune.action.CANCEL_FLOATING_RECOGNITION"
        private const val ActionClose = "moe.rukamori.archivetune.action.CLOSE_FLOATING_RECOGNITION"
        private const val InitialBubbleHeightFraction = 0.3f
        private const val EdgeMarginDp = 4f
        private const val DismissZoneHeightDp = 176f
        private const val DismissZoneHalfWidthDp = 120f
        private const val SnapDurationMillis = 260L

        fun showIntent(
            context: Context,
            listen: Boolean,
        ): Intent =
            Intent(context, FloatingMusicRecognitionService::class.java).apply {
                action = if (listen) ActionListen else ActionShow
            }

        fun cancelIntent(context: Context): Intent =
            Intent(context, FloatingMusicRecognitionService::class.java).apply {
                action = ActionCancel
            }

        fun closeIntent(context: Context): Intent =
            Intent(context, FloatingMusicRecognitionService::class.java).apply {
                action = ActionClose
            }
    }
}

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
private class DraggableOverlayLayout(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE ->
                if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                    beginDrag(event)
                }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
            }

            MotionEvent.ACTION_MOVE ->
                if (dragging) {
                    onDrag(event.rawX - lastX, event.rawY - lastY)
                    lastX = event.rawX
                    lastY = event.rawY
                } else if (hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                    beginDrag(event)
                }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                if (dragging) {
                    dragging = false
                    onDragEnd()
                }
        }
        return true
    }

    private fun beginDrag(event: MotionEvent) {
        dragging = true
        lastX = event.rawX
        lastY = event.rawY
        onDragStart()
    }
}
