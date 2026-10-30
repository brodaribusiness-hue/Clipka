
package com.brodari.clipka

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

@UnstableApi
class SplitScreenCompositorSettings(
    private val frames: List<FrameRegion>,
    private val aspectRatio: String
) : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: List<Size>): Size {
        return when (aspectRatio) {
            "1:1" -> Size(1080, 1080)
            "16:9" -> Size(1920, 1080)
            else -> Size(1080, 1920)
        }
    }

    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long
    ): OverlaySettings {
        val frame = frames.getOrNull(inputId)
            ?: frames.firstOrNull()
            ?: FrameRegion()

        val centerX = (2f * frame.outputX) +
            frame.outputWidth - 1f

        val centerY = 1f -
            (2f * frame.outputY) -
            frame.outputHeight

        return StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(
                frame.outputWidth,
                frame.outputHeight
            )
            .setBackgroundFrameAnchor(centerX, centerY)
            .build()
    }
}
