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

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.androidauto.AndroidAutoActionSlot
import moe.rukamori.archivetune.androidauto.AndroidAutoConfiguration
import moe.rukamori.archivetune.androidauto.AndroidAutoConnectionStatus
import moe.rukamori.archivetune.androidauto.AndroidAutoCustomAction
import moe.rukamori.archivetune.androidauto.AndroidAutoNetworkState
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.viewmodels.AndroidAutoSettingsAction
import moe.rukamori.archivetune.viewmodels.AndroidAutoSettingsEvent
import moe.rukamori.archivetune.viewmodels.AndroidAutoSettingsState
import moe.rukamori.archivetune.viewmodels.AndroidAutoSettingsUiModel
import moe.rukamori.archivetune.viewmodels.AndroidAutoSettingsViewModel

private val ScreenGutter = 16.dp
private val HeroCornerRadius = 36.dp
private val ConsoleCornerRadius = 24.dp
private val TileCornerRest = 20.dp
private val TileCornerChecked = 32.dp
private val GlyphSize = 64.dp
private val BadgeSize = 40.dp
private val ConsoleKeySize = 36.dp
private val ConsolePlaySize = 52.dp
private val ConsoleSlotSize = 48.dp
private val ConsoleCompactWidth = 248.dp
private val TileStackWidth = 340.dp
private const val TileStackFontScale = 1.3f
private const val SourceCount = 3
private const val BackdropRotationMillis = 24_000

private val primaryAndroidAutoActions =
    AndroidAutoCustomAction.entries.filterNot { it == AndroidAutoCustomAction.NONE }
private val secondaryAndroidAutoActions = AndroidAutoCustomAction.entries

private enum class SignalTone { Positive, Caution, Warning }

@Immutable
private data class Signal(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val tone: SignalTone,
)

@Composable
fun AndroidAutoSettings(
    navController: NavController,
    viewModel: AndroidAutoSettingsViewModel = hiltViewModel(),
) {
    val onBack = remember(navController) { { navController.navigateUp(); Unit } }
    AndroidAutoSettingsRoute(onBack = onBack, viewModel = viewModel)
}

@Composable
fun AndroidAutoSettingsRoute(
    onBack: () -> Unit,
    viewModel: AndroidAutoSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onAction(AndroidAutoSettingsAction.PermissionResult) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onAction(AndroidAutoSettingsAction.PermissionResult) }
    LaunchedEffect(viewModel, context, permissionLauncher) {
        viewModel.events.collectLatest { event ->
            when (event) {
                AndroidAutoSettingsEvent.RequestAudioPermission -> permissionLauncher.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                )
                AndroidAutoSettingsEvent.OpenAppPermissions -> runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }.onFailure {
                    onAction(AndroidAutoSettingsAction.ExternalActionFailed)
                }
            }
        }
    }
    AndroidAutoSettingsContent(state = state, onAction = onAction, onBack = onBack)
}

@Composable
private fun AndroidAutoSettingsContent(
    state: AndroidAutoSettingsState,
    onAction: (AndroidAutoSettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.android_auto),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = stringResource(R.string.android_auto_settings_subtitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            label = "androidAutoSettingsState",
        ) { target ->
            when (target) {
                AndroidAutoSettingsState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                is AndroidAutoSettingsState.Success -> AndroidAutoSettingsBody(
                    model = target.model,
                    onAction = onAction,
                    contentPadding = padding,
                )
                AndroidAutoSettingsState.Empty -> AndroidAutoSettingsFailure(
                    onAction = onAction,
                    modifier = Modifier.padding(padding),
                )
                is AndroidAutoSettingsState.Error -> AndroidAutoSettingsFailure(
                    onAction = onAction,
                    modifier = Modifier.padding(padding),
                    messageRes = target.messageRes,
                )
            }
        }
    }
}

