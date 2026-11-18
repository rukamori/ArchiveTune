/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchSortKey

private data class SortOrderOption(
    val descending: Boolean,
    @param:StringRes val labelRes: Int,
)

@get:StringRes
private val OnlineSearchSortKey.labelRes: Int
    get() =
        when (this) {
            OnlineSearchSortKey.RELEVANCE -> R.string.search_sort_relevance
            OnlineSearchSortKey.VIEWS -> R.string.views
            OnlineSearchSortKey.TITLE -> R.string.sort_by_name
            OnlineSearchSortKey.ARTIST -> R.string.sort_by_artist
            OnlineSearchSortKey.DURATION -> R.string.duration_sort
            OnlineSearchSortKey.YEAR -> R.string.year_sort
        }

@get:DrawableRes
private val OnlineSearchSortKey.iconRes: Int
    get() =
        when (this) {
            OnlineSearchSortKey.RELEVANCE -> R.drawable.search
            OnlineSearchSortKey.VIEWS -> R.drawable.trending_up
            OnlineSearchSortKey.TITLE -> R.drawable.text_fields
            OnlineSearchSortKey.ARTIST -> R.drawable.person
            OnlineSearchSortKey.DURATION -> R.drawable.timer
            OnlineSearchSortKey.YEAR -> R.drawable.calendar_today
        }

private val OnlineSearchSortKey.supportingTextRes: Int?
    get() =
        when (this) {
            OnlineSearchSortKey.VIEWS -> R.string.search_sort_views_scope
            OnlineSearchSortKey.DURATION -> R.string.search_sort_duration_scope
            OnlineSearchSortKey.YEAR -> R.string.albums
            OnlineSearchSortKey.RELEVANCE,
            OnlineSearchSortKey.TITLE,
            OnlineSearchSortKey.ARTIST,
            -> null
        }

private val OnlineSearchSortKey.orderOptions: List<SortOrderOption>
    get() {
        val (ascendingLabel, descendingLabel) =
            when (this) {
                OnlineSearchSortKey.RELEVANCE -> return emptyList()
                OnlineSearchSortKey.VIEWS -> R.string.search_sort_least_viewed to R.string.search_sort_most_viewed
                OnlineSearchSortKey.TITLE,
                OnlineSearchSortKey.ARTIST,
                -> R.string.sort_a_to_z to R.string.sort_z_to_a
                OnlineSearchSortKey.DURATION -> R.string.search_sort_shortest to R.string.search_sort_longest
                OnlineSearchSortKey.YEAR -> R.string.search_sort_oldest to R.string.search_sort_newest
            }
        val ascending = SortOrderOption(descending = false, labelRes = ascendingLabel)
        val descending = SortOrderOption(descending = true, labelRes = descendingLabel)
        return if (defaultDescending) listOf(descending, ascending) else listOf(ascending, descending)
    }

@Composable
fun OnlineSearchSortMenu(
    sort: OnlineSearchSort,
    pureBlack: Boolean,
    onSortKeySelected: (OnlineSearchSortKey) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isCustomSort = sort.key != OnlineSearchSortKey.RELEVANCE
    val menuLabel = stringResource(R.string.search_sort_results)
    val activeSortDescription =
        stringResource(R.string.search_sort_active, stringResource(sort.key.labelRes))
    val groupContainerColor = if (pureBlack) Color.Black else MenuDefaults.groupStandardContainerColor
    val groupBorder =
        if (pureBlack) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val motionScheme = MaterialTheme.motionScheme

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { stateDescription = activeSortDescription },
        ) {
            BadgedBox(badge = { if (isCustomSort) Badge() }) {
                Icon(
                    painter = painterResource(R.drawable.sort),
                    contentDescription = menuLabel,
                )
            }
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .semantics { paneTitle = menuLabel }
                    .verticalScroll(rememberScrollState()),
        ) {
            val sortKeys = OnlineSearchSortKey.entries
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, if (isCustomSort) 2 else 1),
                containerColor = groupContainerColor,
                border = groupBorder,
            ) {
                MenuDefaults.DropdownMenuGroupLabel {
                    Text(text = stringResource(R.string.search_sort_by))
                }
                sortKeys.forEachIndexed { index, key ->
                    SelectableDropdownMenuItem(
                        selected = key == sort.key,
                        onClick = {
                            onSortKeySelected(key)
                            if (key == OnlineSearchSortKey.RELEVANCE) expanded = false
                        },
                        text = { Text(text = stringResource(key.labelRes)) },
                        shapes = MenuDefaults.itemShape(index, sortKeys.size),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(key.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                        selectedLeadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                        supportingText =
                            key.supportingTextRes?.let { supportingTextRes ->
                                { Text(text = stringResource(supportingTextRes)) }
                            },
                    )
                }
            }

            AnimatedVisibility(
                visible = isCustomSort,
                enter =
                    expandVertically(motionScheme.fastSpatialSpec()) +
                        fadeIn(motionScheme.fastEffectsSpec()),
                exit =
                    shrinkVertically(motionScheme.fastSpatialSpec()) +
                        fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                val orderOptions = sort.key.orderOptions
                Column {
                    Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(1, 2),
                        containerColor = groupContainerColor,
                        border = groupBorder,
                    ) {
                        MenuDefaults.DropdownMenuGroupLabel {
                            Text(text = stringResource(R.string.search_sort_order))
                        }
                        orderOptions.forEachIndexed { index, option ->
                            SelectableDropdownMenuItem(
                                selected = option.descending == sort.descending,
                                onClick = {
                                    onSortDescendingChange(option.descending)
                                    expanded = false
                                },
                                text = { Text(text = stringResource(option.labelRes)) },
                                shapes = MenuDefaults.itemShape(index, orderOptions.size),
                                leadingIcon = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                if (option.descending) {
                                                    R.drawable.arrow_downward
                                                } else {
                                                    R.drawable.arrow_upward
                                                },
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    )
                                },
                                selectedLeadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
