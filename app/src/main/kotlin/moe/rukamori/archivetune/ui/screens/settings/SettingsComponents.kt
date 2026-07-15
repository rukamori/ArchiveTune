/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R

@Composable
fun SettingsProfileHeader(
    state: SettingsProfileState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when {
            state.isLoading -> stringResource(R.string.loading)
            state.isLoggedIn -> state.accountName.ifBlank { stringResource(R.string.account) }
            else -> stringResource(R.string.login)
        }
    val subtitle =
        when {
            state.isLoggedIn && state.accountEmail.isNotBlank() -> state.accountEmail
            state.isLoggedIn -> state.accountName.ifBlank { null }
            else -> null
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.ProfileCardAvatarSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (state.isLoggedIn && !state.accountImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = state.accountImageUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter =
                            painterResource(
                                if (state.isLoggedIn) R.drawable.account else R.drawable.login,
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(SettingsDimensions.ProfileCardAvatarIconSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { s ->
                    Text(
                        text = s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }
    }
}

@Composable
fun SettingsPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.security),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onRequestPermission,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.allow),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun SettingsUpdateBanner(
    latestVersion: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "updateScale",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale)
                .focusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "v$latestVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                )
            }

            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    group: SettingsGroup,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = group.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.2f,
            modifier =
                Modifier.padding(
                    horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                    vertical = SettingsDimensions.SectionHeaderBottomPadding,
                ),
        )

        Card(
            shape = RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                group.items.forEachIndexed { index, item ->
                    SettingsRow(
                        item = item,
                        showDivider = index < group.items.size - 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    item: SettingsItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent =
        if (item.accentColor.isSpecified) {
            item.accentColor
        } else {
            MaterialTheme.colorScheme.primary
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "rowScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.06f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "rowBgAlpha",
    )

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.background(MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha))
                    .focusable()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = item.onClick,
                    ).padding(
                        horizontal = SettingsDimensions.RowHorizontalPadding,
                        vertical = SettingsDimensions.RowVerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.RowIconSize)
                        .clip(CircleShape)
                        .background(effectiveAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(8.dp),
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = effectiveAccent,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (item.showUpdateIndicator) {
                                effectiveAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                thickness = SettingsDimensions.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.2f,
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
    )
}

@Composable
fun SettingsSegmentedItem(
    item: SettingsItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent =
        if (item.accentColor.isSpecified) {
            item.accentColor
        } else {
            MaterialTheme.colorScheme.primary
        }
    val iconContentCandidate = contentColorFor(effectiveAccent)
    val iconContentColor =
        if (iconContentCandidate.isSpecified) {
            iconContentCandidate
        } else {
            MaterialTheme.colorScheme.surface
        }
    val shape = remember(index, count) { segmentedSettingsItemShape(index, count) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "settingsSegmentScale",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .focusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = item.onClick,
                ),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(effectiveAccent),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(9.dp),
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private fun segmentedSettingsItemShape(
    index: Int,
    count: Int,
): Shape {
    val large = 28.dp
    val small = 6.dp
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

@Composable
fun SettingsFlatItem(
    item: SettingsItem,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent =
        if (item.accentColor.isSpecified) {
            item.accentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = item.onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.showUpdateIndicator) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(8.dp),
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                ) {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                    )
                }
            } else {
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    maxLines = if (item.subtitle == null) 2 else 1,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (item.showUpdateIndicator) {
                                effectiveAccent
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
