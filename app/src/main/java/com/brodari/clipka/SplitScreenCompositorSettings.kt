package com.brodari.clipka

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

/**
 * Applies the final output placement of each independently cropped
 * speaker frame.
 *
 * FrameRegion.x/y/width/height describe the source crop.
 * FrameRegion.outputX/outputY/outputWidth/outputHeight describe
 * the destination rectangle in the final composition.
 *
 * Both Preview and Export must use this same settings class.
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

    override fun getOutputSize(inputSizes: List<Size>): Size {
        return outputSize
    }

    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long
    ): OverlaySettings {
        val frame = frames.getOrNull(inputId)
            ?: frames.firstOrNull()
            ?: FrameRegion()

        val left = frame.outputX.coerceIn(0f, 1f)
        val top = frame.outputY.coerceIn(0f, 1f)
        val width = frame.outputWidth.coerceIn(0.01f, 1f - left)
        val height = frame.outputHeight.coerceIn(0.01f, 1f - top)

        // Media3 anchors are expressed in normalized coordinates
        // from -1 to +1, with the screen center at 0,0.
        val centerX = (2f * left) + width - 1f
        val centerY = 1f - (2f * top) - height

        return StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(width, height)
            .setBackgroundFrameAnchor(centerX, centerY)
            .build()
    }
}
