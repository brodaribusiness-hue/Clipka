package com.brodari.clipka

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

@UnstableApi
class SplitScreenCompositorSettings(
    private val layout: String,
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
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, 0f)

        when (layout) {
            "SPLIT_VERTICAL" -> {
                settings
                    .setScale(1f, 0.5f)
                    .setBackgroundFrameAnchor(
                        0f,
                        if (inputId == 0) 0.5f else -0.5f
                    )
            }

            "SPLIT_60_40" -> {
                if (inputId == 0) {
                    settings
                        .setScale(1f, 0.6f)
                        .setBackgroundFrameAnchor(0f, 0.4f)
                } else {
                    settings
                        .setScale(1f, 0.4f)
                        .setBackgroundFrameAnchor(0f, -0.6f)
                }
            }

            "SPLIT_40_60" -> {
                if (inputId == 0) {
                    settings
                        .setScale(1f, 0.4f)
                        .setBackgroundFrameAnchor(0f, 0.6f)
                } else {
                    settings
                        .setScale(1f, 0.6f)
                        .setBackgroundFrameAnchor(0f, -0.4f)
                }
            }

            "SPLIT_HORIZONTAL" -> {
                settings
                    .setScale(0.5f, 1f)
                    .setBackgroundFrameAnchor(
                        if (inputId == 0) -0.5f else 0.5f,
                        0f
                    )
            }

            else -> {
                settings
                    .setScale(1f, 1f)
                    .setBackgroundFrameAnchor(0f, 0f)
            }
        }

        return settings.build()
    }
}
