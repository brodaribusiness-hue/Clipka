package com.brodari.clipka

import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects

/**
 * Shared composition builder used by both Preview and final Export.
 *
 * Source crop and output placement remain independent:
 * - Crop is created from FrameRegion.x/y/width/height.
 * - Placement is created from outputX/outputY/outputWidth/outputHeight
 *   by SplitScreenCompositorSettings.
 */
@UnstableApi
object ClipkaCompositionFactory {

    private val splitLayouts = setOf(
        "SPLIT_VERTICAL",
        "SPLIT_60_40",
        "SPLIT_40_60",
        "SPLIT_HORIZONTAL"
    )

        fun isMultiFrameComposition(clip: ClipSegment): Boolean {
        return clip.frameLayout in splitLayouts ||
            (clip.frameLayout == "CUSTOM" && clip.frames.isNotEmpty())
    }

    fun validateForComposition(clip: ClipSegment): String? {
        val validation = clip.validate()

        if (validation is ClipValidationResult.Invalid) {
            return validation.reason
        }

        if (clip.frameLayout == "SPLIT_DIAGONAL") {
            return "Diagonal masking is not implemented yet."
        }

                if (
            clip.frameLayout in splitLayouts &&
            clip.frames.size < 2
        ) {
            return "A split composition needs at least two speaker frames."
        }

        return null
    }

    fun createCrop(frame: FrameRegion): Crop {
        val left = -1f + (2f * frame.x)
        val right = -1f + (2f * (frame.x + frame.width))
        val top = 1f - (2f * frame.y)
        val bottom = 1f - (2f * (frame.y + frame.height))

        return Crop(left, right, bottom, top)
    }

    fun createSingleEditedItem(
        mediaItem: MediaItem,
        clip: ClipSegment
    ): EditedMediaItem {
        val targetRatio = aspectRatio(clip.aspectRatio)
        val effects = mutableListOf<Effect>()
                    effects.add(Brightness(clip.brightness))
        effects.add(Contrast(clip.contrast))
        effects.add(
            HslAdjustment.Builder()
                .adjustSaturation(clip.saturation)
                .build()
        )
        

        val frame = clip.frames.firstOrNull()
        if (frame != null) {
            effects.add(createCrop(frame))
        }

        effects.add(
            Presentation.createForAspectRatio(
                targetRatio,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    emptyList(),
                    effects
                )
            )
            .build()
    }

    fun createComposition(
        mediaItem: MediaItem,
        clip: ClipSegment
    ): Composition {
        val error = validateForComposition(clip)
        require(error == null) { error ?: "Invalid composition." }
        require(isMultiFrameComposition(clip)) {
            "This clip uses a single video track."
        }

        val videoSequences = clip.frames.map { frame ->
            val videoItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .setEffects(
                                        Effects(
                        emptyList(),
                        listOf(
                            Brightness(clip.brightness),
                            Contrast(clip.contrast),
                            HslAdjustment.Builder()
                                .adjustSaturation(clip.saturation)
                                .build(),
                            createCrop(frame)
                        )
                    )
                )
                .build()

            EditedMediaItemSequence(listOf(videoItem))
        }

        val audioItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioSequence = EditedMediaItemSequence(
            listOf(audioItem)
        )

        return Composition.Builder(
            videoSequences + audioSequence
        )
            .setVideoCompositorSettings(
                SplitScreenCompositorSettings(
                    frames = clip.frames,
                    aspectRatio = clip.aspectRatio
                )
            )
            .build()
    }

    fun aspectRatio(value: String): Float {
        return when (value) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            else -> 9f / 16f
        }
    }
}
