package com.brodari.clipka

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
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
        if (finished) return

        if (clips.isEmpty()) {
            finish(null, "No clips to export.")
            return
        }

        val invalidClip = clips.firstOrNull { clip ->
            ClipkaCompositionFactory.validateForComposition(clip) != null
        }

        if (invalidClip != null) {
            val reason =
                ClipkaCompositionFactory.validateForComposition(
                    invalidClip
                ) ?: "Invalid clip settings."

            finish(
                null,
                "Cannot export ${invalidClip.name}: $reason"
            )
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

    private fun exportNextClip() {
        if (finished) return

        if (currentIndex >= clips.size) {
            createZip()
            return
        }

        val clip = clips[currentIndex]
        val outputFile = File(
            context.cacheDir,
            "clipka_batch_${System.currentTimeMillis()}_$currentIndex.mp4"
        )

        tempFiles.add(outputFile)

        try {
            postProgress(
                "Exporting ${currentIndex + 1}/${clips.size}: ${clip.name}"
            )

            val mediaItem = createClippedMediaItem(clip)

            val listener = object : Transformer.Listener {

                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    activeTransformer = null

                    if (finished) return

                    if (!outputFile.exists() || outputFile.length() <= 0L) {
                        finish(
                            null,
                            "Export failed: ${clip.name} produced an empty file."
                        )
                        return
                    }

                    currentIndex++
                    exportNextClip()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    activeTransformer = null

                    finish(
                        null,
                        "Failed: ${clip.name} — ${
                            exportException.message ?: "Export error"
                        }"
                    )
                }
            }

            val transformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            activeTransformer = transformer

            if (ClipkaCompositionFactory.isMultiFrameComposition(clip)) {
                val composition =
                    ClipkaCompositionFactory.createComposition(
                        mediaItem = mediaItem,
                        clip = clip
                    )

                transformer.start(
                    composition,
                    outputFile.absolutePath
                )
            } else {
                val editedItem =
                    ClipkaCompositionFactory.createSingleEditedItem(
                        mediaItem = mediaItem,
                        clip = clip
                    )

                transformer.start(
                    editedItem,
                    outputFile.absolutePath
                )
            }
        } catch (error: Exception) {
            finish(
                null,
                "Failed: ${clip.name} — ${
                    error.message ?: "Unknown error"
                }"
            )
        }
    }

    private fun createZip() {
        if (finished) return

        var zipFile: File? = null

        try {
            val output = File(
                context.cacheDir,
                "Clipka_Clips_${System.currentTimeMillis()}.zip"
            )
            zipFile = output

            ZipOutputStream(
                FileOutputStream(output)
            ).use { zip ->
                tempFiles.forEachIndexed { index, file ->
                    if (!file.exists() || file.length() <= 0L) {
                        throw IllegalStateException(
                            "Clip ${index + 1} is missing or empty."
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

            if (!output.exists() || output.length() <= 0L) {
                throw IllegalStateException("ZIP file is empty.")
            }

            finish(
                output,
                "ZIP ready: ${clips.size} clips exported."
            )
        } catch (error: Exception) {
            zipFile?.delete()

            finish(
                null,
                "ZIP failed: ${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun postProgress(message: String) {
        handler.post {
            if (!finished) {
                onProgress(message)
            }
        }
    }

    private fun finish(
        result: File?,
        message: String
    ) {
        if (finished) return
        finished = true

        activeTransformer = null
        tempFiles.forEach { it.delete() }

        handler.post {
            onProgress(message)
            onComplete(result)
        }
    }

    fun cancel() {
        if (finished) return

        try {
            activeTransformer?.cancel()
        } catch (_: Exception) {
            // Continue cleanup.
        }

        activeTransformer = null
        finish(null, "Export cancelled.")
    }
}
