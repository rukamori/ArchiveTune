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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.PoTokenGvsKey
import moe.rukamori.archivetune.constants.PoTokenPlayerKey
import moe.rukamori.archivetune.constants.PoTokenSourceUrlKey
import moe.rukamori.archivetune.constants.UseVisitorDataKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.WebClientPoTokenEnabledKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupTitle
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.PoTokenState
import moe.rukamori.archivetune.viewmodels.PoTokenViewModel

private const val DEFAULT_EXTRACT_URL = "https://youtube.com/account"

private val SUPPORTED_CLIENTS =
    listOf(
        "web",
        "mweb",
        "web_safari",
        "web_embedded",
        "web_creator",
        "web_music",
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoTokenScreen(
    navController: NavController,
    viewModel: PoTokenViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tokenState by viewModel.state.collectAsState()
    var showRegenerateSheet by remember { mutableStateOf(false) }
    val regenerateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var (webClientPoTokenEnabled, onWebClientPoTokenEnabledChange) =
        rememberPreference(
            WebClientPoTokenEnabledKey,
            defaultValue = false,
        )
    var (useVisitorData, onUseVisitorDataChange) =
        rememberPreference(
            UseVisitorDataKey,
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
    val (innerTubeCookie, _) =
        rememberPreference(
            InnerTubeCookieKey,
            defaultValue = "",
        )

    val extractionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val gvsToken = data?.getStringExtra(PoTokenExtractionActivity.EXTRA_GVS_TOKEN).orEmpty()
                val playerToken = data?.getStringExtra(PoTokenExtractionActivity.EXTRA_PLAYER_TOKEN).orEmpty()
                val visitorData = data?.getStringExtra(PoTokenExtractionActivity.EXTRA_VISITOR_DATA).orEmpty()

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
                val error = result.data?.getStringExtra(PoTokenExtractionActivity.EXTRA_ERROR).orEmpty()
                if (error.isNotBlank()) {
                    viewModel.onExtractionError(error)
                }
            }
        }

    val launchExtraction: () -> Unit = {
        viewModel.resetState()
        val launchUrl = sourceUrl.takeIf { it.isNotBlank() } ?: DEFAULT_EXTRACT_URL
        val intent =
            Intent(context, PoTokenExtractionActivity::class.java).apply {
                putExtra(PoTokenExtractionActivity.EXTRA_SOURCE_URL, launchUrl)
            }
        extractionLauncher.launch(intent)
    }

    val hasCookie = innerTubeCookie.isNotBlank()

    LaunchedEffect(tokenState) {
        when (val state = tokenState) {
            is PoTokenState.Success -> {
                onStoredGvsTokenChange(state.gvsToken)
                onStoredPlayerTokenChange(state.playerToken)
                onStoredVisitorDataChange(state.visitorData)
                Toast.makeText(context, R.string.tokens_generated, Toast.LENGTH_SHORT).show()
            }

            is PoTokenState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }

            else -> {}
        }
    }

    val displayGvsToken =
        when (val s = tokenState) {
            is PoTokenState.Success -> s.gvsToken
            else -> storedGvsToken
        }
    val displayPlayerToken =
        when (val s = tokenState) {
            is PoTokenState.Success -> s.playerToken
            else -> storedPlayerToken
        }
    val displayVisitorData =
        when (val s = tokenState) {
            is PoTokenState.Success -> s.visitorData
            else -> storedVisitorData
        }

    if (showRegenerateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRegenerateSheet = false },
            sheetState = regenerateSheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.source_url),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = onSourceUrlChange,
                    label = { Text(stringResource(R.string.source_url)) },
                    placeholder = { Text(stringResource(R.string.source_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    shape = MaterialTheme.shapes.medium,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        showRegenerateSheet = false
                        launchExtraction()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.regenerate_token))
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.po_token_generation)) },
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
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding)
                .animateContentSize(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
        ) {
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

            AnimatedVisibility(
                visible = webClientPoTokenEnabled,
                enter =
                    expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeIn(),
                exit =
                    shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeOut(),
            ) {
                Column {
                    PreferenceGroupTitle(
                        title = stringResource(R.string.generated_tokens),
                    )

                    SelectableTokenCard(
                        label = stringResource(R.string.po_token_gvs),
                        token = displayGvsToken,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(displayGvsToken))
                            Toast.makeText(context, R.string.token_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    SelectableTokenCard(
                        label = stringResource(R.string.po_token_player),
                        token = displayPlayerToken,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(displayPlayerToken))
                            Toast.makeText(context, R.string.token_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    SelectableTokenCard(
                        label = stringResource(R.string.visitor_data),
                        token = displayVisitorData,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(displayVisitorData))
                            Toast.makeText(context, R.string.token_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    PreferenceGroupTitle(
                        title = stringResource(R.string.supported_clients),
                    )

                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SUPPORTED_CLIENTS.forEach { client ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = client,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors =
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                            )
                        }
                    }

                    PreferenceGroup(title = stringResource(R.string.token_settings)) {
                        item {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.use_visitor_data)) },
                                description = stringResource(R.string.use_visitor_data_desc),
                                icon = { Icon(painterResource(R.drawable.person), null) },
                                checked = useVisitorData,
                                onCheckedChange = { enabled ->
                                    if (enabled && hasCookie) {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.cookies_must_be_disabled,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    } else {
                                        onUseVisitorDataChange(enabled)
                                    }
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    ExtendedFloatingActionButton(
                        onClick = {
                            showRegenerateSheet = true
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.medium,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.regenerate),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectableTokenCard(
    label: String,
    token: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = token.ifBlank { "—" },
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color =
                            if (token.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (token.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onCopy,
                    onLongClick = onCopy,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
