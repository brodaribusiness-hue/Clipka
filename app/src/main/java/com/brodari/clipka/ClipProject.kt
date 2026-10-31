
package com.brodari.clipka

import java.util.UUID

/**
 * One speaker frame.
 *
 * x/y/width/height = source video crop.
 * outputX/outputY/outputWidth/outputHeight = final split-screen position.
 * All coordinates are normalized from 0.0 to 1.0.
 */
data class FrameRegion(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Frame",
    val x: Float = 0.25f,
    val y: Float = 0.10f,
    val width: Float = 0.50f,
    val height: Float = 0.80f,
    val detectedFace: Boolean = false,
    val detectionConfidence: Float? = null,
    val outputX: Float = 0f,
    val outputY: Float = 0f,
    val outputWidth: Float = 1f,
    val outputHeight: Float = 1f
) {
    fun validate(): String? {
        val sourceValues = listOf(x, y, width, height)
        val outputValues = listOf(
            outputX, outputY, outputWidth, outputHeight
        )

        if (sourceValues.any { !it.isFinite() } ||
            outputValues.any { !it.isFinite() }
        ) {
            return "Frame coordinates must be valid numbers."
        }

        if (x < 0f || y < 0f || width <= 0f || height <= 0f ||
            x > 1f || y > 1f || width > 1f || height > 1f ||
            x + width > 1.0001f || y + height > 1.0001f
        ) {
            return "Source crop must remain inside the video."
        }

        if (outputX < 0f || outputY < 0f ||
            outputWidth <= 0f || outputHeight <= 0f ||
            outputX > 1f || outputY > 1f ||
            outputWidth > 1f || outputHeight > 1f ||
            outputX + outputWidth > 1.0001f ||
            outputY + outputHeight > 1.0001f
        ) {
            return "Output frame must remain inside the final screen."
        }

        val confidence = detectionConfidence
        if (confidence != null &&
            (!confidence.isFinite() || confidence !in 0f..1f)
        ) {
            return "Face detection confidence must be between 0 and 1."
        }

        return null
    }
}

/**
 * One editable clip cut from a source video.
 * All speaker frames are composited into one output video.
 */
data class ClipSegment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Clip",
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: String = "9:16",
    val frames: List<FrameRegion> = emptyList(),
    val frameLayout: String = "SINGLE",
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    val durationMs: Long
        get() = endMs - startMs

    fun validate(): ClipValidationResult {
        if (startMs < 0L) {
            return ClipValidationResult.Invalid("Start time cannot be negative.")
        }

        if (endMs <= startMs) {
            return ClipValidationResult.Invalid("End time must be after start time.")
        }

        if (durationMs > MAX_CLIP_DURATION_MS) {
            return ClipValidationResult.Invalid("A clip cannot be longer than 3 minutes.")
        }

        if (aspectRatio !in SUPPORTED_ASPECT_RATIOS) {
            return ClipValidationResult.Invalid("Unsupported aspect ratio.")
        }

        if (frameLayout !in SUPPORTED_FRAME_LAYOUTS) {
            return ClipValidationResult.Invalid("Unsupported frame layout.")
        }

        if (frames.map { it.id }.distinct().size != frames.size) {
            return ClipValidationResult.Invalid("Frame IDs must be unique within a clip.")
        }

        for (frame in frames) {
            val frameError = frame.validate()
            if (frameError != null) {
                return ClipValidationResult.Invalid("${frame.name}: $frameError")
            }
        }
                if (!brightness.isFinite() || brightness !in -1f..1f) {
            return ClipValidationResult.Invalid("Invalid brightness value.")
        }

        if (!contrast.isFinite() || contrast !in -1f..1f) {
            return ClipValidationResult.Invalid("Invalid contrast value.")
        }

        if (!saturation.isFinite() || saturation !in -100f..100f) {
            return ClipValidationResult.Invalid("Invalid saturation value.")
        }

        if (frameLayout in setOf(
                "SPLIT_VERTICAL",
                "SPLIT_60_40",
                "SPLIT_40_60",
                "SPLIT_HORIZONTAL",
                "SPLIT_DIAGONAL"
            ) && frames.size < 2
        ) {
            return ClipValidationResult.Invalid("Split layouts need at least 2 frames.")
        }

        if (frameLayout == "CUSTOM" && frames.size < 1) {
            return ClipValidationResult.Invalid("Custom layout needs at least 1 frame.")
        }

        return ClipValidationResult.Valid
    }

    companion object {
    const val MIN_CLIP_DURATION_MS = 30_000L
    const val MAX_CLIP_DURATION_MS = 180_000L

        val SUPPORTED_ASPECT_RATIOS = setOf("9:16", "1:1", "16:9")

        val SUPPORTED_FRAME_LAYOUTS = setOf(
            "SINGLE",
            "SPLIT_VERTICAL",
            "SPLIT_60_40",
            "SPLIT_40_60",
            "SPLIT_HORIZONTAL",
            "SPLIT_DIAGONAL",
            "CUSTOM"
        )
    }
}

