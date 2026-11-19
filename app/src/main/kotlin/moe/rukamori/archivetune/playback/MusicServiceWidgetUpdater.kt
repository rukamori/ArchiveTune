/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.widget.AlbumArtWidget
import moe.rukamori.archivetune.widget.ArtworkNowPlayingWidget
import moe.rukamori.archivetune.widget.ListeningInsightsWidget
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import moe.rukamori.archivetune.widget.LoadWidgetRecentSongsUseCase
import moe.rukamori.archivetune.widget.MiniPlayerWidget
import moe.rukamori.archivetune.widget.MusicWidget
import moe.rukamori.archivetune.widget.MusicWidgetKeys
import moe.rukamori.archivetune.widget.NoQueueIndex
import moe.rukamori.archivetune.widget.NowPlayingCardWidget
import moe.rukamori.archivetune.widget.PlaybackCapsuleWidget
import moe.rukamori.archivetune.widget.PlaybackCommandWidget
import moe.rukamori.archivetune.widget.PlaybackDeckWidget
import moe.rukamori.archivetune.widget.PlaybackSpotlightWidget
import moe.rukamori.archivetune.widget.RecentlyPlayedWidget
import moe.rukamori.archivetune.widget.UpNextWidget
import moe.rukamori.archivetune.widget.WidgetInsightsSnapshot
import moe.rukamori.archivetune.widget.WidgetMediaItem
import moe.rukamori.archivetune.widget.encodeWidgetMediaItems
import moe.rukamori.archivetune.widget.toWidgetPreferenceValue
import java.io.File