@Composable
private fun AndroidAutoSettingsBody(
    model: AndroidAutoSettingsUiModel,
    onAction: (AndroidAutoSettingsAction) -> Unit,
    contentPadding: PaddingValues,
) {
    val configuration = model.snapshot.configuration
    val reducedMotion = LocalAnimationsDisabled.current
    val motion = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val enabled = !model.busy
    var editingSlot by rememberSaveable { mutableStateOf(AndroidAutoActionSlot.PRIMARY) }
    val entrance = rememberEntranceProgress(reducedMotion)
    val slotActions =
        if (editingSlot == AndroidAutoActionSlot.PRIMARY) primaryAndroidAutoActions else secondaryAndroidAutoActions
    val selectedAction = configuration.actionFor(editingSlot)
    val otherSlot = editingSlot.other()
    val otherAction = configuration.actionFor(otherSlot)
    val enabledSources = listOf(
        configuration.onlineRecommendations,
        configuration.onlineVoiceSearch,
        configuration.localSongs,
    ).count { it }
    val onSlotSelected = remember { { slot: AndroidAutoActionSlot -> editingSlot = slot } }
    val onSetAction = remember(onAction, editingSlot) {
        { action: AndroidAutoCustomAction ->
            onAction(AndroidAutoSettingsAction.SetAction(slot = editingSlot, action = action))
        }
    }
    val onOpenPermissions = remember(onAction) { { onAction(AndroidAutoSettingsAction.OpenAppPermissions) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + ScreenGutter,
                end = contentPadding.calculateEndPadding(layoutDirection) + ScreenGutter,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + SettingsDimensions.ScreenBottomPadding,
            ),
    ) {
        item(key = "hero", contentType = "hero") {
            CarDashboardHero(
                model = model,
                editingSlot = editingSlot,
                onSlotSelected = onSlotSelected,
                reducedMotion = reducedMotion,
                modifier = Modifier.entrance(entrance, order = 0),
            )
        }
        item(key = "sources_header", contentType = "header") {
            SectionHeader(
                title = R.string.android_auto_content,
                trailing =
                    pluralStringResource(
                        R.plurals.android_auto_sources_enabled,
                        enabledSources,
                        enabledSources,
                        SourceCount,
                    ),
                modifier = Modifier.entrance(entrance, order = 1),
            )
        }
        item(key = "sources", contentType = "sources") {
            SourceTiles(
                configuration = configuration,
                hasLocalAudioPermission = model.snapshot.hasLocalAudioPermission,
                enabled = enabled,
                onAction = onAction,
                modifier = Modifier.entrance(entrance, order = 2),
            )
        }
        item(key = "data_header", contentType = "header") {
            SectionHeader(
                title = R.string.android_auto_data,
                modifier = Modifier.entrance(entrance, order = 3),
            )
        }
        item(key = "data", contentType = "data") {
            DataRules(
                model = model,
                enabled = enabled,
                onAction = onAction,
                modifier = Modifier.entrance(entrance, order = 4),
            )
        }
        item(key = "controls_header", contentType = "header") {
            SectionHeader(
                title = R.string.android_auto_controls,
                modifier = Modifier.entrance(entrance, order = 5),
            )
        }
        item(key = "slot_switcher", contentType = "slot_switcher") {
            SlotSwitcher(
                configuration = configuration,
                editingSlot = editingSlot,
                onSlotSelected = onSlotSelected,
                modifier = Modifier.padding(bottom = 12.dp).entrance(entrance, order = 6),
            )
        }
        itemsIndexed(
            items = slotActions,
            key = { _, action -> "action_${action.name}" },
            contentType = { _, _ -> "action" },
        ) { index, action ->
            ActionOptionRow(
                action = action,
                selected = action == selectedAction,
                usedBy = otherSlot.takeIf { action == otherAction && action != selectedAction && action != AndroidAutoCustomAction.NONE },
                index = index,
                count = slotActions.size,
                enabled = enabled,
                onSelect = onSetAction,
                modifier =
                    Modifier
                        .animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                        )
                        .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)
                        .entrance(entrance, order = 7),
            )
        }
        item(key = "system_header", contentType = "header") {
            SectionHeader(title = R.string.android_auto_system)
        }
        item(key = "system", contentType = "system") {
            SegmentedListItem(
                onClick = onOpenPermissions,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                leadingContent = { RowBadge(icon = R.drawable.security, active = false) },
                supportingContent = { Text(stringResource(R.string.android_auto_app_permissions_desc)) },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Text(stringResource(R.string.android_auto_app_permissions))
            }
        }
    }
}

