/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.modules.ModuleAudioSelection
import moe.rukamori.archivetune.modules.ModuleCheckStatus
import okio.ByteString
import java.io.IOException

data class PlaybackFailure(
    val error: PlaybackException,
    val source: PlaybackFailureSource?,
    val trackTitle: String?,
    val trackArtists: String?,
)

sealed interface PlaybackFailureSource {
    data class Module(
        val name: String,
        val version: String,
        val author: String,
        val icon: ByteString?,
        val upstream: String,
        val variant: String,
        val mimeType: String,
        val codecs: String,
        val bitrate: Int,
        val sampleRate: Int?,
        val offline: Boolean,
        val preparationFailed: Boolean,
    ) : PlaybackFailureSource

    data object ModulePipeline : PlaybackFailureSource

    data class YouTubeMusic(val consultedModules: List<ConsultedModule>) : PlaybackFailureSource

    data class DirectLink(val host: String) : PlaybackFailureSource

    data object LocalFile : PlaybackFailureSource
}

data class ConsultedModule(
    val name: String,
    val status: ModuleCheckStatus?,
)

internal sealed interface ModuleSourceOutcome {
    data class Selected(val selection: ModuleAudioSelection, val offline: Boolean) : ModuleSourceOutcome

    data class Fallback(val startedAtMs: Long) : ModuleSourceOutcome

    data object Bypassed : ModuleSourceOutcome

    data class Failed(val selection: ModuleAudioSelection?, val offline: Boolean) : ModuleSourceOutcome
}

internal class ModuleSourcePreparationException(cause: Throwable) : IOException("Audio source could not be prepared", cause)
