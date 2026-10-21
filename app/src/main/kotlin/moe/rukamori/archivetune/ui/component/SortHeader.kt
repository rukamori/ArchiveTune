/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.PlaylistSortType

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
inline fun <reified T : Enum<T>> SortHeader(
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    noinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
    showDescending: Boolean? = true,
    compact: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val allowDescending =
        when (sortType) {
            is PlaylistSongSortType -> sortType != PlaylistSongSortType.CUSTOM
            is PlaylistSortType -> sortType != PlaylistSortType.CUSTOM
            else -> true
        }
    val showSortDirection = showDescending == true && allowDescending
    val sortDirectionRotation by animateFloatAsState(
        targetValue = if (sortDescending) 0f else 180f,
        label = "SortHeaderDirection",
    )

    Box(modifier = modifier.padding(vertical = 8.dp)) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { menuExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text(
                        text = stringResource(sortTypeText(sortType)),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showSortDirection) {
                    IconButton(
                        onClick = { onSortDescendingChange(!sortDescending) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_downward),
                            contentDescription =
                                stringResource(
                                    if (sortDescending) R.string.sort_order_descending else R.string.sort_order_ascending,
                                ),
                            modifier = Modifier.size(18.dp).rotate(sortDirectionRotation),
                        )
                    }
                }
            }
        } else if (showSortDirection) {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.TonalLeadingButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.heightIn(min = SplitButtonDefaults.MediumContainerHeight),
                    ) {
                        Text(
                            text = stringResource(sortTypeText(sortType)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TonalTrailingButton(
                        checked = sortDescending,
                        onCheckedChange = onSortDescendingChange,
                        modifier =
                            Modifier
                                .heightIn(min = SplitButtonDefaults.MediumContainerHeight)
                                .widthIn(min = SplitButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_downward),
                            contentDescription =
                                stringResource(
                                    if (sortDescending) {
                                        R.string.sort_order_descending
                                    } else {
                                        R.string.sort_order_ascending
                                    },
                                ),
                            modifier =
                                Modifier
                                    .size(SplitButtonDefaults.TrailingIconSize)
                                    .rotate(sortDirectionRotation),
                        )
                    }
                },
            )
        } else {
            FilledTonalButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.heightIn(min = SplitButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    text = stringResource(sortTypeText(sortType)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 172.dp),
        ) {
            enumValues<T>().forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sortTypeText(type)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            painter =
                                painterResource(
                                    if (sortType == type) {
                                        R.drawable.radio_button_checked
                                    } else {
                                        R.drawable.radio_button_unchecked
                                    },
                                ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onSortTypeChange(type)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}