/**
 * A project can contain multiple independently editable clips.
 */
data class ClipProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val sourceVideoUri: String? = null,
    val sourceDurationMs: Long = 0L,
    val clips: List<ClipSegment> = emptyList(),
    val selectedClipId: String? = null
) {
    fun addClip(clip: ClipSegment): ProjectEditResult {
        val validation = clip.validate()
        if (validation is ClipValidationResult.Invalid) {
            return ProjectEditResult.Rejected(validation.reason)
        }

        if (clips.any { it.id == clip.id }) {
            return ProjectEditResult.Rejected("A clip with this ID already exists.")
        }

        if (sourceDurationMs > 0L && clip.endMs > sourceDurationMs) {
            return ProjectEditResult.Rejected("Clip end time exceeds the source video duration.")
        }

        return ProjectEditResult.Success(
            copy(clips = clips + clip, selectedClipId = clip.id)
        )
    }

    fun selectClip(clipId: String): ClipProject {
        return if (clips.any { it.id == clipId }) copy(selectedClipId = clipId) else this
    }

    fun updateClip(updatedClip: ClipSegment): ProjectEditResult {
        val index = clips.indexOfFirst { it.id == updatedClip.id }
        if (index == -1) return ProjectEditResult.Rejected("Clip not found.")

        val validation = updatedClip.validate()
        if (validation is ClipValidationResult.Invalid) {
            return ProjectEditResult.Rejected(validation.reason)
        }

        if (sourceDurationMs > 0L && updatedClip.endMs > sourceDurationMs) {
            return ProjectEditResult.Rejected("Clip end time exceeds the source video duration.")
        }

        val updatedClips = clips.toMutableList()
        updatedClips[index] = updatedClip
        return ProjectEditResult.Success(copy(clips = updatedClips))
    }

    fun updateClipFrames(
        clipId: String,
        updatedFrames: List<FrameRegion>,
        layout: String
    ): ProjectEditResult {
        val clip = clips.firstOrNull { it.id == clipId }
            ?: return ProjectEditResult.Rejected("Clip not found.")

        return updateClip(clip.copy(frames = updatedFrames, frameLayout = layout))
    }

    fun deleteClip(clipId: String): ClipProject {
        val remainingClips = clips.filterNot { it.id == clipId }
        return copy(
            clips = remainingClips,
            selectedClipId = if (selectedClipId == clipId) {
                remainingClips.firstOrNull()?.id
            } else {
                selectedClipId
            }
        )
    }
}

sealed class ClipValidationResult {
    data object Valid : ClipValidationResult()
    data class Invalid(val reason: String) : ClipValidationResult()
}

sealed class ProjectEditResult {
    data class Success(val project: ClipProject) : ProjectEditResult()
    data class Rejected(val reason: String) : ProjectEditResult()
}
