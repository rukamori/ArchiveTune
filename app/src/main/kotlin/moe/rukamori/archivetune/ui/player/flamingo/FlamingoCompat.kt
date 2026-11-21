/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Compatibility shims for APIs that were referenced by the Flamingo player port
 * but were never part of this codebase. They keep the UI rendering safely until
 * the real integrations are wired up.
 */

package moe.rukamori.archivetune.ui.player.flamingo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel

// TODO(flamingo): These preference keys came from the upstream Flamingo port and have no
// equivalents here. They are local keys so the toggles remain functional, but they are not
// connected to ArchiveTune's settings screens yet.
val AiRomanizeLyricsKey = booleanPreferencesKey("flamingoAiRomanizeLyrics")
val AutoAiRomanizeLyricsKey = booleanPreferencesKey("flamingoAutoAiRomanizeLyrics")
val AutoTranslateExcludedLanguagesKey =
    stringSetPreferencesKey("flamingoAutoTranslateExcludedLanguages")
val AutoTranslateLyricsKey = booleanPreferencesKey("flamingoAutoTranslateLyrics")
val FlamingoHideSlideBarKey = booleanPreferencesKey("flamingoHideSlideBar")
val ProceduralArtworkEnabledKey = booleanPreferencesKey("flamingoProceduralArtworkEnabled")

// TODO(flamingo): LyricsMenuViewModel has no translationDismissedMediaIds flow in this repo.
// Auto-translation dismissal should be wired to a persisted "dismissed" set later.
val FlamingoTranslationDismissedMediaIds: Set<String> = emptySet()

// TODO(flamingo): LyricsUtils has no language-detection helpers here; these are conservative
// fallbacks so the auto-translate path cannot fire spuriously.
fun flamingoHasTranslation(lyrics: String): Boolean =
    lyrics.contains("translation:", ignoreCase = true)

fun flamingoShouldAutoTranslate(
    lyrics: String,
    targetLanguage: String,
    excludedLanguageCodes: Set<String>,
): Boolean =
    lyrics.isNotBlank() &&
        targetLanguage.isNotBlank() &&
        targetLanguage !in excludedLanguageCodes

// TODO(flamingo): FormatEntity has no isLossless() helper; derive it from the codec strings.
fun FormatEntity.isLossless(): Boolean =
    listOf(codecs, mimeType).any { value ->
        listOf("FLAC", "ALAC", "WAV", "PCM", "APE").any { value.contains(it, ignoreCase = true) }
    }

// TODO(flamingo): The kyant backdrop library is not a dependency here. These no-op wrappers
// keep the glass popups laid out correctly with an opaque scrim instead of a real backdrop.
class PlatformBackdrop {
    fun requestRecord() = Unit
}

data class FlamingoLiquidGlassTuning(
    val saturation: Float = 1f,
    val blurFactor: Float = 1f,
)

val LocalLiquidGlassTuning = compositionLocalOf { FlamingoLiquidGlassTuning() }

@Composable
fun rememberBackdrop(color: Color): PlatformBackdrop = remember(color) { PlatformBackdrop() }

@Composable
fun rememberLiquidGlassEnabled(): Boolean = false

fun Modifier.layerBackdrop(backdrop: PlatformBackdrop?): Modifier = this

fun Density.colorControls(saturation: Float) = Unit

fun Density.blur(radiusPx: Float) = Unit

fun Modifier.drawBackdrop(
    backdrop: PlatformBackdrop,
    effects: Density.() -> Unit,
    onDrawBackdrop: ContentDrawScope.(drawBackdrop: () -> Unit) -> Unit,
    shape: () -> Shape,
): Modifier = this

// TODO(flamingo): Canvas playback is currently self-contained; a shared loop-sync leader is
// not supported by CanvasArtworkPlayer yet.
class CanvasLoopSync

// TODO(flamingo): No inline music-video player exists in this repo. When video artwork state
// becomes available, feed its URLs into CanvasArtworkPlayer; until then render an empty box.
data class FlamingoVideoArtworkState(
    val primaryUrl: String? = null,
    val fallbackUrl: String? = null,
)

val LocalVideoArtworkState = compositionLocalOf<FlamingoVideoArtworkState?> { null }
val LocalVideoPlaybackFailed = compositionLocalOf { false }

@Composable
fun InlineVideoPlayer(
    controlsOnTap: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    modifier: Modifier = Modifier,
) {
    val state = LocalVideoArtworkState.current
    if (state != null && !LocalVideoPlaybackFailed.current) {
        CanvasArtworkPlayer(
            source = CanvasSource.ALL,
            primaryUrl = state.primaryUrl,
            fallbackUrl = state.fallbackUrl,
            isPlaying = true,
            resizeMode = resizeMode,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}

// TODO(flamingo): The procedural artwork renderer was not merged. Fall back to the static
// artwork image so the stage is never blank.
@Composable
fun ProceduralArtworkFallback(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

// TODO(flamingo): The anchored lyrics overflow menu was not merged. Reuse the existing
// LyricsMenu content inside a lightweight anchored panel.
@Composable
fun AnchoredLyricsOverflowMenu(
    iconBoundsInRoot: Rect,
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: PlatformBackdrop?,
    showTranslationActions: Boolean = true,
) {
    val density = LocalDensity.current
    val topPadding = with(density) { (iconBoundsInRoot.bottom + 8f).toDp().coerceAtLeast(0.dp) }
    val autoHideControlsState = rememberUpdatedState(false)
    val lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(top = topPadding, end = 16.dp)
                    .background(Color(0xEE1C1C1E), RoundedCornerShape(16.dp))
                    .clickable(enabled = false, onClick = {}),
        ) {
            LyricsMenu(
                lyricsProvider = lyricsProvider,
                mediaMetadataProvider = mediaMetadataProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                autoHideControlsState = autoHideControlsState,
                onAutoHideControlsChange = {},
                onDismiss = onDismiss,
                lyricsRenderViewModel = lyricsRenderViewModel,
            )
        }
    }
}
