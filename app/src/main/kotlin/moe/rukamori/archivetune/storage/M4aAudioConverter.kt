/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Converts an audio file to an AAC-in-MP4 file without blocking the UI thread. */
@Singleton
@UnstableApi
class M4aAudioConverter
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val applicationContext = context.applicationContext
        private val mainHandler = Handler(Looper.getMainLooper())

        suspend fun convert(
            sourceFile: File,
            targetFile: File,
        ): Boolean =
            withContext(Dispatchers.Main.immediate) {
                if (!sourceFile.isFile || sourceFile.length() <= 0L) return@withContext false
                if (targetFile.exists() && !targetFile.delete()) return@withContext false
                targetFile.parentFile?.mkdirs()

                suspendCancellableCoroutine { continuation ->
                    lateinit var transformer: Transformer
                    val listener =
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(targetFile.isFile && targetFile.length() > 0L)
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                Timber.tag(LogTag).w(exportException, "Failed to convert %s to M4A", sourceFile.name)
                                targetFile.delete()
                                if (continuation.isActive) continuation.resume(false)
                            }
                        }

                    transformer =
                        Transformer
                            .Builder(applicationContext)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(listener)
                            .build()

                    continuation.invokeOnCancellation {
                        targetFile.delete()
                        mainHandler.post {
                            transformer.cancel()
                            targetFile.delete()
                        }
                    }

                    try {
                        val editedMediaItem =
                            EditedMediaItem
                                .Builder(MediaItem.fromUri(sourceFile.toUri()))
                                .setRemoveVideo(true)
                                .build()
                        transformer.start(editedMediaItem, targetFile.absolutePath)
                    } catch (throwable: Throwable) {
                        Timber.tag(LogTag).w(throwable, "Unable to start M4A conversion for %s", sourceFile.name)
                        targetFile.delete()
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }

        private companion object {
            const val LogTag = "M4aAudioConverter"
        }
    }