@Composable
private fun CarDashboardHero(
    model: AndroidAutoSettingsUiModel,
    editingSlot: AndroidAutoActionSlot,
    onSlotSelected: (AndroidAutoActionSlot) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = model.snapshot.connectionStatus
    val connected = status != AndroidAutoConnectionStatus.DISCONNECTED
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (connected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = motion.slowEffectsSpec(),
        label = "heroContainer",
    )
    val content by animateColorAsState(
        targetValue = if (connected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = motion.slowEffectsSpec(),
        label = "heroContent",
    )
    val rotation =
        if (connected && !reducedMotion) {
            rememberInfiniteTransition(label = "heroRotation").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(BackdropRotationMillis, easing = LinearEasing)),
                label = "heroRotationAngle",
            )
        } else {
            null
        }
    val backdropShape = MaterialShapes.Cookie12Sided.toShape()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HeroCornerRadius),
        color = container,
        contentColor = content,
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 64.dp, y = (-72).dp)
                    .size(220.dp)
                    .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                    .background(content.copy(alpha = 0.06f), backdropShape),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ConnectionGlyph(connected = connected, rotation = rotation)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.android_auto_connection),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(alpha = 0.78f),
                        )
                        AnimatedContent(
                            targetState = status,
                            transitionSpec = {
                                (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec()))
                                    .togetherWith(
                                        slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()),
                                    )
                            },
                            label = "heroStatus",
                        ) { target ->
                            Text(
                                text = stringResource(target.labelRes()),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                            )
                        }
                    }
                }
                DashboardConsole(
                    configuration = model.snapshot.configuration,
                    editingSlot = editingSlot,
                    onSlotSelected = onSlotSelected,
                )
                HeroSignals(model = model)
            }
        }
    }
}

