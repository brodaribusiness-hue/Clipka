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
import kotlin.math.sqrt

/**
 * Local on-device face detection for Clipka.
 *
 * Samples the complete clip, groups detections by position,
 * and averages detected regions to reduce unstable crop positions.
 */
object ClipkaFaceDetection {

    private const val SAMPLE_COUNT = 12
    private const val FACE_MATCH_DISTANCE = 0.18f

    private data class FaceCluster(
        var leftTotal: Float,
        var topTotal: Float,
        var rightTotal: Float,
        var bottomTotal: Float,
        var count: Int,
        var largestArea: Float
    ) {
        fun add(bounds: RectF) {
            leftTotal += bounds.left
            topTotal += bounds.top
            rightTotal += bounds.right
            bottomTotal += bounds.bottom
            count++

            val area = bounds.width() * bounds.height()
            largestArea = max(largestArea, area)
        }

        fun averageBounds(): RectF {
            val divisor = count.toFloat()

            return RectF(
                leftTotal / divisor,
                topTotal / divisor,
                rightTotal / divisor,
                bottomTotal / divisor
            )
        }
    }

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
        val clusters = mutableListOf<FaceCluster>()

        var processedFrames = 0
        var videoWidth = 0
        var videoHeight = 0

        try {
            retriever.setDataSource(context, videoUri)

            videoWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0

            videoHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0

            if (videoWidth <= 0 || videoHeight <= 0) {
                throw IllegalStateException(
                    "Could not read video dimensions."
                )
            }

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                )
                .enableTracking()
                .build()

            val detector = FaceDetection.getClient(options)

            try {
                val clipLengthMs = endMs - startMs

                for (sampleIndex in 0 until SAMPLE_COUNT) {

                    // Include the beginning and end of the clip.
                    val sampleMs = startMs +
                        (
                            clipLengthMs *
                                sampleIndex /
                                (SAMPLE_COUNT - 1)
                            )

                    var bitmap: Bitmap? = null

                    try {
                        bitmap = retriever.getFrameAtTime(
                            sampleMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )

                        val currentBitmap = bitmap ?: continue

                        if (
                            currentBitmap.width <= 0 ||
                            currentBitmap.height <= 0
                        ) {
                            continue
                        }

                        val image = InputImage.fromBitmap(
                            currentBitmap,
                            0
                        )

                        val faces: List<Face> = Tasks.await(
                            detector.process(image)
                        )

                        processedFrames++

                        val imageDiagonal = sqrt(
                            currentBitmap.width.toFloat() *
                                currentBitmap.width +
                                currentBitmap.height.toFloat() *
                                currentBitmap.height
                        ).coerceAtLeast(1f)

                        for (face in faces) {
                            val bounds = RectF(face.boundingBox)

                            bounds.left = bounds.left.coerceIn(
                                0f,
                                currentBitmap.width.toFloat()
                            )
                            bounds.top = bounds.top.coerceIn(
                                0f,
                                currentBitmap.height.toFloat()
                            )
                            bounds.right = bounds.right.coerceIn(
                                0f,
                                currentBitmap.width.toFloat()
                            )
                            bounds.bottom = bounds.bottom.coerceIn(
                                0f,
                                currentBitmap.height.toFloat()
                            )

                            if (
                                bounds.width() <= 0f ||
                                bounds.height() <= 0f
                            ) {
                                continue
                            }

                            val faceCenterX =
                                (bounds.left + bounds.right) / 2f
                            val faceCenterY =
                                (bounds.top + bounds.bottom) / 2f

                            val matchingCluster =
                                clusters.minByOrNull { cluster ->
                                    val average =
                                        cluster.averageBounds()

                                    val clusterCenterX =
                                        (average.left + average.right) / 2f
                                    val clusterCenterY =
                                        (average.top + average.bottom) / 2f

                                    val dx =
                                        faceCenterX - clusterCenterX
                                    val dy =
                                        faceCenterY - clusterCenterY

                                    sqrt(dx * dx + dy * dy) /
                                        imageDiagonal
                                }?.takeIf { cluster ->
                                    val average =
                                        cluster.averageBounds()

                                    val clusterCenterX =
                                        (average.left + average.right) / 2f
                                    val clusterCenterY =
                                        (average.top + average.bottom) / 2f

                                    val dx =
                                        faceCenterX - clusterCenterX
                                    val dy =
                                        faceCenterY - clusterCenterY

                                    sqrt(dx * dx + dy * dy) /
                                        imageDiagonal <=
                                        FACE_MATCH_DISTANCE
                                }

                            if (matchingCluster != null) {
                                matchingCluster.add(bounds)
                            } else {
                                clusters.add(
                                    FaceCluster(
                                        leftTotal = bounds.left,
                                        topTotal = bounds.top,
                                        rightTotal = bounds.right,
                                        bottomTotal = bounds.bottom,
                                        count = 1,
                                        largestArea =
                                            bounds.width() *
                                                bounds.height()
                                    )
                                )
                            }
                        }
                    } finally {
                        bitmap?.recycle()
                    }
                }
            } finally {
                detector.close()
            }
        } finally {
            retriever.release()
        }

        if (processedFrames == 0) {
            throw IllegalStateException(
                "Could not read video frames. Try another video."
            )
        }

        clusters
            .sortedWith(
                compareByDescending<FaceCluster> { it.count }
                    .thenByDescending { it.largestArea }
            )
            .take(maxFaces.coerceIn(1, 10))
            .mapIndexed { index, cluster ->
                createFrameRegion(
                    bounds = cluster.averageBounds(),
                    index = index,
                    imageWidth = videoWidth,
                    imageHeight = videoHeight,
                    confidence = (
                        cluster.count.toFloat() / SAMPLE_COUNT
                        ).coerceIn(0f, 1f)
                )
            }
    }

    private fun createFrameRegion(
        bounds: RectF,
        index: Int,
        imageWidth: Int,
        imageHeight: Int,
        confidence: Float
    ): FrameRegion {

        val safeWidth = imageWidth.coerceAtLeast(1).toFloat()
        val safeHeight = imageHeight.coerceAtLeast(1).toFloat()

        // Leave space around the face for a more natural crop.
        val extraX = bounds.width() * 0.45f
        val extraY = bounds.height() * 0.75f

        val left = max(0f, bounds.left - extraX)
        val top = max(0f, bounds.top - extraY)
        val right = min(safeWidth, bounds.right + extraX)
        val bottom = min(safeHeight, bounds.bottom + extraY)

        val x = (left / safeWidth).coerceIn(0f, 1f)
        val y = (top / safeHeight).coerceIn(0f, 1f)

        val width = ((right - left) / safeWidth)
            .coerceIn(0.01f, 1f - x)

        val height = ((bottom - top) / safeHeight)
            .coerceIn(0.01f, 1f - y)

        return FrameRegion(
            name = "Face ${index + 1}",
            x = x,
            y = y,
            width = width,
            height = height,
            detectedFace = true,
            detectionConfidence = confidence
        )
    }
}
