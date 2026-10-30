
package com.brodari.clipka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.abs

/**
 * On-device visual scene-change detection.
 * This does not identify viral potential or analyze speech.
 */
object AutoClipAnalyzer {

    private const val SAMPLE_SIZE = 32
    private const val MIN_CLIP_MS = 30_000L
    private const val MAX_CLIP_MS = 180_000L
    private const val MIN_SCENE_GAP_MS = 15_000L
    private const val CHANGE_THRESHOLD = 0.26

    fun detectSegments(
        context: Context,
        uri: Uri,
        durationMs: Long
    ): List<Pair<Long, Long>> {
        require(durationMs > 0L) {
            "Video duration is not available."
        }

        val retriever = MediaMetadataRetriever()
        val sceneCuts = mutableListOf<Long>()
        var previous: Bitmap? = null
        var lastCutMs = 0L

        try {
            retriever.setDataSource(context, uri)

            val intervalMs = maxOf(
                5_000L,
                durationMs / 240L
            )

            var timestampMs = 0L

            while (timestampMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        SAMPLE_SIZE,
                        SAMPLE_SIZE,
                        true
                    )

                    if (scaled !== frame) {
                        frame.recycle()
                    }

                    val oldFrame = previous

                    if (oldFrame != null) {
                        val difference = frameDifference(
                            oldFrame,
                            scaled
                        )

                        if (
                            difference >= CHANGE_THRESHOLD &&
                            timestampMs - lastCutMs >= MIN_SCENE_GAP_MS &&
                            timestampMs >= MIN_CLIP_MS
                        ) {
                            sceneCuts.add(timestampMs)
                            lastCutMs = timestampMs
                        }

                        oldFrame.recycle()
                    }

                    previous = scaled
                }

                timestampMs += intervalMs
            }
        } finally {
            previous?.recycle()
            retriever.release()
        }

        return buildSegments(durationMs, sceneCuts)
    }

    private fun frameDifference(
        first: Bitmap,
        second: Bitmap
    ): Double {
        var difference = 0L
        val pixelCount = first.width * first.height

        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)

                difference += abs(Color.red(a) - Color.red(b))
                difference += abs(Color.green(a) - Color.green(b))
                difference += abs(Color.blue(a) - Color.blue(b))
            }
        }

        return difference.toDouble() /
            (pixelCount.toDouble() * 3.0 * 255.0)
    }

    private fun buildSegments(
        durationMs: Long,
        sceneCuts: List<Long>
    ): List<Pair<Long, Long>> {
        if (durationMs <= 0L) return emptyList()

        if (durationMs < MIN_CLIP_MS) {
            return listOf(0L to durationMs)
        }

        if (durationMs <= MAX_CLIP_MS) {
            return listOf(0L to durationMs)
        }

        val segmentCount = (
            (durationMs + MAX_CLIP_MS - 1L) / MAX_CLIP_MS
        ).toInt()

        val cuts = sceneCuts.distinct().sorted()
        val segments = mutableListOf<Pair<Long, Long>>()

        var startMs = 0L

        for (index in 0 until segmentCount - 1) {
            val remainingSegments = segmentCount - index - 1

            val minEndMs = startMs + MIN_CLIP_MS

            val maxEndMs = minOf(
                startMs + MAX_CLIP_MS,
                durationMs - remainingSegments * MIN_CLIP_MS
            )

            val idealEndMs = (
                durationMs.toDouble() *
                    (index + 1).toDouble() /
                    segmentCount.toDouble()
                ).toLong()

            val chosenEndMs = cuts
                .filter { it in minEndMs..maxEndMs }
                .minByOrNull { abs(it - idealEndMs) }
                ?: idealEndMs.coerceIn(minEndMs, maxEndMs)

            segments.add(startMs to chosenEndMs)
            startMs = chosenEndMs
        }

        if (startMs < durationMs) {
            segments.add(startMs to durationMs)
        }

        return segments
    }
}
