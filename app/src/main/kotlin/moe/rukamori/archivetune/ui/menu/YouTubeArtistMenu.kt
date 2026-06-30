/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val libraryArtist by database.artist(artist.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val artistPin = remember(artist.id) { SpeedDialPin(type = SpeedDialPinType.ARTIST, id = artist.id) }
    val isInSpeedDial =
        remember(speedDialPins, artistPin) {
            speedDialPins.any { it.type == artistPin.type && it.id == artistPin.id }
        }

    YouTubeListItem(
        item = artist,
        trailingContent = {},
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val dividerModifier = Modifier.padding(start = 56.dp)
    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions =
                        buildList {
                            artist.radioEndpoint?.let { watchEndpoint ->
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.start_radio),
                                        onClick = {
                                            playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                            onDismiss()
                                        },
                                    ),
                                )
                            }

                            artist.shuffleEndpoint?.let { watchEndpoint ->
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.shuffle),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.shuffle),
                                        onClick = {
                                            playerConnection.playQueue(YouTubeQueue(watchEndpoint))
                                            onDismiss()
                                        },
                                    ),
                                )
                            }

                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.share),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    text = stringResource(R.string.share),
                                    onClick = {
                                        val intent =
                                            Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, artist.shareLink)
                                            }
                                        context.startActivity(Intent.createChooser(intent, null))
                                        onDismiss()
                                    },
                                ),
                            )
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    if (libraryArtist?.artist?.bookmarkedAt !=
                                        null
                                    ) {
                                        stringResource(R.string.subscribed)
                                    } else {
                                        stringResource(R.string.subscribe)
                                    },
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter =
                                    painterResource(
                                        if (libraryArtist?.artist?.bookmarkedAt != null) {
                                            R.drawable.subscribed
                                        } else {
                                            R.drawable.subscribe
                                        },
                                    ),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                database.query {
                                    val libraryArtist = libraryArtist
                                    if (libraryArtist != null) {
                                        update(libraryArtist.artist.toggleLike())
                                    } else {
                                        insert(
                                            ArtistEntity(
                                                id = artist.id,
                                                name = artist.title,
                                                channelId = artist.channelId,
                                                thumbnailUrl = artist.thumbnail,
                                            ).toggleLike(),
                                        )
                                    }
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    HorizontalDivider(
                        modifier = dividerModifier,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text =
                                    stringResource(
                                        if (isInSpeedDial) {
                                            R.string.remove_from_speed_dial
                                        } else {
                                            R.string.pin_to_speed_dial
                                        },
                                    ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                coroutineScope.launch {
                                    if (!isInSpeedDial) {
                                        withContext(Dispatchers.IO) {
                                            database.transaction {
                                                insert(
                                                    ArtistEntity(
                                                        id = artist.id,
                                                        name = artist.title,
                                                        channelId = artist.channelId,
                                                        thumbnailUrl = artist.thumbnail,
                                                    ),
                                                )
                                            }
                                        }
                                    }

                                    val updatedPins = toggleSpeedDialPin(speedDialPins, artistPin)
                                    onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                                    onDismiss()
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}
