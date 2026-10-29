
package com.brodari.clipka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.abs

/**
 * On-device scene-change detection.
 * This detects visual changes, not viral potential.
 */
object AutoClipAnalyzer {

    private const val SAMPLE_SIZE = 32
    private const val MIN_CLIP_MS = 30_000L
    private const val TARGET_CLIP_MS = 60_000L
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

            // Limit analysis work for long videos.
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
        val maxClipMs = ClipSegment.MAX_CLIP_DURATION_MS
        val cuts = sceneCuts.distinct().sorted()
        val segments = mutableListOf<Pair<Long, Long>>()
        var startMs = 0L

        while (durationMs - startMs > maxClipMs) {
            val minEndMs = startMs + MIN_CLIP_MS
            val maxEndMs = minOf(
                startMs + maxClipMs,
                durationMs
            )
            val targetEndMs = startMs + TARGET_CLIP_MS

            val chosenEndMs = cuts
                .filter { it in minEndMs..maxEndMs }
                .minByOrNull { abs(it - targetEndMs) }
                ?: targetEndMs.coerceIn(minEndMs, maxEndMs)

            segments.add(startMs to chosenEndMs)
            startMs = chosenEndMs
        }

        if (startMs < durationMs) {
            val remainingMs = durationMs - startMs

            if (
                remainingMs < MIN_CLIP_MS &&
                segments.isNotEmpty() &&
                durationMs - segments.last().first <= maxClipMs
            ) {
                val previousStart = segments.last().first
                segments[segments.lastIndex] =
                    previousStart to durationMs
            } else {
                segments.add(startMs to durationMs)
            }
        }

        return segments.filter { (start, end) -> end > start }
    }
}
