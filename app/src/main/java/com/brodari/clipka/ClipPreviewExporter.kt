
package com.brodari.clipka

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

@UnstableApi
class ClipPreviewExporter(
    private val context: Context,
    private val sourceUri: Uri,
    private val clip: ClipSegment,
    private val onProgress: (String) -> Unit,
    private val onComplete: (File?) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private var finished = false

    fun start() {
        if (clip.validate() !is ClipValidationResult.Valid) {
            finish(null, "Clip settings are invalid")
            return
        }

        if (clip.frameLayout == "SPLIT_DIAGONAL") {
            finish(
                null,
                "Diagonal split preview is not supported yet"
            )
            return
        }

        val file = File(
            context.cacheDir,
            "clipka_preview_${System.currentTimeMillis()}.mp4"
        )
        outputFile = file

        try {
            onProgress("Preparing composition preview...")

            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    transformer = null

                    if (!finished) {
                        finish(file, "Preview ready")
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer = null
                    finish(
                        null,
                        "Preview failed: ${
                            exportException.message ?: "Export error"
                        }"
                    )
                }
            }

            val activeTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            transformer = activeTransformer

            if (
    clip.frameLayout in setOf(
        "SPLIT_VERTICAL",
        "SPLIT_60_40",
        "SPLIT_40_60",
        "SPLIT_HORIZONTAL"
    ) || (
        clip.frameLayout == "CUSTOM" &&
        clip.frames.size > 1
    )
) {
                exportComposition(
                    activeTransformer,
                    mediaItem,
                    file
                )
            } else {
                exportSingle(
                    activeTransformer,
                    mediaItem,
                    file
                )
            }
        } catch (error: Exception) {
            finish(
                null,
                "Preview failed: ${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun createCrop(frame: FrameRegion): Crop {
        val left = -1f + 2f * frame.x
        val right = -1f + 2f * (frame.x + frame.width)
        val top = 1f - 2f * frame.y
        val bottom = 1f - 2f * (frame.y + frame.height)

        return Crop(left, right, bottom, top)
    }

    private fun exportSingle(
        activeTransformer: Transformer,
        mediaItem: MediaItem,
        file: File
    ) {
        val frame = clip.frames.firstOrNull()

        val targetRatio = when (clip.aspectRatio) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            else -> 9f / 16f
        }

        val effects = mutableListOf<androidx.media3.common.Effect>()

        if (
            frame != null &&
            clip.frameLayout in setOf("SINGLE", "CUSTOM")
        ) {
            effects.add(createCrop(frame))
        }

        effects.add(
            Presentation.createForAspectRatio(
                targetRatio,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    effects
                )
            )
            .build()

        activeTransformer.start(
            editedItem,
            file.absolutePath
        )
    }

    private fun exportComposition(
        activeTransformer: Transformer,
        mediaItem: MediaItem,
        file: File
    ) {
        if (clip.frames.size < 2) {
            throw IllegalArgumentException(
                "Combined preview needs at least two frames"
            )
        }

        val videoSequences = clip.frames.map { frame ->
            val videoItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(createCrop(frame))
                    )
                )
                .build()

            EditedMediaItemSequence(listOf(videoItem))
        }

        val audioItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioSequence = EditedMediaItemSequence(
            listOf(audioItem)
        )

        val composition = Composition.Builder(
            videoSequences + audioSequence
        )
            .setVideoCompositorSettings(
                SplitScreenCompositorSettings(
                    frames = clip.frames,
                    aspectRatio = clip.aspectRatio
                )
            )
            .build()

        activeTransformer.start(
            composition,
            file.absolutePath
        )
    }

    private fun finish(
        result: File?,
        message: String
    ) {
        if (finished) return

        finished = true

        if (result == null) {
            outputFile?.delete()
        }

        handler.post {
            onProgress(message)
            onComplete(result)
        }
    }

    fun cancel() {
        if (finished) return

        try {
            transformer?.cancel()
        } catch (_: Exception) {
            // Continue cleanup.
        }

        transformer = null
        finish(null, "Preview cancelled")
    }
}
