/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.MainMenuDestination
import moe.rukamori.archivetune.viewmodels.MenuProfileState

@Composable
fun TopAppBarOverflowMenu(
    expanded: Boolean,
    profileState: MenuProfileState,
    accountImageUrl: String?,
    hazeState: HazeState,
    pureBlack: Boolean,
    hasUnreadNews: Boolean,
    hasUpdateAvailable: Boolean,
    onDismissRequest: () -> Unit,
    onSelect: (MainMenuDestination) -> Unit,
) {
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface
    BlurredOverflowMenu(
        expanded = expanded,
        hazeState = hazeState,
        pureBlack = pureBlack,
        onDismissRequest = onDismissRequest,
    ) {
        MenuProfileHeader(
            state = profileState,
            accountImageUrl = accountImageUrl,
            contentColor = contentColor,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.Profile) } },
        )
        OverflowMenuDivider(modifier = Modifier.padding(vertical = 8.dp))
        OverflowAction(
            label = stringResource(R.string.new_releases),
            iconRes = R.drawable.new_release,
            contentColor = contentColor,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.NewRelease) } },
        )
        OverflowMenuDivider()
        OverflowAction(
            label = stringResource(R.string.news),
            iconRes = R.drawable.newspaper,
            contentColor = contentColor,
            hasBadge = hasUnreadNews,
            badgeDescription = if (hasUnreadNews) stringResource(R.string.news_tooltip_title) else null,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.News) } },
        )
        OverflowMenuDivider()
        OverflowAction(
            label = stringResource(R.string.history),
            iconRes = R.drawable.history,
            contentColor = contentColor,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.History) } },
        )
        OverflowMenuDivider()
        OverflowAction(
            label = stringResource(R.string.settings),
            iconRes = R.drawable.settings,
            contentColor = contentColor,
            hasBadge = hasUpdateAvailable,
            badgeDescription = if (hasUpdateAvailable) stringResource(R.string.new_update_available) else null,
            onClick = remember(onSelect) { { onSelect(MainMenuDestination.Settings) } },
        )
    }
}

@Composable
private fun MenuProfileHeader(
    state: MenuProfileState,
    accountImageUrl: String?,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val profile = (state as? MenuProfileState.Success)?.profile
    val subtitle = when (state) {
        MenuProfileState.Loading -> stringResource(R.string.loading)
        MenuProfileState.Empty -> stringResource(R.string.login)
        is MenuProfileState.Error -> stringResource(state.messageResId)
        is MenuProfileState.Success -> state.profile.name.ifBlank { stringResource(R.string.account) }
    }
    val context = LocalContext.current
    val avatarSize = with(LocalDensity.current) { 48.dp.roundToPx() }
    val avatarRequest = remember(context, accountImageUrl, avatarSize, profile != null) {
        if (profile != null && !accountImageUrl.isNullOrBlank()) {
            ImageRequest.Builder(context).data(accountImageUrl).size(avatarSize).build()
        } else {
            null
        }
    }
    DropdownMenuItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        text = {
            Column {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile != null && profile.email.isNotBlank()) {
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingIcon = {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.account), null, modifier = Modifier.size(28.dp))
                if (avatarRequest != null) {
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                    )
                }
            }
        },
        trailingIcon = {
            Icon(painterResource(R.drawable.navigate_next), null, modifier = Modifier.size(20.dp))
        },
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor,
        ),
        onClick = onClick,
    )
}

@Composable
private fun OverflowAction(
    label: String,
    @DrawableRes iconRes: Int,
    contentColor: Color,
    onClick: () -> Unit,
    hasBadge: Boolean = false,
    badgeDescription: String? = null,
) {
    DropdownMenuItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            if (badgeDescription != null) stateDescription = badgeDescription
        },
        text = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            BadgedBox(badge = { if (hasBadge) Badge() }) {
                Icon(painterResource(iconRes), null, modifier = Modifier.size(24.dp))
            }
        },
        colors = MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor),
        onClick = onClick,
    )
}
