
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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@UnstableApi
class BatchZipExporter(
    private val context: Context,
    private val sourceUri: Uri,
    private val clips: List<ClipSegment>,
    private val onProgress: (String) -> Unit,
    private val onComplete: (File?) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tempFiles = mutableListOf<File>()

    private var activeTransformer: Transformer? = null
    private var currentIndex = 0
    private var finished = false

    fun start() {
        if (clips.isEmpty()) {
            onProgress("No clips to export.")
            finish(null)
            return
        }

        val invalidClip = clips.firstOrNull {
            it.validate() !is ClipValidationResult.Valid
        }

        if (invalidClip != null) {
            onProgress("Invalid clip: ${invalidClip.name}")
            finish(null)
            return
        }

        val diagonalClip = clips.firstOrNull {
            it.frameLayout == "SPLIT_DIAGONAL"
        }

        if (diagonalClip != null) {
            onProgress(
                "Diagonal split export is not supported yet: " +
                    diagonalClip.name
            )
            finish(null)
            return
        }

        exportNextClip()
    }

    private fun createClippedMediaItem(
        clip: ClipSegment
    ): MediaItem {
        return MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs)
                    .build()
            )
            .build()
    }

    private fun getTargetRatio(clip: ClipSegment): Float {
        return when (clip.aspectRatio) {
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            else -> 9f / 16f
        }
    }

    private fun createCrop(frame: FrameRegion): Crop {
        val left = -1f + 2f * frame.x
        val right = -1f + 2f * (frame.x + frame.width)
        val top = 1f - 2f * frame.y
        val bottom = 1f - 2f * (frame.y + frame.height)

        return Crop(left, right, bottom, top)
    }

    private fun createVideoItem(
    mediaItem: MediaItem,
    frame: FrameRegion
): EditedMediaItem {
    return EditedMediaItem.Builder(mediaItem)
        .setRemoveAudio(true)
        .setEffects(
            Effects(
                emptyList(),
                listOf(createCrop(frame))
            )
        )
        .build()
}

    private fun exportNextClip() {
        if (finished) return

        if (currentIndex >= clips.size) {
            createZip()
            return
        }

        val clip = clips[currentIndex]
        val splitLayouts = setOf(
    "SPLIT_VERTICAL",
    "SPLIT_60_40",
    "SPLIT_40_60",
    "SPLIT_HORIZONTAL",
    "SPLIT_DIAGONAL"
)

if (
    clip.frameLayout in splitLayouts &&
    clip.frames.size < 2
) {
    onProgress(
        "Split-screen ke liye 2 frame regions zaroori hain: " +
            clip.name
    )
    finish(null)
    return
}

        val outputFile = File(
            context.cacheDir,
            "clipka_batch_${System.currentTimeMillis()}_$currentIndex.mp4"
        )

        tempFiles.add(outputFile)

        try {
            onProgress(
                "Exporting ${currentIndex + 1}/${clips.size}: ${clip.name}"
            )

            val mediaItem = createClippedMediaItem(clip)
            val targetRatio = getTargetRatio(clip)

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        activeTransformer = null

                        if (finished) return

                        currentIndex++
                        exportNextClip()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        activeTransformer = null

                        onProgress(
                            "Failed: ${clip.name} — " +
                                (
                                    exportException.message
                                        ?: "Export error"
                                )
                        )

                        finish(null)
                    }
                })
                .build()

                        activeTransformer = transformer

            when {
                clip.frameLayout in setOf(
                    "SPLIT_VERTICAL",
                    "SPLIT_60_40",
                    "SPLIT_40_60",
                    "SPLIT_HORIZONTAL"
                ) || (
                    clip.frameLayout == "CUSTOM" &&
                    clip.frames.size > 1
                ) -> {
                    exportSplitClip(
                        transformer = transformer,
                        mediaItem = mediaItem,
                        clip = clip,
                        outputFile = outputFile,
                        targetRatio = targetRatio
                    )
                }

                else -> {
                    exportSingleClip(
                        transformer = transformer,
                        mediaItem = mediaItem,
                        clip = clip,
                        outputFile = outputFile,
                        targetRatio = targetRatio
                    )
                }
            }
        } catch (error: Exception) {
            onProgress(
                "Failed: ${clip.name} — " +
                    (error.message ?: "Unknown error")
            )
            finish(null)
        }
    }

    private fun exportSingleClip(
        transformer: Transformer,
        mediaItem: MediaItem,
        clip: ClipSegment,
        outputFile: File,
        targetRatio: Float
    ) {
        val presentation = Presentation.createForAspectRatio(
            targetRatio,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        val frame = clip.frames.firstOrNull()

        val videoEffects = if (
    clip.frameLayout in setOf("SINGLE", "CUSTOM") &&
    frame != null
) {
            listOf(
                createCrop(frame),
                presentation
            )
        } else {
            listOf(presentation)
        }

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    videoEffects
                )
            )
            .build()

        transformer.start(
            editedItem,
            outputFile.absolutePath
        )
    }

        private fun exportSplitClip(
        transformer: Transformer,
        mediaItem: MediaItem,
        clip: ClipSegment,
        outputFile: File,
        targetRatio: Float
    ) {
        if (clip.frames.size < 2) {
            throw IllegalArgumentException(
                "Split-screen export needs at least two frame regions."
            )
        }

        val videoSequences = clip.frames.map { frame ->
            val videoItem = createVideoItem(
                mediaItem,
                frame
            )

            EditedMediaItemSequence(
                listOf(videoItem)
            )
        }

        val audioItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioSequence = EditedMediaItemSequence(
            listOf(audioItem)
        )

        val allSequences = videoSequences + audioSequence

        val composition = Composition.Builder(allSequences)
            .setVideoCompositorSettings(
                SplitScreenCompositorSettings(
                    frames = clip.frames,
                    aspectRatio = clip.aspectRatio
                )
            )
            .build()

        transformer.start(
            composition,
            outputFile.absolutePath
        )
    }

    private fun createZip() {
        if (finished) return

        try {
            val zipFile = File(
                context.cacheDir,
                "Clipka_Clips_${System.currentTimeMillis()}.zip"
            )

            ZipOutputStream(
                FileOutputStream(zipFile)
            ).use { zip ->
                tempFiles.forEachIndexed { index, file ->
                    if (!file.exists() || file.length() == 0L) {
                        throw IllegalStateException(
                            "Exported clip file is missing or empty."
                        )
                    }

                    val clip = clips[index]

                    val safeName = clip.name
                        .replace(Regex("[^A-Za-z0-9_-]"), "_")
                        .ifBlank { "Clip_${index + 1}" }

                    zip.putNextEntry(
                        ZipEntry("${index + 1}_$safeName.mp4")
                    )

                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }

                    zip.closeEntry()
                }
            }

            finish(zipFile)
        } catch (error: Exception) {
            onProgress(
                "ZIP failed: ${error.message ?: "Unknown error"}"
            )
            finish(null)
        }
    }

    private fun finish(result: File?) {
        if (finished) return

        finished = true
        activeTransformer = null

        tempFiles.forEach { it.delete() }

        handler.post {
            if (result != null) {
                onProgress(
                    "ZIP ready: ${clips.size} clips exported."
                )
            }

            onComplete(result)
        }
    }

    fun cancel() {
        if (finished) return

        try {
            activeTransformer?.cancel()
        } catch (_: Exception) {
            // Continue with cleanup if cancellation throws.
        }

        finish(null)
    }
}
