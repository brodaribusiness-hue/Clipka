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
        if (finished) return

        val validationError =
            ClipkaCompositionFactory.validateForComposition(clip)

        if (validationError != null) {
            finish(null, validationError)
            return
        }

        val file = File(
            context.cacheDir,
            "clipka_preview_${System.currentTimeMillis()}.mp4"
        )
        outputFile = file

        try {
            onProgress("Preparing combined preview...")

            val mediaItem = createClippedMediaItem()
            val listener = createListener(file)

            val activeTransformer = Transformer.Builder(context)
                .addListener(listener)
                .build()

            transformer = activeTransformer

            if (ClipkaCompositionFactory.isMultiFrameComposition(clip)) {
                val composition =
                    ClipkaCompositionFactory.createComposition(
                        mediaItem = mediaItem,
                        clip = clip
                    )

                activeTransformer.start(
                    composition,
                    file.absolutePath
                )
            } else {
                val editedItem =
                    ClipkaCompositionFactory.createSingleEditedItem(
                        mediaItem = mediaItem,
                        clip = clip
                    )

                activeTransformer.start(
                    editedItem,
                    file.absolutePath
                )
            }
        } catch (error: Exception) {
            finish(
                null,
                "Preview failed: ${error.message ?: "Unknown error"}"
            )
        }
    }

    private fun createClippedMediaItem(): MediaItem {
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

    private fun createListener(
        file: File
    ): Transformer.Listener {
        return object : Transformer.Listener {

            override fun onCompleted(
                composition: Composition,
                exportResult: ExportResult
            ) {
                transformer = null

                if (!finished) {
                    if (file.exists() && file.length() > 0L) {
                        finish(file, "Combined preview ready")
                    } else {
                        finish(null, "Preview output is empty")
                    }
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