@Composable
private fun ConnectionGlyph(
    connected: Boolean,
    rotation: State<Float>?,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "glyphMorph",
    )
    val fill by animateColorAsState(
        targetValue = if (connected) colors.primary else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "glyphFill",
    )
    val tint by animateColorAsState(
        targetValue = if (connected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "glyphTint",
    )
    Box(Modifier.size(GlyphSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                .background(fill, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))),
        )
        Icon(
            painter = painterResource(R.drawable.directions_car),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun DashboardConsole(
    configuration: AndroidAutoConfiguration,
    editingSlot: AndroidAutoActionSlot,
    onSlotSelected: (AndroidAutoActionSlot) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val primaryLabel = stringResource(configuration.primaryAction.labelResource())
    val caption =
        if (configuration.secondaryAction == AndroidAutoCustomAction.NONE) {
            stringResource(R.string.android_auto_preview_button, primaryLabel)
        } else {
            stringResource(
                R.string.android_auto_preview_buttons,
                primaryLabel,
                stringResource(configuration.secondaryAction.labelResource()),
            )
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ConsoleCornerRadius),
        color = colors.surfaceContainerLowest,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < ConsoleCompactWidth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.clearAndSetSemantics {},
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!compact) ConsoleKey(R.drawable.skip_previous)
                        Box(
                            Modifier
                                .size(ConsolePlaySize)
                                .background(colors.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        if (!compact) ConsoleKey(R.drawable.skip_next)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AndroidAutoActionSlot.entries.forEach { slot ->
                            ConsoleSlotKey(
                                slot = slot,
                                action = configuration.actionFor(slot),
                                selected = slot == editingSlot,
                                onClick = { onSlotSelected(slot) },
                            )
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = caption,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "consoleCaption",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConsoleKey(@DrawableRes icon: Int) {
    Box(
        Modifier
            .size(ConsoleKeySize)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ConsoleSlotKey(
    slot: AndroidAutoActionSlot,
    action: AndroidAutoCustomAction,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (selected) 14.dp else ConsoleSlotSize / 2,
        animationSpec = motion.fastSpatialSpec(),
        label = "slotCorner",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.tertiaryContainer else colors.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "slotContainer",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) colors.tertiary else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "slotRing",
    )
    val tint = if (selected) colors.onTertiaryContainer else colors.onSurfaceVariant
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val description = stringResource(
        R.string.android_auto_slot_value,
        stringResource(slot.titleRes()),
        stringResource(action.labelResource()),
    )
    Box(
        modifier =
            Modifier
                .size(ConsoleSlotSize)
                .clip(shape)
                .background(container)
                .border(2.dp, ring, shape)
                .selectable(selected = selected, onClick = onClick, role = Role.Tab)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f) + fadeIn(motion.fastEffectsSpec()))
                    .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "slotIcon",
        ) { target ->
            if (target == AndroidAutoCustomAction.NONE) {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(2.dp, colors.outline, CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(target.iconResource()),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroSignals(model: AndroidAutoSettingsUiModel) {
    val signals = buildList {
        add(model.network.toSignal())
        if (model.snapshot.configuration.localSongs) {
            add(
                if (model.snapshot.hasLocalAudioPermission) {
                    Signal(R.drawable.lock_open, R.string.android_auto_audio_access_ready, SignalTone.Positive)
                } else {
                    Signal(R.drawable.lock, R.string.android_auto_audio_access_needed, SignalTone.Warning)
                },
            )
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        signals.forEach { signal -> SignalPill(signal) }
    }
}

@Composable
private fun SignalPill(signal: Signal) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (targetContainer, targetContent) = when (signal.tone) {
        SignalTone.Positive -> colors.secondaryContainer to colors.onSecondaryContainer
        SignalTone.Caution -> colors.tertiaryContainer to colors.onTertiaryContainer
        SignalTone.Warning -> colors.errorContainer to colors.onErrorContainer
    }
    val container by animateColorAsState(targetContainer, motion.defaultEffectsSpec(), label = "pillContainer")
    val content by animateColorAsState(targetContent, motion.defaultEffectsSpec(), label = "pillContent")
    Surface(shape = CircleShape, color = container, contentColor = content) {
        AnimatedContent(
            targetState = signal,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "pillContent",
        ) { target ->
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(target.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(target.label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val motion = MaterialTheme.motionScheme
    val accent = MaterialShapes.Cookie4Sided.toShape()
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 28.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.tertiary, accent),
        )
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) {
            AnimatedContent(
                targetState = trailing,
                transitionSpec = {
                    (slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(
                            slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
                        )
                },
                label = "headerTrailing",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SourceTiles(
    configuration: AndroidAutoConfiguration,
    hasLocalAudioPermission: Boolean,
    enabled: Boolean,
    onAction: (AndroidAutoSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRecommendations = remember(onAction) {
        { value: Boolean -> onAction(AndroidAutoSettingsAction.SetOnlineRecommendations(value)) }
    }
    val onVoiceSearch = remember(onAction) {
        { value: Boolean -> onAction(AndroidAutoSettingsAction.SetOnlineVoiceSearch(value)) }
    }
    val onLocalSongs = remember(onAction, hasLocalAudioPermission) {
        { value: Boolean ->
            onAction(AndroidAutoSettingsAction.SetLocalSongs(value))
            if (value && !hasLocalAudioPermission) onAction(AndroidAutoSettingsAction.RequestAudioPermission)
        }
    }
    val onGrant = remember(onAction) { { onAction(AndroidAutoSettingsAction.RequestAudioPermission) } }
    val fontScale = LocalDensity.current.fontScale

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < TileStackWidth || fontScale > TileStackFontScale
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceTile(
                        icon = R.drawable.discover_tune,
                        title = R.string.android_auto_online_recommendations,
                        description = R.string.android_auto_online_recommendations_desc,
                        checked = configuration.onlineRecommendations,
                        enabled = enabled,
                        onCheckedChange = onRecommendations,
                        wide = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SourceTile(
                        icon = R.drawable.mic,
                        title = R.string.android_auto_online_voice_search,
                        description = R.string.android_auto_online_voice_search_desc,
                        checked = configuration.onlineVoiceSearch,
                        enabled = enabled,
                        onCheckedChange = onVoiceSearch,
                        wide = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceTile(
                        icon = R.drawable.discover_tune,
                        title = R.string.android_auto_online_recommendations,
                        description = R.string.android_auto_online_recommendations_desc,
                        checked = configuration.onlineRecommendations,
                        enabled = enabled,
                        onCheckedChange = onRecommendations,
                        wide = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    SourceTile(
                        icon = R.drawable.mic,
                        title = R.string.android_auto_online_voice_search,
                        description = R.string.android_auto_online_voice_search_desc,
                        checked = configuration.onlineVoiceSearch,
                        enabled = enabled,
                        onCheckedChange = onVoiceSearch,
                        wide = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        SourceTile(
            icon = R.drawable.library_music,
            title = R.string.android_auto_local_songs,
            description = R.string.android_auto_local_songs_desc,
            checked = configuration.localSongs,
            enabled = enabled,
            onCheckedChange = onLocalSongs,
            wide = true,
            modifier = Modifier.fillMaxWidth(),
            footerVisible = configuration.localSongs && !hasLocalAudioPermission,
            footer = { PermissionStrip(onGrant = onGrant) },
        )
    }
}

@Composable
private fun SourceTile(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    footerVisible: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (checked) TileCornerChecked else TileCornerRest,
        animationSpec = motion.defaultSpatialSpec(),
        label = "tileCorner",
    )
    val container by animateColorAsState(
        targetValue = if (checked) colors.secondaryContainer else colors.surfaceContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContainer",
    )
    val content by animateColorAsState(
        targetValue = if (checked) colors.onSecondaryContainer else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContent",
    )
    val titleStyle =
        if (checked) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium
    val switch: @Composable () -> Unit = {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent =
                if (checked) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
        )
    }
    val texts: @Composable (Modifier) -> Unit = { textModifier ->
        Column(textModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(title), style = titleStyle)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = container,
        contentColor = content,
    ) {
        Column {
            val toggleModifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                    .padding(16.dp)
            if (wide) {
                Row(
                    modifier = toggleModifier,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowBadge(icon = icon, active = checked)
                    texts(Modifier.weight(1f))
                    switch()
                }
            } else {
                Column(
                    modifier = toggleModifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RowBadge(icon = icon, active = checked)
                        switch()
                    }
                    texts(Modifier.fillMaxWidth())
                }
            }
            if (footer != null) {
                AnimatedVisibility(
                    visible = footerVisible,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    footer()
                }
            }
        }
    }
}

@Composable
private fun PermissionStrip(onGrant: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(TileCornerChecked - 8.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.error), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.android_auto_audio_permission_missing),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onGrant, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.allow))
            }
        }
    }
}

@Composable
private fun DataRules(
    model: AndroidAutoSettingsUiModel,
    enabled: Boolean,
    onAction: (AndroidAutoSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = model.snapshot.configuration
    val onMeteredPlayback = remember(onAction) {
        { value: Boolean -> onAction(AndroidAutoSettingsAction.SetMeteredPlayback(value)) }
    }
    val onMeteredArtwork = remember(onAction) {
        { value: Boolean -> onAction(AndroidAutoSettingsAction.SetMeteredArtwork(value)) }
    }
    Column(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            DataRuleRow(
                icon = R.drawable.android_cell,
                title = R.string.android_auto_metered_playback,
                description = R.string.android_auto_metered_playback_desc,
                checked = configuration.meteredPlayback,
                enabled = enabled,
                onCheckedChange = onMeteredPlayback,
                index = 0,
            )
            DataRuleRow(
                icon = R.drawable.image,
                title = R.string.android_auto_metered_artwork,
                description = R.string.android_auto_metered_artwork_desc,
                checked = configuration.meteredArtwork,
                enabled = enabled,
                onCheckedChange = onMeteredArtwork,
                index = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        NetworkVerdict(model = model)
    }
}

@Composable
private fun DataRuleRow(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = 2),
        enabled = enabled,
        leadingContent = { RowBadge(icon = icon, active = checked) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    ) {
        Text(stringResource(title))
    }
}

@Composable
private fun NetworkVerdict(model: AndroidAutoSettingsUiModel) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ConsoleCornerRadius),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(model.network.toSignal().icon),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.android_auto_verdict_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            AnimatedContent(
                targetState = model.network.online,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "verdictOnline",
            ) { online ->
                if (online) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VerdictItem(
                            allowed = model.onlinePlaybackAllowed,
                            allowedLabel = R.string.android_auto_verdict_playback_allowed,
                            pausedLabel = R.string.android_auto_verdict_playback_paused,
                            modifier = Modifier.weight(1f),
                        )
                        VerdictItem(
                            allowed = model.remoteArtworkAllowed,
                            allowedLabel = R.string.android_auto_verdict_artwork_allowed,
                            pausedLabel = R.string.android_auto_verdict_artwork_paused,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.android_auto_verdict_offline),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictItem(
    allowed: Boolean,
    @StringRes allowedLabel: Int,
    @StringRes pausedLabel: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (allowed) colors.primaryContainer else colors.errorContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "verdictContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (allowed) colors.onPrimaryContainer else colors.onErrorContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "verdictTint",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = allowed,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()))
                },
                label = "verdictIcon",
            ) { isAllowed ->
                Icon(
                    painter = painterResource(if (isAllowed) R.drawable.check else R.drawable.block),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = stringResource(if (allowed) allowedLabel else pausedLabel),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SlotSwitcher(
    configuration: AndroidAutoConfiguration,
    editingSlot: AndroidAutoActionSlot,
    onSlotSelected: (AndroidAutoActionSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val height = ButtonDefaults.MediumContainerHeight
    val slots = AndroidAutoActionSlot.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        slots.forEachIndexed { index, slot ->
            ToggleButton(
                checked = slot == editingSlot,
                onCheckedChange = { onSlotSelected(slot) },
                shapes =
                    if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = height)
                        .semantics { role = Role.RadioButton },
            ) {
                AnimatedContent(
                    targetState = configuration.actionFor(slot),
                    transitionSpec = {
                        (scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f) + fadeIn(motion.fastEffectsSpec()))
                            .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "switcherIcon",
                ) { action ->
                    Icon(
                        painter = painterResource(action.iconResource()),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                    )
                }
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
                Text(
                    text = stringResource(slot.shortLabelRes()),
                    style = ButtonDefaults.textStyleFor(height),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionOptionRow(
    action: AndroidAutoCustomAction,
    selected: Boolean,
    usedBy: AndroidAutoActionSlot?,
    index: Int,
    count: Int,
    enabled: Boolean,
    onSelect: (AndroidAutoCustomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = selected,
        onClick = { onSelect(action) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        enabled = enabled,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = colors.surfaceContainer,
                selectedContainerColor = colors.tertiaryContainer,
            ),
        leadingContent = { RowBadge(icon = action.iconResource(), active = selected, accent = true) },
        supportingContent = {
            Text(
                stringResource(
                    when (usedBy) {
                        AndroidAutoActionSlot.PRIMARY -> R.string.android_auto_action_used_by_primary
                        AndroidAutoActionSlot.SECONDARY -> R.string.android_auto_action_used_by_secondary
                        null -> action.descriptionResource()
                    },
                ),
            )
        },
        trailingContent = {
            Icon(
                painter =
                    painterResource(
                        if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
                    ),
                contentDescription = null,
                tint = if (selected) colors.tertiary else colors.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text = stringResource(action.labelResource()),
            style = if (selected) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RowBadge(
    @DrawableRes icon: Int,
    active: Boolean,
    accent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie7Sided) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "badgeMorph",
    )
    val activeFill = if (accent) colors.tertiary else colors.primary
    val activeTint = if (accent) colors.onTertiary else colors.onPrimary
    val fill by animateColorAsState(
        targetValue = if (active) activeFill else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "badgeFill",
    )
    val tint by animateColorAsState(
        targetValue = if (active) activeTint else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "badgeTint",
    )
    Box(
        modifier =
            Modifier
                .size(BadgeSize)
                .graphicsLayer { rotationZ = progress * 45f }
                .background(fill, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -progress * 45f },
        )
    }
}

@Composable
private fun AndroidAutoSettingsFailure(
    onAction: (AndroidAutoSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes messageRes: Int = R.string.android_auto_settings_load_failed,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(colors.errorContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = remember(onAction) { { onAction(AndroidAutoSettingsAction.Retry) } },
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.retry_button))
        }
    }
}

@Composable
private fun rememberEntranceProgress(reducedMotion: Boolean): State<Float> {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played || reducedMotion) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(1f)
        } else if (!played) {
            progress.animateTo(1f, spec)
        }
        played = true
    }
    return progress.asState()
}

private fun Modifier.entrance(progress: State<Float>, order: Int): Modifier =
    graphicsLayer {
        val value = progress.value
        translationY = (1f - value) * (order + 1) * 28.dp.toPx()
        alpha = value.coerceIn(0f, 1f)
    }

private fun AndroidAutoNetworkState.toSignal(): Signal = when {
    !online -> Signal(R.drawable.cloud_off, R.string.android_auto_network_offline, SignalTone.Warning)
    metered -> Signal(R.drawable.android_cell, R.string.android_auto_network_metered, SignalTone.Caution)
    else -> Signal(R.drawable.wifi, R.string.android_auto_network_unmetered, SignalTone.Positive)
}

private fun AndroidAutoConfiguration.actionFor(slot: AndroidAutoActionSlot): AndroidAutoCustomAction = when (slot) {
    AndroidAutoActionSlot.PRIMARY -> primaryAction
    AndroidAutoActionSlot.SECONDARY -> secondaryAction
}

private fun AndroidAutoActionSlot.other(): AndroidAutoActionSlot = when (this) {
    AndroidAutoActionSlot.PRIMARY -> AndroidAutoActionSlot.SECONDARY
    AndroidAutoActionSlot.SECONDARY -> AndroidAutoActionSlot.PRIMARY
}

@StringRes
private fun AndroidAutoActionSlot.titleRes(): Int = when (this) {
    AndroidAutoActionSlot.PRIMARY -> R.string.android_auto_primary_action
    AndroidAutoActionSlot.SECONDARY -> R.string.android_auto_secondary_action
}

@StringRes
private fun AndroidAutoActionSlot.shortLabelRes(): Int = when (this) {
    AndroidAutoActionSlot.PRIMARY -> R.string.android_auto_slot_primary
    AndroidAutoActionSlot.SECONDARY -> R.string.android_auto_slot_secondary
}

@StringRes
private fun AndroidAutoConnectionStatus.labelRes(): Int = when (this) {
    AndroidAutoConnectionStatus.DISCONNECTED -> R.string.android_auto_disconnected
    AndroidAutoConnectionStatus.PROJECTION -> R.string.android_auto_projection
    AndroidAutoConnectionStatus.NATIVE -> R.string.android_auto_native
}

@StringRes
private fun AndroidAutoCustomAction.labelResource(): Int = when (this) {
    AndroidAutoCustomAction.LIKE -> R.string.android_auto_action_like
    AndroidAutoCustomAction.START_RADIO -> R.string.android_auto_action_radio
    AndroidAutoCustomAction.SHUFFLE -> R.string.android_auto_action_shuffle
    AndroidAutoCustomAction.REPEAT -> R.string.android_auto_action_repeat
    AndroidAutoCustomAction.NONE -> R.string.android_auto_action_none
}

@StringRes
private fun AndroidAutoCustomAction.descriptionResource(): Int = when (this) {
    AndroidAutoCustomAction.LIKE -> R.string.android_auto_action_like_desc
    AndroidAutoCustomAction.START_RADIO -> R.string.android_auto_action_radio_desc
    AndroidAutoCustomAction.SHUFFLE -> R.string.android_auto_action_shuffle_desc
    AndroidAutoCustomAction.REPEAT -> R.string.android_auto_action_repeat_desc
    AndroidAutoCustomAction.NONE -> R.string.android_auto_action_none_desc
}

@DrawableRes
private fun AndroidAutoCustomAction.iconResource(): Int = when (this) {
    AndroidAutoCustomAction.LIKE -> R.drawable.favorite
    AndroidAutoCustomAction.START_RADIO -> R.drawable.radio
    AndroidAutoCustomAction.SHUFFLE -> R.drawable.shuffle
    AndroidAutoCustomAction.REPEAT -> R.drawable.repeat
    AndroidAutoCustomAction.NONE -> R.drawable.more_horiz
}
