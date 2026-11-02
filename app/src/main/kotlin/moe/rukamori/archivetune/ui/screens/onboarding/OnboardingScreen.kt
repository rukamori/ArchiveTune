/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package moe.rukamori.archivetune.ui.screens.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.OnboardingCommunityActionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingEvent
import moe.rukamori.archivetune.onboarding.OnboardingLoginBenefitUiModel
import moe.rukamori.archivetune.onboarding.OnboardingPageId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionAction
import moe.rukamori.archivetune.onboarding.OnboardingPermissionId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionStatus
import moe.rukamori.archivetune.onboarding.OnboardingPermissionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    onLoginRequested: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val openLogin by rememberUpdatedState(onLoginRequested)
    var requestedPermission by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val permission = requestedPermission
            val canRequestAgain =
                permission == null || activity == null ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            viewModel.onPermissionResult(permission, granted, canRequestAgain)
            requestedPermission = null
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onSettingsResult()
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    LaunchedEffect(context, lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                try {
                    when (event) {
                        is OnboardingEvent.RequestPermission -> {
                            requestedPermission = event.permission
                            permissionLauncher.launch(event.permission)
                        }
                        OnboardingEvent.OpenInstallPackagesSettings -> {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        }
                        OnboardingEvent.OpenAppSettings -> {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        }
                        OnboardingEvent.OpenLogin -> openLogin()
                        is OnboardingEvent.OpenUri -> {
                            context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
                            viewModel.onExternalActionFinished(event)
                        }
                    }
                } catch (_: ActivityNotFoundException) {
                    requestedPermission = null
                    viewModel.onExternalActionFailed(event)
                } catch (_: SecurityException) {
                    requestedPermission = null
                    viewModel.onExternalActionFailed(event)
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onRetry = viewModel::retryLoading,
        onLogin = viewModel::onLogin,
        onPermissionAction = viewModel::onPermissionAction,
        onCommunityAction = viewModel::onCommunityAction,
        onRetryExternalAction = viewModel::retryExternalAction,
        onDismissActionError = viewModel::dismissActionError,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: (OnboardingPageId) -> Unit,
    onBack: (OnboardingPageId) -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    onRetryExternalAction: () -> Unit,
    onDismissActionError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = (state as? OnboardingScreenState.Success)?.uiState
    val page = uiState?.pages?.get(uiState.currentPage)
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()
    val errorMessage = uiState?.actionErrorResId?.let { stringResource(it) }
    val retryLabel = stringResource(R.string.retry)
    val retryExternalAction by rememberUpdatedState(onRetryExternalAction)
    val dismissActionError by rememberUpdatedState(onDismissActionError)

    LaunchedEffect(errorMessage, uiState?.actionErrorId) {
        if (errorMessage != null) {
            val result =
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = retryLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) retryExternalAction() else dismissActionError()
        }
    }

    BackHandler(enabled = uiState != null && uiState.currentPage > 0) {
        if (uiState != null && page != null && !uiState.isBusy) onBack(page.id)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (state) {
            OnboardingScreenState.Loading -> SetupLoading()
            OnboardingScreenState.Empty -> SetupError(R.string.onboarding_empty_title, onRetry)
            is OnboardingScreenState.Error -> SetupError(state.messageResId, onRetry)
            is OnboardingScreenState.Success -> {
                OnboardingFlow(
                    uiState = state.uiState,
                    pageStateHolder = pageStateHolder,
                    snackbarHostState = snackbarHostState,
                    onNext = onNext,
                    onBack = onBack,
                    onLogin = onLogin,
                    onPermissionAction = onPermissionAction,
                    onCommunityAction = onCommunityAction,
                )
            }
        }
    }
}

@Composable
private fun SetupLoading() {
    val loadingLabel = stringResource(R.string.loading)
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalAnimationsDisabled.current) {
            Text(loadingLabel, style = MaterialTheme.typography.bodyLarge)
        } else {
            LoadingIndicator(modifier = Modifier.semantics { contentDescription = loadingLabel })
        }
    }
}

