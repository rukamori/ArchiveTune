/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DiscordAvatarUrlKey
import moe.rukamori.archivetune.constants.DiscordNameKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.DiscordUsernameKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.LastFMSessionKey
import moe.rukamori.archivetune.constants.LastFMUsernameKey
import moe.rukamori.archivetune.constants.PinDiscordCardKey
import moe.rukamori.archivetune.constants.PinLastFmCardKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * "Accounts" section for the Integration screen.
 *
 * Shows a card per connected service. The YouTube Music card is ALWAYS shown first.
 * The Last.fm and Discord cards can be pinned by the user; pinned cards float to the
 * top (just under the always-on YouTube card) so the user's preferred integrations
 * stay within easy reach.
 */
@Composable
fun IntegrationAccountCards(navController: NavController) {
    // YouTube Music — always shown.
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (accountHandle) = rememberPreference(AccountChannelHandleKey, "")

    // Last.fm.
    val (lastFmSession) = rememberPreference(LastFMSessionKey, "")
    val (lastFmUsername) = rememberPreference(LastFMUsernameKey, "")
    val (pinLastFm, setPinLastFm) = rememberPreference(PinLastFmCardKey, false)

    // Discord.
    val (discordToken) = rememberPreference(DiscordTokenKey, "")
    val (discordName) = rememberPreference(DiscordNameKey, "")
    val (discordUsername) = rememberPreference(DiscordUsernameKey, "")
    val (discordAvatarUrl) = rememberPreference(DiscordAvatarUrlKey, "")
    val (pinDiscord, setPinDiscord) = rememberPreference(PinDiscordCardKey, false)

    val youtubeConnected = innerTubeCookie.isNotBlank()
    val lastFmConnected = lastFmSession.isNotBlank()
    val discordConnected = discordToken.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // YouTube Music is always first and cannot be pinned/unpinned.
        AccountCard(
            iconRes = R.drawable.music_note,
            title = stringResource(R.string.youtube_music_account),
            connected = youtubeConnected,
            detail =
                when {
                    !youtubeConnected -> null
                    accountName.isNotBlank() -> stringResource(R.string.account_signed_in_as, accountName)
                    accountHandle.isNotBlank() -> stringResource(R.string.account_signed_in_as, accountHandle)
                    else -> null
                },
            onClick = { navController.navigate("settings/account") },
        )

        // Pinnable cards sorted so pinned ones come first.
        val pinnables =
            listOf(
                PinnableCard(
                    order = if (pinLastFm) 0 else 1,
                    iconRes = R.drawable.token,
                    title = stringResource(R.string.lastfm_integration),
                    connected = lastFmConnected,
                    detail =
                        if (lastFmConnected && lastFmUsername.isNotBlank()) {
                            stringResource(R.string.account_signed_in_as, lastFmUsername)
                        } else {
                            null
                        },
                    pinned = pinLastFm,
                    onTogglePin = { setPinLastFm(!pinLastFm) },
                    onClick = { navController.navigate("settings/lastfm") },
                ),
                PinnableCard(
                    order = if (pinDiscord) 0 else 1,
                    iconRes = R.drawable.discord,
                    avatarUrl = discordAvatarUrl.takeIf { discordConnected && it.isNotBlank() },
                    title = stringResource(R.string.discord_integration),
                    connected = discordConnected,
                    detail =
                        when {
                            !discordConnected -> null
                            discordName.isNotBlank() -> stringResource(R.string.account_signed_in_as, discordName)
                            discordUsername.isNotBlank() -> stringResource(R.string.account_signed_in_as, discordUsername)
                            else -> null
                        },
                    pinned = pinDiscord,
                    onTogglePin = { setPinDiscord(!pinDiscord) },
                    onClick = { navController.navigate("settings/discord") },
                ),
            ).sortedBy { it.order }

        pinnables.forEach { card ->
            AccountCard(
                iconRes = card.iconRes,
                avatarUrl = card.avatarUrl,
                title = card.title,
                connected = card.connected,
                detail = card.detail,
                pinned = card.pinned,
                onTogglePin = card.onTogglePin,
                onClick = card.onClick,
            )
        }
    }
}

private data class PinnableCard(
    val order: Int,
    val iconRes: Int,
    val avatarUrl: String? = null,
    val title: String,
    val connected: Boolean,
    val detail: String?,
    val pinned: Boolean,
    val onTogglePin: () -> Unit,
    val onClick: () -> Unit,
)

@Composable
private fun AccountCard(
    iconRes: Int,
    title: String,
    connected: Boolean,
    detail: String?,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    pinned: Boolean? = null,
    onTogglePin: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading avatar (if available) or service icon.
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        detail
                            ?: stringResource(
                                if (connected) R.string.account_connected else R.string.account_not_connected,
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Pin toggle for pinnable cards.
            if (pinned != null && onTogglePin != null) {
                IconButton(
                    onClick = onTogglePin,
                    onLongClick = {},
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (pinned) R.drawable.bookmark_filled else R.drawable.bookmark,
                            ),
                        contentDescription =
                            stringResource(
                                if (pinned) R.string.account_card_unpin else R.string.account_card_pin,
                            ),
                        tint =
                            if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
