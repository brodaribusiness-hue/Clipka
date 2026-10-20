
package com.brodari.clipka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Detects faces in the middle frame of a selected clip.
 * Runs locally on the device; no video is uploaded.
 */
object ClipkaFaceDetection {

    suspend fun detectFaces(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        maxFaces: Int = 4
    ): List<FrameRegion> = withContext(Dispatchers.IO) {

        require(startMs >= 0L && endMs > startMs) {
            "Clip time range is invalid."
        }

        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null

        try {
            retriever.setDataSource(context, videoUri)

            val middleMs = startMs + (endMs - startMs) / 2L

            bitmap = retriever.getFrameAtTime(
                middleMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: throw IllegalStateException(
                "Could not read a frame from this video."
            )

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                )
                .enableTracking()
                .build()

            val detector = FaceDetection.getClient(options)

            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val faces: List<Face> = Tasks.await(
                    detector.process(image)
                )

                faces
                    .sortedByDescending { face ->
                        face.boundingBox.width().toLong() *
                            face.boundingBox.height().toLong()
                    }
                    .take(maxFaces.coerceIn(1, 10))
                    .mapIndexed { index, face ->
                        createFrameRegion(
                            face = face,
                            index = index,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height
                        )
                    }
            } finally {
                detector.close()
            }
        } finally {
            bitmap?.recycle()
            retriever.release()
        }
    }

    private fun createFrameRegion(
        face: Face,
        index: Int,
        imageWidth: Int,
        imageHeight: Int
    ): FrameRegion {
        val bounds = RectF(face.boundingBox)

        // Add space around the face for a more useful crop.
        val extraX = bounds.width() * 0.45f
        val extraY = bounds.height() * 0.75f

        val left = max(0f, bounds.left - extraX)
        val top = max(0f, bounds.top - extraY)
        val right = min(
            imageWidth.toFloat(),
            bounds.right + extraX
        )
        val bottom = min(
            imageHeight.toFloat(),
            bounds.bottom + extraY
        )

        val x = (left / imageWidth).coerceIn(0f, 1f)
        val y = (top / imageHeight).coerceIn(0f, 1f)
        val width = ((right - left) / imageWidth)
            .coerceIn(0.01f, 1f - x)
        val height = ((bottom - top) / imageHeight)
            .coerceIn(0.01f, 1f - y)

        return FrameRegion(
            name = "Face ${index + 1}",
            x = x,
            y = y,
            width = width,
            height = height,
            detectedFace = true
        )
    }
}