@Composable
private fun SetupError(
    @StringRes messageResId: Int,
    onRetry: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                stringResource(messageResId),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Button(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(R.string.retry), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OnboardingFlow(
    uiState: OnboardingUiState,
    pageStateHolder: SaveableStateHolder,
    snackbarHostState: SnackbarHostState,
    onNext: (OnboardingPageId) -> Unit,
    onBack: (OnboardingPageId) -> Unit,
    onLogin: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val page = uiState.pages[uiState.currentPage].id
    val palette = page.palette()
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val stageColor by animateColorAsState(palette.stage, colorSpec, label = "onboardingStage")
    val onStageColor by animateColorAsState(palette.onStage, colorSpec, label = "onboardingOnStage")
    val accentColor by animateColorAsState(palette.accent, colorSpec, label = "onboardingAccent")
    val onAccentColor by animateColorAsState(palette.onAccent, colorSpec, label = "onboardingOnAccent")

    BoxWithConstraints(Modifier.fillMaxSize().background(stageColor)) {
        val twoPane = maxWidth > maxHeight && maxWidth >= 600.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                OnboardingStage(
                    uiState = uiState,
                    page = page,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    onStageColor = onStageColor,
                    onBack = onBack,
                    modifier =
                        Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom),
                            ),
                )
                OnboardingSheet(
                    uiState = uiState,
                    page = page,
                    pageStateHolder = pageStateHolder,
                    snackbarHostState = snackbarHostState,
                    shape = RoundedCornerShape(topStart = OnboardingSheetCorner, bottomStart = OnboardingSheetCorner),
                    contentInsets =
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom),
                    onNext = onNext,
                    onLogin = onLogin,
                    onPermissionAction = onPermissionAction,
                    onCommunityAction = onCommunityAction,
                    modifier = Modifier.weight(0.56f).fillMaxHeight(),
                )
            }
        } else {
            val stageHeight = (maxHeight * 0.38f).coerceAtMost(360.dp)
            OnboardingStage(
                uiState = uiState,
                page = page,
                accentColor = accentColor,
                onAccentColor = onAccentColor,
                onStageColor = onStageColor,
                onBack = onBack,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(stageHeight)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(bottom = OnboardingSheetCorner),
            )
            OnboardingSheet(
                uiState = uiState,
                page = page,
                pageStateHolder = pageStateHolder,
                snackbarHostState = snackbarHostState,
                shape = RoundedCornerShape(topStart = OnboardingSheetCorner, topEnd = OnboardingSheetCorner),
                contentInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                onNext = onNext,
                onLogin = onLogin,
                onPermissionAction = onPermissionAction,
                onCommunityAction = onCommunityAction,
                modifier = Modifier.fillMaxSize().padding(top = stageHeight - OnboardingSheetCorner),
            )
        }
    }
}

@Composable
private fun OnboardingStage(
    uiState: OnboardingUiState,
    page: OnboardingPageId,
    accentColor: Color,
    onAccentColor: Color,
    onStageColor: Color,
    onBack: (OnboardingPageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OnboardingProgressHeader(
            uiState = uiState,
            page = page,
            accentColor = accentColor,
            onStageColor = onStageColor,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val heroSize = minOf(maxWidth, maxHeight, 280.dp)
            if (heroSize >= 72.dp) {
                OnboardingHero(
                    page = page,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    modifier = Modifier.size(heroSize),
                )
            }
        }
    }
}