internal class MusicServiceWidgetUpdater(
    private val service: MusicService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadWidgetInsights: LoadWidgetInsightsUseCase,
    private val loadWidgetRecentSongs: LoadWidgetRecentSongsUseCase,
) {
    private val widgetManager = GlanceAppWidgetManager(service)
    private var stateJob: Job? = null
    private var progressJob: Job? = null

    fun update() {
        stateJob?.cancel()
        stateJob =
            scope.launch(SilentHandler) {
                pushState()
            }
    }

    fun updateProgressTracking() {
        progressJob?.cancel()
        if (player.isPlaying && player.duration > 0) {
            progressJob =
                scope.launch(SilentHandler) {
                    val installedTargets = findInstalledTargets(progressWidgets)
                    if (installedTargets.isEmpty()) return@launch

                    while (isActive && player.isPlaying) {
                        delay(WIDGET_PROGRESS_UPDATE_INTERVAL_MILLIS)
                        if (player.isPlaying) {
                            updateProgress(installedTargets, player.playbackProgress())
                        }
                    }
                }
        }
    }

    private suspend fun pushState() {
        val installedTargets = findInstalledTargets(playbackWidgets)
        if (installedTargets.isEmpty()) return

        val mediaItem = player.currentMediaItem
        val meta = mediaItem?.mediaMetadata
        val title = meta?.title?.toString() ?: service.getString(R.string.no_track_playing)
        val artist = meta?.artist?.toString().orEmpty()
        val isPlaying = player.isPlaying
        val playbackPosition = player.playbackProgress()
        val queueEntries =
            if (installedTargets.any { it.target.requiresQueue }) collectUpNextEntries() else null

        val artFile = meta?.artworkUri?.let { cacheAlbumArt(it) }
        val dominantColor = artFile?.let { extractDominantColor(it) }
        val upNext = queueEntries?.map { entry -> entry.toWidgetMediaItem() }
        val recents =
            if (installedTargets.any { it.target.requiresRecents }) loadRecentItems() else null
        val snapshot =
            WidgetSnapshot(
                mediaId = mediaItem?.mediaId,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                isAvailable = mediaItem != null,
                playbackPosition = playbackPosition,
                artPath = artFile?.absolutePath,
                dominantColor = dominantColor,
                insights = WidgetInsightsSnapshot.Empty,
                upNext = upNext,
                recents = recents,
            )

        installedTargets.forEach { target ->
            updateWidget(target, snapshot)
        }
        pruneArtCache(snapshot.referencedArtPaths())
    }

    private fun collectUpNextEntries(): List<QueueEntry> {
        val timeline = player.currentTimeline
        val itemCount = player.mediaItemCount
        if (timeline.isEmpty || itemCount == 0) return emptyList()

        val shuffle = player.shuffleModeEnabled
        val entries = mutableListOf<QueueEntry>()
        var index = player.currentMediaItemIndex
        while (entries.size < UP_NEXT_LIMIT) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            if (index == C.INDEX_UNSET || index >= itemCount) break
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            entries +=
                QueueEntry(
                    index = index,
                    mediaId = item.mediaId,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    artworkUri = metadata.artworkUri,
                )
        }
        return entries
    }

    private suspend fun QueueEntry.toWidgetMediaItem(): WidgetMediaItem =
        WidgetMediaItem(
            id = mediaId,
            title = title.ifBlank { service.getString(R.string.no_track_playing) },
            subtitle = artist,
            artPath = artworkUri?.let { cacheAlbumArt(it) }?.absolutePath,
            queueIndex = index,
        )

    private suspend fun loadRecentItems(): List<WidgetMediaItem> {
        val songs =
            try {
                loadWidgetRecentSongs()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportException(error)
                return emptyList()
            }
        return songs.map { song ->
            WidgetMediaItem(
                id = song.id,
                title = song.title,
                subtitle = song.artist,
                artPath = song.thumbnailUrl?.let { url -> cacheAlbumArt(Uri.parse(url)) }?.absolutePath,
                queueIndex = NoQueueIndex,
            )
        }
    }

    private suspend fun updateProgress(
        installedTargets: List<InstalledWidgetTarget>,
        progress: Float,
    ) {
        installedTargets.forEach { installedTarget ->
            installedTarget.ids.forEach { id ->
                updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutableWidgetPreferences().apply {
                        this[MusicWidgetKeys.PLAYBACK_POSITION] = progress
                    }
                }
                installedTarget.target.widget.update(service, id)
            }
        }
    }

    private suspend fun updateWidget(
        installedTarget: InstalledWidgetTarget,
        snapshot: WidgetSnapshot,
    ) {
        val targetSnapshot =
            if (installedTarget.target.requiresInsights) {
                snapshot.copy(insights = loadInsightsSnapshot())
            } else {
                snapshot
            }

        installedTarget.ids.forEach { id ->
            updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutableWidgetPreferences().apply {
                    writeSnapshot(targetSnapshot)
                }
            }
            installedTarget.target.widget.update(service, id)
        }
    }

    private suspend fun findInstalledTargets(targets: List<WidgetTarget>): List<InstalledWidgetTarget> =
        targets.mapNotNull { target ->
            widgetManager
                .getGlanceIds(target.widgetClass)
                .takeIf { ids -> ids.isNotEmpty() }
                ?.let { ids -> InstalledWidgetTarget(target, ids) }
        }

    private fun Preferences.toMutableWidgetPreferences(): MutablePreferences =
        mutablePreferencesOf().also { mutable ->
            this[MusicWidgetKeys.TRACK_TITLE]?.let { mutable[MusicWidgetKeys.TRACK_TITLE] = it }
            this[MusicWidgetKeys.TRACK_ARTIST]?.let { mutable[MusicWidgetKeys.TRACK_ARTIST] = it }
            this[MusicWidgetKeys.ART_PATH]?.let { mutable[MusicWidgetKeys.ART_PATH] = it }
            this[MusicWidgetKeys.IS_PLAYING]?.let { mutable[MusicWidgetKeys.IS_PLAYING] = it }
            this[MusicWidgetKeys.IS_AVAILABLE]?.let { mutable[MusicWidgetKeys.IS_AVAILABLE] = it }
            this[MusicWidgetKeys.DOMINANT_COLOR]?.let { mutable[MusicWidgetKeys.DOMINANT_COLOR] = it }
            this[MusicWidgetKeys.PLAYBACK_POSITION]?.let { mutable[MusicWidgetKeys.PLAYBACK_POSITION] = it }
            this[MusicWidgetKeys.LISTENING_TIME]?.let { mutable[MusicWidgetKeys.LISTENING_TIME] = it }
            this[MusicWidgetKeys.TOTAL_PLAYS]?.let { mutable[MusicWidgetKeys.TOTAL_PLAYS] = it }
            this[MusicWidgetKeys.RECENT_SONGS]?.let { mutable[MusicWidgetKeys.RECENT_SONGS] = it }
            this[MusicWidgetKeys.GENRES]?.let { mutable[MusicWidgetKeys.GENRES] = it }
            this[MusicWidgetKeys.RECOMMENDATIONS]?.let { mutable[MusicWidgetKeys.RECOMMENDATIONS] = it }
            this[MusicWidgetKeys.TOP_SONG_SUMMARY]?.let { mutable[MusicWidgetKeys.TOP_SONG_SUMMARY] = it }
            this[MusicWidgetKeys.CURRENT_MEDIA_ID]?.let { mutable[MusicWidgetKeys.CURRENT_MEDIA_ID] = it }
            this[MusicWidgetKeys.UP_NEXT]?.let { mutable[MusicWidgetKeys.UP_NEXT] = it }
            this[MusicWidgetKeys.RECENT_ITEMS]?.let { mutable[MusicWidgetKeys.RECENT_ITEMS] = it }
        }

    private fun MutablePreferences.writeSnapshot(snapshot: WidgetSnapshot) {
        this[MusicWidgetKeys.TRACK_TITLE] = snapshot.title
        this[MusicWidgetKeys.TRACK_ARTIST] = snapshot.artist
        this[MusicWidgetKeys.IS_PLAYING] = snapshot.isPlaying
        this[MusicWidgetKeys.IS_AVAILABLE] = snapshot.isAvailable
        this[MusicWidgetKeys.PLAYBACK_POSITION] = snapshot.playbackPosition

        val mediaId = snapshot.mediaId
        if (!mediaId.isNullOrBlank()) {
            this[MusicWidgetKeys.CURRENT_MEDIA_ID] = mediaId
        } else {
            remove(MusicWidgetKeys.CURRENT_MEDIA_ID)
        }

        val artPath = snapshot.artPath
        if (artPath != null) {
            this[MusicWidgetKeys.ART_PATH] = artPath
        } else {
            remove(MusicWidgetKeys.ART_PATH)
        }

        val dominantColor = snapshot.dominantColor
        if (dominantColor != null) {
            this[MusicWidgetKeys.DOMINANT_COLOR] = dominantColor
        } else {
            remove(MusicWidgetKeys.DOMINANT_COLOR)
        }

        snapshot.upNext?.let { items -> writeMediaItems(MusicWidgetKeys.UP_NEXT, items) }
        snapshot.recents?.let { items -> writeMediaItems(MusicWidgetKeys.RECENT_ITEMS, items) }
        writeInsights(snapshot.insights)
    }

    private fun MutablePreferences.writeInsights(insights: WidgetInsightsSnapshot) {
        if (insights.listeningTime.isNotBlank()) {
            this[MusicWidgetKeys.LISTENING_TIME] = insights.listeningTime
        } else {
            remove(MusicWidgetKeys.LISTENING_TIME)
        }
        if (insights.totalPlays.isNotBlank()) {
            this[MusicWidgetKeys.TOTAL_PLAYS] = insights.totalPlays
        } else {
            remove(MusicWidgetKeys.TOTAL_PLAYS)
        }
        writeList(MusicWidgetKeys.RECENT_SONGS, insights.recentSongs)
        writeList(MusicWidgetKeys.GENRES, insights.genres)
        writeList(MusicWidgetKeys.RECOMMENDATIONS, insights.recommendations)

        val topSongSummary = insights.topSongSummary
        if (!topSongSummary.isNullOrBlank()) {
            this[MusicWidgetKeys.TOP_SONG_SUMMARY] = topSongSummary
        } else {
            remove(MusicWidgetKeys.TOP_SONG_SUMMARY)
        }
    }

    private fun MutablePreferences.writeList(
        key: Preferences.Key<String>,
        values: List<String>,
    ) {
        if (values.isEmpty()) {
            remove(key)
        } else {
            this[key] = values.toWidgetPreferenceValue()
        }
    }

    private fun MutablePreferences.writeMediaItems(
        key: Preferences.Key<String>,
        items: List<WidgetMediaItem>,
    ) {
        if (items.isEmpty()) {
            remove(key)
        } else {
            this[key] = items.encodeWidgetMediaItems()
        }
    }

    private suspend fun loadInsightsSnapshot(): WidgetInsightsSnapshot =
        try {
            loadWidgetInsights()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportException(error)
            WidgetInsightsSnapshot.Empty
        }

    private suspend fun cacheAlbumArt(uri: Uri): File? =
        withContext(Dispatchers.IO) {
            val dest = File(service.cacheDir, "$ART_FILE_PREFIX${Integer.toHexString(uri.toString().hashCode())}.jpg")
            if (dest.isFile && dest.length() > 0L) return@withContext dest

            if (uri.scheme == "content" || uri.scheme == "file") {
                return@withContext try {
                    service.contentResolver.openInputStream(uri)?.use { src ->
                        dest.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    if (dest.exists() && dest.length() > 0) dest else null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }

            if (uri.scheme == "https" || uri.scheme == "http") {
                return@withContext try {
                    val loader = service.applicationContext.imageLoader
                    val request =
                        ImageRequest
                            .Builder(service.applicationContext)
                            .data(uri.toString())
                            .size(512, 512)
                            .allowHardware(false)
                            .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        dest.outputStream().use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                        }
                        if (dest.exists() && dest.length() > 0) dest else null
                    } else {
                        null
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }

            null
        }

    private suspend fun pruneArtCache(referencedPaths: Set<String>) {
        withContext(Dispatchers.IO) {
            val files =
                service.cacheDir.listFiles { file -> file.isFile && file.name.startsWith(ART_FILE_PREFIX) }
                    ?: return@withContext
            if (files.size <= MAX_CACHED_ART_FILES) return@withContext
            files
                .filterNot { file -> file.absolutePath in referencedPaths }
                .sortedBy { file -> file.lastModified() }
                .take(files.size - MAX_CACHED_ART_FILES)
                .forEach { file -> file.delete() }
        }
    }

    private suspend fun extractDominantColor(file: File): Int? =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val palette = Palette.from(bitmap).generate()
                palette.getDarkVibrantColor(
                    palette.getDominantColor(android.graphics.Color.DKGRAY),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

    private fun Player.playbackProgress(): Float =
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    private fun WidgetSnapshot.referencedArtPaths(): Set<String> =
        buildSet {
            artPath?.let(::add)
            upNext?.forEach { item -> item.artPath?.let(::add) }
            recents?.forEach { item -> item.artPath?.let(::add) }
        }

    private data class QueueEntry(
        val index: Int,
        val mediaId: String,
        val title: String,
        val artist: String,
        val artworkUri: Uri?,
    )

    private data class WidgetSnapshot(
        val mediaId: String?,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val isAvailable: Boolean,
        val playbackPosition: Float,
        val artPath: String?,
        val dominantColor: Int?,
        val insights: WidgetInsightsSnapshot,
        val upNext: List<WidgetMediaItem>?,
        val recents: List<WidgetMediaItem>?,
    )

    private data class WidgetTarget(
        val widgetClass: Class<out GlanceAppWidget>,
        val widget: GlanceAppWidget,
        val requiresInsights: Boolean = false,
        val requiresQueue: Boolean = false,
        val requiresRecents: Boolean = false,
    )

    private data class InstalledWidgetTarget(
        val target: WidgetTarget,
        val ids: List<GlanceId>,
    )

    private companion object {
        const val WIDGET_PROGRESS_UPDATE_INTERVAL_MILLIS = 30_000L
        const val UP_NEXT_LIMIT = 8
        const val MAX_CACHED_ART_FILES = 64
        const val ART_FILE_PREFIX = "widget_art_"

        val playbackWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget()),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget()),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget()),
                WidgetTarget(AlbumArtWidget::class.java, AlbumArtWidget()),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget()),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget()),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget()),
                WidgetTarget(
                    widgetClass = ListeningInsightsWidget::class.java,
                    widget = ListeningInsightsWidget(),
                    requiresInsights = true,
                    requiresRecents = true,
                ),
                WidgetTarget(
                    widgetClass = ArtworkNowPlayingWidget::class.java,
                    widget = ArtworkNowPlayingWidget(),
                    requiresRecents = true,
                ),
                WidgetTarget(MiniPlayerWidget::class.java, MiniPlayerWidget()),
                WidgetTarget(
                    widgetClass = UpNextWidget::class.java,
                    widget = UpNextWidget(),
                    requiresQueue = true,
                ),
                WidgetTarget(
                    widgetClass = RecentlyPlayedWidget::class.java,
                    widget = RecentlyPlayedWidget(),
                    requiresRecents = true,
                ),
            )

        val progressWidgets =
            listOf(
                WidgetTarget(MusicWidget::class.java, MusicWidget()),
                WidgetTarget(NowPlayingCardWidget::class.java, NowPlayingCardWidget()),
                WidgetTarget(PlaybackDeckWidget::class.java, PlaybackDeckWidget()),
                WidgetTarget(PlaybackCapsuleWidget::class.java, PlaybackCapsuleWidget()),
                WidgetTarget(PlaybackSpotlightWidget::class.java, PlaybackSpotlightWidget()),
                WidgetTarget(PlaybackCommandWidget::class.java, PlaybackCommandWidget()),
                WidgetTarget(UpNextWidget::class.java, UpNextWidget()),
            )
    }
}
