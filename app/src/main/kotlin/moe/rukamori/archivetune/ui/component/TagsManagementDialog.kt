/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.PlaylistTagColorPickerState
import moe.rukamori.archivetune.viewmodels.PlaylistTagEditorState
import moe.rukamori.archivetune.viewmodels.PlaylistTagUiModel
import moe.rukamori.archivetune.viewmodels.PlaylistTagsScreenState
import moe.rukamori.archivetune.viewmodels.PlaylistTagsViewModel

@Composable
fun TagsManagementDialog(
    onDismiss: () -> Unit,
    viewModel: PlaylistTagsViewModel = hiltViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val colorPickerState by viewModel.colorPickerState.collectAsStateWithLifecycle()

    (editorState as? PlaylistTagEditorState.Visible)?.let { editor ->
        AddEditTagDialog(
            editor = editor,
            onNameChange = viewModel::updateEditorName,
            onColorClick = viewModel::openEditorColorPicker,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
        )
    }

    (colorPickerState as? PlaylistTagColorPickerState.Visible)?.let { colorPicker ->
        PlaylistTagColorPickerDialog(
            colorPicker = colorPicker,
            onDismiss = viewModel::dismissColorPicker,
            onColorSelected = viewModel::selectColor,
        )
    }

    PlaylistTagsDialogScaffold(onDismiss = onDismiss) {
        TagsManagementContent(
            state = screenState,
            onAddTag = viewModel::openCreateEditor,
            onEditTag = viewModel::openEditEditor,
            onChangeColor = viewModel::openTagColorPicker,
            onDeleteTag = viewModel::deleteTag,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TagsManagementContent(
    state: PlaylistTagsScreenState,
    onAddTag: () -> Unit,
    onEditTag: (String) -> Unit,
    onChangeColor: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PlaylistTagsDialogLayout(
        icon = R.drawable.style,
        title = stringResource(R.string.manage_tags),
        subtitle = stringResource(R.string.manage_playlist_tags_desc),
        body = { bodyModifier ->
            when (state) {
                PlaylistTagsScreenState.Loading -> {
                    PlaylistTagsLoadingContent(modifier = bodyModifier)
                }

                PlaylistTagsScreenState.Empty -> {
                    PlaylistTagsEmptyContent(modifier = bodyModifier)
                }

                is PlaylistTagsScreenState.Error -> {
                    PlaylistTagsErrorContent(
                        messageResId = state.messageResId,
                        modifier = bodyModifier,
                    )
                }

                is PlaylistTagsScreenState.Success -> {
                    TagsManagementList(
                        tags = state.tags,
                        onEditTag = onEditTag,
                        onChangeColor = onChangeColor,
                        onDeleteTag = onDeleteTag,
                        modifier = bodyModifier,
                    )
                }
            }
        },
        actions = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.close))
            }

            Button(
                onClick = onAddTag,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.add_tag))
            }
        },
    )
}

@Composable
private fun PlaylistTagsDialogLayout(
    icon: Int,
    title: String,
    subtitle: String? = null,
    body: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PlaylistTagsHeader(
            icon = icon,
            title = title,
            subtitle = subtitle,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp),
        )

        Spacer(modifier = Modifier.heightIn(min = 20.dp))

        body(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        PlaylistTagsActionRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 24.dp),
            actions = actions,
        )
    }
}

@Composable
private fun PlaylistTagsActionRow(
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { actions() },
    )
}

@Composable
private fun TagsManagementList(
    tags: List<PlaylistTagUiModel>,
    onEditTag: (String) -> Unit,
    onChangeColor: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = tags,
            key = { _, tag -> tag.id },
            contentType = { _, _ -> "tag_management_row" },
        ) { index, tag ->
            val editClick = remember(tag.id, onEditTag) { { onEditTag(tag.id) } }
            val colorClick = remember(tag.id, onChangeColor) { { onChangeColor(tag.id) } }
            val deleteClick = remember(tag.id, onDeleteTag) { { onDeleteTag(tag.id) } }

            EditableTagRow(
                tag = tag,
                index = index,
                count = tags.size,
                onEdit = editClick,
                onChangeColor = colorClick,
                onDelete = deleteClick,
            )
        }
    }
}

@Composable
private fun EditableTagRow(
    tag: PlaylistTagUiModel,
    index: Int,
    count: Int,
    onEdit: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit,
) {
    SegmentedListItem(
        selected = false,
        onClick = onEdit,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = {
            PlaylistTagColorSwatch(
                color = tag.color,
                selected = false,
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onChangeColor,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.palette),
                        contentDescription = stringResource(R.string.choose_color),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        },
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddEditTagDialog(
    editor: PlaylistTagEditorState.Visible,
    onNameChange: (String) -> Unit,
    onColorClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val title =
        if (editor.tagId == null) {
            stringResource(R.string.add_tag)
        } else {
            stringResource(R.string.edit_tag)
        }

    PlaylistTagsDialogScaffold(onDismiss = onDismiss) {
        PlaylistTagsDialogLayout(
            icon = if (editor.tagId == null) R.drawable.add else R.drawable.edit,
            title = title,
            body = { bodyModifier ->
                Column(
                    modifier = bodyModifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = editor.name,
                        onValueChange = onNameChange,
                        label = { Text(text = stringResource(R.string.tag_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (!editor.canSave) return@KeyboardActions
                                    keyboardController?.hide()
                                    onSave()
                                },
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onColorClick),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PlaylistTagColorSwatch(
                                color = editor.color,
                                selected = true,
                                onClick = onColorClick,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.tag_color),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.tap_to_change_color),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.palette),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            actions = {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                Button(
                    enabled = editor.canSave,
                    onClick = {
                        keyboardController?.hide()
                        onSave()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.save))
                }
            },
        )
    }
}

@Composable
private fun PlaylistTagColorPickerDialog(
    colorPicker: PlaylistTagColorPickerState.Visible,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    PlaylistTagsDialogScaffold(onDismiss = onDismiss) {
        PlaylistTagsDialogLayout(
            icon = R.drawable.palette,
            title = stringResource(R.string.choose_color),
            body = { bodyModifier ->
                FlowRow(
                    modifier =
                        bodyModifier
                            .verticalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    colorPicker.colors.forEach { color ->
                        val colorClick = remember(color, onColorSelected) { { onColorSelected(color) } }

                        PlaylistTagColorSwatch(
                            color = color,
                            selected = color == colorPicker.selectedColor,
                            onClick = colorClick,
                        )
                    }
                }
            },
            actions = {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistTagsHeader(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaylistTagsLoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistTagsEmptyContent(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(R.drawable.style),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(R.string.no_tags_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaylistTagsErrorContent(
    messageResId: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(messageResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaylistTagColorSwatch(
    color: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    val swatchColor = rememberTagColor(color)
    val size by animateDpAsState(
        targetValue = if (selected) 56.dp else 48.dp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "tagColorSwatchSize",
    )
    val borderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "tagColorSwatchBorder",
    )

    Surface(
        modifier =
            Modifier
                .size(size)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        color = swatchColor,
        border = BorderStroke(2.dp, borderColor),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTagsDialogScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                shape = AlertDialogDefaults.shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                content = content,
            )
        }
    }
}

@Composable
private fun rememberTagColor(color: String): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(color, fallback) {
        runCatching { Color(color.toColorInt()) }.getOrDefault(fallback)
    }
}