@Composable
private fun OnboardingProgressHeader(
    uiState: OnboardingUiState,
    page: OnboardingPageId,
    accentColor: Color,
    onStageColor: Color,
    onBack: (OnboardingPageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val stepCount = uiState.pages.size
    val stepNumber = uiState.currentPage + 1
    val stepLabel = stringResource(R.string.onboarding_step, stepNumber, stepCount)
    val progress by animateFloatAsState(
        targetValue = stepNumber.toFloat() / stepCount,
        animationSpec = if (animationsDisabled) snap() else ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "onboardingProgress",
    )
    Row(
        modifier = modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = uiState.currentPage > 0,
            enter =
                if (animationsDisabled) {
                    EnterTransition.None
                } else {
                    fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                },
            exit =
                if (animationsDisabled) {
                    ExitTransition.None
                } else {
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                },
        ) {
            FilledIconButton(
                onClick = { onBack(page) },
                enabled = !uiState.isBusy,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            color = accentColor,
            trackColor = onStageColor.copy(alpha = 0.16f),
            modifier =
                Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = stepLabel
                        liveRegion = LiveRegionMode.Polite
                    },
        )
        Text(
            text = stringResource(R.string.onboarding_step_short, stepNumber, stepCount),
            style = MaterialTheme.typography.labelLarge,
            color = onStageColor,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun OnboardingHero(
    page: OnboardingPageId,
    accentColor: Color,
    onAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val targetPolygon = page.heroPolygon()
    var morphFrom by remember { mutableStateOf(targetPolygon) }
    var morphTo by remember { mutableStateOf(targetPolygon) }
    val morphProgress = remember { Animatable(1f) }
    val morphSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val entranceSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var hasEntered by rememberSaveable { mutableStateOf(false) }
    val entrance = remember { Animatable(if (hasEntered || animationsDisabled) 1f else 0f) }
    val rotation by animateFloatAsState(
        targetValue = page.ordinal * HeroRotationStep,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "onboardingHeroRotation",
    )

    LaunchedEffect(targetPolygon, animationsDisabled) {
        if (targetPolygon !== morphTo) {
            morphFrom = morphTo
            morphTo = targetPolygon
            if (animationsDisabled) {
                morphProgress.snapTo(1f)
            } else {
                morphProgress.snapTo(0f)
                morphProgress.animateTo(1f, morphSpec)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!hasEntered) {
            hasEntered = true
            entrance.animateTo(1f, entranceSpec)
        }
    }

    val morph = remember(morphFrom, morphTo) { Morph(morphFrom, morphTo) }
    Box(
        modifier =
            modifier.graphicsLayer {
                val scale = 0.72f + 0.28f * entrance.value
                scaleX = scale
                scaleY = scale
                alpha = entrance.value.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -rotation / 2f
                    shape = EditorialMorphShape(morph, morphProgress.value)
                    clip = true
                }.background(accentColor.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .fillMaxSize(0.76f)
                .graphicsLayer {
                    rotationZ = rotation
                    shape = EditorialMorphShape(morph, morphProgress.value)
                    clip = true
                }.background(accentColor),
        )
        Crossfade(
            targetState = page,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            modifier = Modifier.fillMaxSize(0.3f),
            label = "onboardingHeroIcon",
        ) { heroPage ->
            Icon(
                painter = painterResource(heroPage.heroIconResId()),
                contentDescription = null,
                tint = onAccentColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OnboardingSheet(
    uiState: OnboardingUiState,
    page: OnboardingPageId,
    pageStateHolder: SaveableStateHolder,
    snackbarHostState: SnackbarHostState,
    shape: RoundedCornerShape,
    contentInsets: WindowInsets,
    onNext: (OnboardingPageId) -> Unit,
    onLogin: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
            val actionsMaxHeight = maxHeight / 2
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    OnboardingPages(
                        uiState = uiState,
                        pageStateHolder = pageStateHolder,
                        onPermissionAction = onPermissionAction,
                        onCommunityAction = onCommunityAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = OnboardingContentMaxWidth),
                    )
                }
                OnboardingActions(
                    uiState = uiState,
                    page = page,
                    onNext = onNext,
                    onLogin = onLogin,
                    maxHeight = actionsMaxHeight,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPages(
    uiState: OnboardingUiState,
    pageStateHolder: SaveableStateHolder,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = uiState.pages[uiState.currentPage].id,
        transitionSpec = {
            if (animationsDisabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward =
                    uiState.pages.indexOfFirst { it.id == targetState } >
                        uiState.pages.indexOfFirst { it.id == initialState }
                val direction = (if (forward) 1 else -1) * (if (isRtl) -1 else 1)
                (slideInHorizontally(slideSpec) { width -> direction * width / 4 } + fadeIn(fadeSpec)) togetherWith
                    (slideOutHorizontally(slideSpec) { width -> -direction * width / 4 } + fadeOut(fadeSpec)) using
                    SizeTransform(clip = false)
            }
        },
        contentAlignment = Alignment.TopCenter,
        modifier = modifier,
        label = "onboardingPage",
    ) { pageId ->
        pageStateHolder.SaveableStateProvider(pageId.name) {
            OnboardingPageContent(
                page = pageId,
                uiState = uiState,
                onPermissionAction = onPermissionAction,
                onCommunityAction = onCommunityAction,
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPageId,
    uiState: OnboardingUiState,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val pageModel = uiState.pages.first { it.id == page }
    val interactive = !uiState.isBusy && uiState.pages[uiState.currentPage].id == page
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = OnboardingContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page == OnboardingPageId.WELCOME) {
                    OnboardingEditionTags(uiState)
                }
                Text(
                    text = stringResource(pageModel.titleResId),
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    text = stringResource(pageModel.subtitleResId),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (page) {
                OnboardingPageId.WELCOME -> Unit
                OnboardingPageId.PERMISSIONS -> {
                    PermissionsContent(
                        permissions = uiState.permissions,
                        interactive = interactive,
                        onPermissionAction = onPermissionAction,
                    )
                }
                OnboardingPageId.LOGIN -> LoginContent(uiState.loginBenefits)
                OnboardingPageId.COMMUNITY -> {
                    CommunityContent(
                        actions = uiState.communityActions,
                        interactive = interactive,
                        onCommunityAction = onCommunityAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingEditionTags(uiState: OnboardingUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnboardingTag(
            text = stringResource(uiState.variantLabelResId),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        OnboardingTag(
            text = stringResource(R.string.onboarding_version_label, uiState.versionName),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OnboardingGlyph(
    @DrawableRes iconResId: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie4Sided,
) {
    Box(
        modifier = modifier.size(48.dp).background(containerColor, polygon.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

private enum class PermissionGroup(
    @StringRes val labelResId: Int,
    @StringRes val descriptionResId: Int,
    val members: Set<OnboardingPermissionId>,
    val showsSummary: Boolean,
) {
    ESSENTIALS(
        labelResId = R.string.onboarding_permission_group_essentials,
        descriptionResId = R.string.onboarding_permission_group_essentials_desc,
        members = setOf(OnboardingPermissionId.NOTIFICATIONS, OnboardingPermissionId.LOCAL_AUDIO),
        showsSummary = true,
    ),
    EXTRAS(
        labelResId = R.string.onboarding_permission_group_extras,
        descriptionResId = R.string.onboarding_permission_group_extras_desc,
        members =
            setOf(
                OnboardingPermissionId.MICROPHONE,
                OnboardingPermissionId.BLUETOOTH_CONNECT,
                OnboardingPermissionId.BLUETOOTH_SCAN,
                OnboardingPermissionId.APP_INSTALLATION,
            ),
        showsSummary = true,
    ),
    INCLUDED(
        labelResId = R.string.onboarding_permission_group_included,
        descriptionResId = R.string.onboarding_permission_group_included_desc,
        members =
            setOf(
                OnboardingPermissionId.NETWORK,
                OnboardingPermissionId.PLAYBACK_SERVICE,
                OnboardingPermissionId.AUDIO_SETTINGS,
                OnboardingPermissionId.DEVICE_AUDIO_CAPTURE,
            ),
        showsSummary = false,
    ),
}

@Composable
private fun PermissionsContent(
    permissions: List<OnboardingPermissionUiModel>,
    interactive: Boolean,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val groups = PermissionGroup.entries.filter { group -> permissions.any { it.id in group.members } }
    var selectedName by rememberSaveable { mutableStateOf(PermissionGroup.ESSENTIALS.name) }
    val selected = groups.firstOrNull { it.name == selectedName } ?: groups.firstOrNull()
    if (selected == null) return

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (groups.size > 1) {
            PermissionGroupSelector(
                groups = groups,
                selected = selected,
                onSelect = { selectedName = it.name },
            )
        }
        val animationsDisabled = LocalAnimationsDisabled.current
        val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                if (animationsDisabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)
                }
            },
            label = "onboardingPermissionGroup",
        ) { group ->
            val members = permissions.filter { it.id in group.members }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                PermissionGroupHeader(group = group, members = members)
                members.forEach { permission ->
                    PermissionRow(
                        permission = permission,
                        enabled = interactive && group == selected,
                        onPermissionAction = onPermissionAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupSelector(
    groups: List<PermissionGroup>,
    selected: PermissionGroup,
    onSelect: (PermissionGroup) -> Unit,
) {
    val singleShapes =
        ToggleButtonShapes(CircleShape, MaterialTheme.shapes.medium, CircleShape)
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        groups.forEachIndexed { index, group ->
            ToggleButton(
                checked = group == selected,
                onCheckedChange = { onSelect(group) },
                shapes =
                    when {
                        groups.size == 1 -> singleShapes
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == groups.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Tab },
            ) {
                Text(
                    text = stringResource(group.labelResId),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PermissionGroupHeader(
    group: PermissionGroup,
    members: List<OnboardingPermissionUiModel>,
) {
    val counted = members.filter { it.status != OnboardingPermissionStatus.UNAVAILABLE }
    val allowedCount =
        counted.count {
            it.status == OnboardingPermissionStatus.ALLOWED || it.status == OnboardingPermissionStatus.ALLOWED_BY_INSTALL
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(group.descriptionResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (group.showsSummary && counted.isNotEmpty()) {
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                OnboardingTag(
                    text = stringResource(R.string.onboarding_permission_group_summary, allowedCount, counted.size),
                    containerColor =
                        if (allowedCount == counted.size) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    contentColor =
                        if (allowedCount == counted.size) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: OnboardingPermissionUiModel,
    enabled: Boolean,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val allowed =
        permission.status == OnboardingPermissionStatus.ALLOWED ||
            permission.status == OnboardingPermissionStatus.ALLOWED_BY_INSTALL
    val blocked = permission.status == OnboardingPermissionStatus.BLOCKED
    val statusLabel =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> R.string.permission_status_allowed
            OnboardingPermissionStatus.NEEDS_ACTION -> R.string.onboarding_permission_optional
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> R.string.onboarding_permission_allowed_by_install
            OnboardingPermissionStatus.APPROVAL_AT_USE -> R.string.onboarding_permission_approval_at_use
            OnboardingPermissionStatus.BLOCKED -> R.string.onboarding_permission_blocked
            OnboardingPermissionStatus.UNAVAILABLE -> R.string.onboarding_permission_unavailable
        }
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val glyphContainer by animateColorAsState(
        targetValue =
            when {
                allowed -> MaterialTheme.colorScheme.primary
                blocked -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = colorSpec,
        label = "permissionGlyphContainer",
    )
    val glyphContent by animateColorAsState(
        targetValue =
            when {
                allowed -> MaterialTheme.colorScheme.onPrimary
                blocked -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = colorSpec,
        label = "permissionGlyphContent",
    )
    val statusColor =
        when {
            allowed -> MaterialTheme.colorScheme.primary
            blocked -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OnboardingGlyph(
            iconResId = permission.iconResId,
            containerColor = glyphContainer,
            contentColor = glyphContent,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(permission.titleResId), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(permission.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allowed || blocked) {
                    Icon(
                        painter = painterResource(if (allowed) R.drawable.check else R.drawable.block),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = stringResource(statusLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            }
            permission.action?.let { action ->
                val label =
                    if (action is OnboardingPermissionAction.RequestRuntimePermission) {
                        R.string.allow
                    } else {
                        R.string.onboarding_open_settings
                    }
                val actionDescription =
                    stringResource(
                        R.string.onboarding_permission_action_description,
                        stringResource(label),
                        stringResource(permission.titleResId),
                    )
                val actionModifier =
                    Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = actionDescription }
                if (action is OnboardingPermissionAction.RequestRuntimePermission) {
                    FilledTonalButton(
                        onClick = { onPermissionAction(action) },
                        enabled = enabled,
                        shapes = ButtonDefaults.shapes(),
                        modifier = actionModifier,
                    ) {
                        Text(stringResource(label))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPermissionAction(action) },
                        enabled = enabled,
                        shapes = ButtonDefaults.shapes(),
                        modifier = actionModifier,
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(benefits: List<OnboardingLoginBenefitUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = stringResource(R.string.onboarding_login_benefits_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.semantics { heading() },
        )
        benefits.forEach { benefit ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OnboardingGlyph(
                    iconResId = benefit.iconResId,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(benefit.titleResId), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(benefit.descriptionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.onboarding_login_optional),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommunityContent(
    actions: List<OnboardingCommunityActionUiModel>,
    interactive: Boolean,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val destinations = actions.filter { it.id != DonationActionId }
    val donation = actions.firstOrNull { it.id == DonationActionId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        destinations.forEach { action ->
            CommunityLink(
                action = action,
                enabled = interactive,
                onClick = { onCommunityAction(action) },
            )
        }
        if (donation != null) {
            Spacer(Modifier.height(8.dp))
            DonationCallout(
                donation = donation,
                enabled = interactive,
                onClick = { onCommunityAction(donation) },
            )
        }
    }
}

@Composable
private fun CommunityLink(
    action: OnboardingCommunityActionUiModel,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingGlyph(
                iconResId = action.iconResId,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                polygon = MaterialShapes.Circle,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(action.titleResId), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(action.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.link),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DonationCallout(
    donation: OnboardingCommunityActionUiModel,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OnboardingGlyph(
                iconResId = donation.iconResId,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                polygon = MaterialShapes.Puffy,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_support_optional),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onClick,
                    enabled = enabled,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                ) {
                    Text(stringResource(donation.titleResId))
                }
            }
        }
    }
}

@Composable
private fun OnboardingActions(
    uiState: OnboardingUiState,
    page: OnboardingPageId,
    onNext: (OnboardingPageId) -> Unit,
    onLogin: () -> Unit,
    maxHeight: Dp,
) {
    val primaryLabel =
        when {
            uiState.isCompleting -> R.string.onboarding_saving
            uiState.completionErrorResId != null && page == OnboardingPageId.COMMUNITY -> R.string.retry
            page == OnboardingPageId.WELCOME -> R.string.onboarding_get_started
            page == OnboardingPageId.LOGIN -> R.string.onboarding_sign_in
            page == OnboardingPageId.COMMUNITY -> R.string.onboarding_finish
            else -> R.string.onboarding_continue
        }
    val showsArrow = !uiState.isCompleting && uiState.completionErrorResId == null && page != OnboardingPageId.LOGIN
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (page == OnboardingPageId.COMMUNITY) {
            uiState.completionErrorResId?.let { message ->
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Row(
            modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page == OnboardingPageId.LOGIN) {
                OutlinedButton(
                    onClick = { onNext(page) },
                    enabled = !uiState.isBusy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text(
                        stringResource(R.string.onboarding_login_skip),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = { if (page == OnboardingPageId.LOGIN) onLogin() else onNext(page) },
                enabled = !uiState.isBusy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    stringResource(primaryLabel),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showsArrow) {
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Icon(
                        painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
    }
}

private data class OnboardingPalette(
    val stage: Color,
    val onStage: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
@ReadOnlyComposable
private fun OnboardingPageId.palette(): OnboardingPalette {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        OnboardingPageId.PERMISSIONS -> {
            OnboardingPalette(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary, colors.onSecondary)
        }
        OnboardingPageId.LOGIN -> {
            OnboardingPalette(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary, colors.onTertiary)
        }
        OnboardingPageId.WELCOME, OnboardingPageId.COMMUNITY -> {
            OnboardingPalette(colors.primaryContainer, colors.onPrimaryContainer, colors.primary, colors.onPrimary)
        }
    }
}

private fun OnboardingPageId.heroPolygon(): RoundedPolygon =
    when (this) {
        OnboardingPageId.WELCOME -> MaterialShapes.Cookie12Sided
        OnboardingPageId.PERMISSIONS -> MaterialShapes.Clover4Leaf
        OnboardingPageId.LOGIN -> MaterialShapes.SoftBurst
        OnboardingPageId.COMMUNITY -> MaterialShapes.Flower
    }

@DrawableRes
private fun OnboardingPageId.heroIconResId(): Int =
    when (this) {
        OnboardingPageId.WELCOME -> R.drawable.app_icon_small
        OnboardingPageId.PERMISSIONS -> R.drawable.security
        OnboardingPageId.LOGIN -> R.drawable.account
        OnboardingPageId.COMMUNITY -> R.drawable.favorite_border
    }

private const val DonationActionId = "donate"
private const val HeroRotationStep = 45f
private val OnboardingSheetCorner = 32.dp
private val OnboardingContentMaxWidth = 560.dp
