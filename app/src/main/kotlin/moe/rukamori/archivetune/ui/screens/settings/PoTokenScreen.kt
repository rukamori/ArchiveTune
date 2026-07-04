/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.PoTokenSourceUrlKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.WebClientPoTokenEnabledKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.PoTokenEvent
import moe.rukamori.archivetune.viewmodels.PoTokenState
import moe.rukamori.archivetune.viewmodels.PoTokenViewModel

private const val DEFAULT_EXTRACT_URL = "https://youtube.com/account"
private val MaxContentWidth = 840.dp
private val MaxSheetWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoTokenScreen(
    navController: NavController,
    viewModel: PoTokenViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = appBarScrollBehavior()
    val tokenState by viewModel.state.collectAsStateWithLifecycle()
    val isRegenerateSheetVisible by viewModel.isRegenerateSheetVisible.collectAsStateWithLifecycle()

    var (webClientPoTokenEnabled, onWebClientPoTokenEnabledChange) =
        rememberPreference(
            WebClientPoTokenEnabledKey,
            defaultValue = false,
        )
    var (sourceUrl, onSourceUrlChange) =
        rememberPreference(
            PoTokenSourceUrlKey,
            defaultValue = "",
        )
    var (storedGvsToken, onStoredGvsTokenChange) =
        rememberPreference(
            PoTokenGvsKey,
            defaultValue = "",
        )
    var (storedPlayerToken, onStoredPlayerTokenChange) =
        rememberPreference(
            PoTokenPlayerKey,
            defaultValue = "",
        )
    var (storedVisitorData, onStoredVisitorDataChange) =
        rememberPreference(
            VisitorDataKey,
            defaultValue = "",
        )

    val extractionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val gvsToken =
                    result.data
                        ?.getStringExtra(PoTokenExtractionActivity.EXTRA_GVS_TOKEN)
                        .orEmpty()
                val playerToken =
                    result.data
                        ?.getStringExtra(PoTokenExtractionActivity.EXTRA_PLAYER_TOKEN)
                        .orEmpty()
                val visitorData =
                    result.data
                        ?.getStringExtra(PoTokenExtractionActivity.EXTRA_VISITOR_DATA)
                        .orEmpty()

                if (gvsToken.isNotBlank() && playerToken.isNotBlank() && visitorData.isNotBlank()) {
                    viewModel.onTokensExtracted(
                        visitorData = visitorData,
                        poToken = gvsToken,
                        playerToken = playerToken,
                    )
                } else {
                    viewModel.onExtractionError(context.getString(R.string.token_generation_failed))
                }
            } else {
                val error =
                    result.data
                        ?.getStringExtra(PoTokenExtractionActivity.EXTRA_ERROR)
                        .orEmpty()
                if (error.isNotBlank()) {
                    viewModel.onExtractionError(error)
                } else {
                    viewModel.onExtractionCancelled()
                }
            }
        }

    val launchExtraction =
        remember(context, extractionLauncher, sourceUrl, viewModel) {
            {
                viewModel.onExtractionStarted()
                val launchUrl = sourceUrl.takeIf(String::isNotBlank) ?: DEFAULT_EXTRACT_URL
                val intent =
                    Intent(context, PoTokenExtractionActivity::class.java).apply {
                        putExtra(PoTokenExtractionActivity.EXTRA_SOURCE_URL, launchUrl)
                    }
                extractionLauncher.launch(intent)
            }
        }

    val tokenCopiedMessage = stringResource(R.string.token_copied)
    val copyToken =
        remember(clipboardManager, coroutineScope, snackbarHostState, tokenCopiedMessage) {
            { token: String ->
                clipboardManager.setText(AnnotatedString(token))
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(tokenCopiedMessage)
                }
                Unit
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PoTokenEvent.TokensGenerated -> {
                    onStoredGvsTokenChange(event.gvsToken)
                    onStoredPlayerTokenChange(event.playerToken)
                    onStoredVisitorDataChange(event.visitorData)
                    snackbarHostState.showSnackbar(context.getString(R.string.tokens_generated))
                }

                is PoTokenEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val displayGvsToken =
        when (val state = tokenState) {
            is PoTokenState.Success -> state.gvsToken
            else -> storedGvsToken
        }
    val displayPlayerToken =
        when (val state = tokenState) {
            is PoTokenState.Success -> state.playerToken
            else -> storedPlayerToken
        }
    val displayVisitorData =
        when (val state = tokenState) {
            is PoTokenState.Success -> state.visitorData
            else -> storedVisitorData
        }

    if (isRegenerateSheetVisible) {
        RegenerateTokenSheet(
            sourceUrl = sourceUrl,
            isGenerating = tokenState is PoTokenState.Loading,
            onSourceUrlChange = onSourceUrlChange,
            onDismiss = viewModel::dismissRegenerateSheet,
            onRegenerate = {
                viewModel.dismissRegenerateSheet()
                launchExtraction()
            },
        )
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.po_token_generation),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "po_token_toggle",
                contentType = "setting",
            ) {
                CenteredContent {
                    PreferenceGroup {
                        item {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.web_client_po_token)) },
                                description = stringResource(R.string.web_client_po_token_desc),
                                icon = { Icon(painterResource(R.drawable.token), null) },
                                checked = webClientPoTokenEnabled,
                                onCheckedChange = onWebClientPoTokenEnabledChange,
                            )
                        }
                    }
                }
            }

            if (webClientPoTokenEnabled) {
                item(
                    key = "generated_tokens_title",
                    contentType = "section_title",
                ) {
                    CenteredContent(
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.generated_tokens),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                item(
                    key = "generated_tokens",
                    contentType = "token_group",
                ) {
                    CenteredContent(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TokenListItem(
                                label = stringResource(R.string.po_token_gvs),
                                token = displayGvsToken,
                                index = 0,
                                count = 3,
                                onCopy = copyToken,
                            )
                            TokenListItem(
                                label = stringResource(R.string.po_token_player),
                                token = displayPlayerToken,
                                index = 1,
                                count = 3,
                                onCopy = copyToken,
                            )
                            TokenListItem(
                                label = stringResource(R.string.visitor_data),
                                token = displayVisitorData,
                                index = 2,
                                count = 3,
                                onCopy = copyToken,
                            )
                        }
                    }
                }

                item(
                    key = "regenerate_token",
                    contentType = "primary_action",
                ) {
                    CenteredContent(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Button(
                            onClick = viewModel::showRegenerateSheet,
                            enabled = tokenState !is PoTokenState.Loading,
                            modifier = Modifier.fillMaxWidth(),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.regenerate))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MaxContentWidth),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun TokenListItem(
    label: String,
    token: String,
    index: Int,
    count: Int,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyLabel = stringResource(R.string.copy)
    SegmentedListItem(
        onClick = { onCopy(token) },
        onLongClick = { onCopy(token) },
        onLongClickLabel = copyLabel,
        enabled = token.isNotBlank(),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        overlineContent = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            if (token.isNotBlank()) {
                Icon(
                    painter = painterResource(R.drawable.copy),
                    contentDescription = copyLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        SelectionContainer {
            Text(
                text = token.ifBlank { "—" },
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegenerateTokenSheet(
    sourceUrl: String,
    isGenerating: Boolean,
    onSourceUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = MaxSheetWidth)
                    .align(Alignment.CenterHorizontally)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.source_url),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = onSourceUrlChange,
                label = { Text(stringResource(R.string.source_url)) },
                placeholder = { Text(stringResource(R.string.source_url_placeholder)) },
                singleLine = true,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onRegenerate,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.regenerate_token))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
