package com.brodari.clipka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.abs

/**
 * Local visual scene analysis.
 *
 * Detects visual scene changes and creates usable clip ranges.
 * It does not claim to predict virality or understand speech.
 */
object AutoClipAnalyzer {

    private const val SAMPLE_SIZE = 32
    private const val MIN_CLIP_MS = 30_000L
    private const val TARGET_CLIP_MS = 60_000L
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

        // A source shorter than 30 seconds is returned as one clip.
        if (durationMs <= MIN_CLIP_MS) {
            return listOf(0L to durationMs)
        }

        val retriever = MediaMetadataRetriever()
        val sceneCuts = mutableListOf<Long>()
        var previousFrame: Bitmap? = null
        var lastCutMs = 0L

        try {
            retriever.setDataSource(context, uri)

            val intervalMs = maxOf(
                2_000L,
                durationMs / 360L
            )

            var timestampMs = 0L

            while (timestampMs < durationMs) {
                val original = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (original != null) {
                    val scaled = Bitmap.createScaledBitmap(
                        original,
                        SAMPLE_SIZE,
                        SAMPLE_SIZE,
                        true
                    )

                    if (scaled !== original) {
                        original.recycle()
                    }

                    val previous = previousFrame

                    if (previous != null) {
                        val difference = frameDifference(
                            previous,
                            scaled
                        )

                        val isValidCut =
                            difference >= CHANGE_THRESHOLD &&
                            timestampMs - lastCutMs >= MIN_SCENE_GAP_MS &&
                            timestampMs >= MIN_CLIP_MS &&
                            durationMs - timestampMs >= MIN_CLIP_MS

                        if (isValidCut) {
                            sceneCuts.add(timestampMs)
                            lastCutMs = timestampMs
                        }

                        previous.recycle()
                    }

                    previousFrame = scaled
                }

                timestampMs += intervalMs
            }
        } finally {
            previousFrame?.recycle()
            retriever.release()
        }

        return buildSegments(
            durationMs = durationMs,
            sceneCuts = sceneCuts
        )
    }

    private fun frameDifference(
        first: Bitmap,
        second: Bitmap
    ): Double {
        if (
            first.width != second.width ||
            first.height != second.height
        ) {
            return 0.0
        }

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

        if (durationMs <= MAX_CLIP_MS) {
            return listOf(0L to durationMs)
        }

        val validCuts = sceneCuts
            .distinct()
            .filter { it in MIN_CLIP_MS until durationMs }
            .sorted()

        val segments = mutableListOf<Pair<Long, Long>>()
        var startMs = 0L

        while (durationMs - startMs > MAX_CLIP_MS) {
            val minimumEnd = startMs + MIN_CLIP_MS
            val maximumEnd = minOf(
                startMs + MAX_CLIP_MS,
                durationMs
            )
            val targetEnd = minOf(
                startMs + TARGET_CLIP_MS,
                maximumEnd
            )

            val sceneEnd = validCuts
                .filter { it in minimumEnd..maximumEnd }
                .minByOrNull { abs(it - targetEnd) }

            val endMs = (
                sceneEnd ?: targetEnd
            ).coerceIn(minimumEnd, maximumEnd)

            if (endMs <= startMs) break

            segments.add(startMs to endMs)
            startMs = endMs
        }

        if (startMs < durationMs) {
            val remaining = durationMs - startMs

            if (
                remaining < MIN_CLIP_MS &&
                segments.isNotEmpty()
            ) {
                val previous = segments.last()
                val combinedDuration = durationMs - previous.first

                if (combinedDuration <= MAX_CLIP_MS) {
                    segments[segments.lastIndex] =
                        previous.first to durationMs
                } else {
                    segments.add(startMs to durationMs)
                }
            } else {
                segments.add(startMs to durationMs)
            }
        }

        return segments.filter { (start, end) ->
            end > start
        }
    }
}
