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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import moe.rukamori.archivetune.widget.ListeningInsightsWidget
import moe.rukamori.archivetune.widget.LoadWidgetInsightsUseCase
import moe.rukamori.archivetune.widget.MusicWidget
import moe.rukamori.archivetune.widget.MusicWidgetKeys
import moe.rukamori.archivetune.widget.NowPlayingCardWidget
import moe.rukamori.archivetune.widget.PlaybackCapsuleWidget
import moe.rukamori.archivetune.widget.PlaybackCommandWidget
import moe.rukamori.archivetune.widget.PlaybackDeckWidget
import moe.rukamori.archivetune.widget.PlaybackSpotlightWidget
import moe.rukamori.archivetune.widget.WidgetInsightsSnapshot
import moe.rukamori.archivetune.widget.toWidgetPreferenceValue
import java.io.File

internal class MusicServiceWidgetUpdater(
    private val service: MusicService,
    private val player: Player,
    private val scope: CoroutineScope,
    private val loadWidgetInsights: LoadWidgetInsightsUseCase,
) {
    private var progressJob: Job? = null

    fun update() {
        scope.launch(SilentHandler) {
            pushState()
        }
    }

    fun updateProgressTracking() {
        progressJob?.cancel()
        if (player.isPlaying && player.duration > 0) {
            progressJob =
                scope.launch(SilentHandler) {
                    while (isActive && player.isPlaying) {
                        updateProgress(player.playbackProgress())
                        delay(1_000)
                    }
                }
        }
    }

    private suspend fun pushState() {
        val mediaItem = player.currentMediaItem
        val meta = mediaItem?.mediaMetadata
        val artFile = meta?.artworkUri?.let { cacheAlbumArt(it) }
        val dominantColor = artFile?.let { extractDominantColor(it) }
        val snapshot =
            WidgetSnapshot(
                title = meta?.title?.toString() ?: service.getString(R.string.no_track_playing),
                artist = meta?.artist?.toString().orEmpty(),
                isPlaying = player.isPlaying,
                isAvailable = mediaItem != null,
                playbackPosition = player.playbackProgress(),
                artPath = artFile?.absolutePath,
                dominantColor = dominantColor,
                insights = WidgetInsightsSnapshot.Empty,
            )

        playbackWidgets.forEach { target ->
            updateWidget(target, snapshot)
        }
    }

    private suspend fun updateProgress(progress: Float) {
        progressWidgets.forEach { target ->
            val ids = GlanceAppWidgetManager(service).getGlanceIds(target.widgetClass)
            ids.forEach { id ->
                updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutableWidgetPreferences().apply {
                        this[MusicWidgetKeys.PLAYBACK_POSITION] = progress
                    }
                }
                target.widget.update(service, id)
            }
        }
    }

    private suspend fun updateWidget(
        target: WidgetTarget,
        snapshot: WidgetSnapshot,
    ) {
        val ids = GlanceAppWidgetManager(service).getGlanceIds(target.widgetClass)
        if (ids.isEmpty()) return

        val targetSnapshot =
            if (target.requiresInsights) {
                snapshot.copy(insights = loadInsightsSnapshot())
            } else {
                snapshot
            }

        ids.forEach { id ->
            updateAppWidgetState(service, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutableWidgetPreferences().apply {
                    writeSnapshot(targetSnapshot)
                }
            }
            target.widget.update(service, id)
        }
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
        }

    private fun MutablePreferences.writeSnapshot(snapshot: WidgetSnapshot) {
        this[MusicWidgetKeys.TRACK_TITLE] = snapshot.title
        this[MusicWidgetKeys.TRACK_ARTIST] = snapshot.artist
        this[MusicWidgetKeys.IS_PLAYING] = snapshot.isPlaying
        this[MusicWidgetKeys.IS_AVAILABLE] = snapshot.isAvailable
        this[MusicWidgetKeys.PLAYBACK_POSITION] = snapshot.playbackPosition

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
            val dest = File(service.cacheDir, "widget_art_${Integer.toHexString(uri.toString().hashCode())}.jpg")

            if (uri.scheme == "content" || uri.scheme == "file") {
                return@withContext try {
                    service.contentResolver.openInputStream(uri)?.use { src ->
                        dest.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    if (dest.exists() && dest.length() > 0) dest else null
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
                } catch (_: Exception) {
                    null
                }
            }

            null
        }

    private suspend fun extractDominantColor(file: File): Int? =
        withContext(Dispatchers.Default) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val palette = Palette.from(bitmap).generate()
                palette.getDarkVibrantColor(
                    palette.getDominantColor(android.graphics.Color.DKGRAY),
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun Player.playbackProgress(): Float =
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    private data class WidgetSnapshot(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val isAvailable: Boolean,
        val playbackPosition: Float,
        val artPath: String?,
        val dominantColor: Int?,
        val insights: WidgetInsightsSnapshot,
    )

    private data class WidgetTarget(
        val widgetClass: Class<out GlanceAppWidget>,
        val widget: GlanceAppWidget,
        val requiresInsights: Boolean = false,
    )

    private companion object {
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
            )
    }
}
