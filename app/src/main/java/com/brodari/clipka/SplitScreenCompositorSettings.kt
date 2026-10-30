
package com.brodari.clipka

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

/**
 * Applies independent output placement to every speaker frame.
 *
 * Source crop:
 * x, y, width, height
 *
 * Final output placement:
 * outputX, outputY, outputWidth, outputHeight
 */
@UnstableApi
class SplitScreenCompositorSettings(
    private val frames: List<FrameRegion>,
    private val aspectRatio: String
) : VideoCompositorSettings {

    private val outputSize: Size
        get() = when (aspectRatio) {
            "1:1" -> Size(1080, 1080)
            "16:9" -> Size(1920, 1080)
            else -> Size(1080, 1920)
        }

    override fun getOutputSize(
        inputSizes: List<Size>
    ): Size {
        return outputSize
    }

    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long
    ): OverlaySettings {
        val frame = frames.getOrNull(inputId)
            ?: frames.firstOrNull()
            ?: FrameRegion()

        val left = frame.outputX.coerceIn(0f, 0.999f)
        val top = frame.outputY.coerceIn(0f, 0.999f)

        val availableWidth = (1f - left).coerceAtLeast(0.001f)
        val availableHeight = (1f - top).coerceAtLeast(0.001f)

        val width = frame.outputWidth.coerceIn(
            0.001f,
            availableWidth
        )

        val height = frame.outputHeight.coerceIn(
            0.001f,
            availableHeight
        )

        val centerX = (2f * left) + width - 1f
        val centerY = 1f - (2f * top) - height

        return StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(width, height)
            .setBackgroundFrameAnchor(centerX, centerY)
            .build()
    }
}
