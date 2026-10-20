
package com.brodari.clipka

import java.util.UUID

/**
 * One editable clip cut from a source video.
 * Each clip has its own aspect ratio.
 */
data class ClipSegment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Clip",
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: String = "9:16"
) {
    val durationMs: Long
        get() = endMs - startMs

    fun validate(): ClipValidationResult {
        if (startMs < 0L) {
            return ClipValidationResult.Invalid(
                "Start time cannot be negative."
            )
        }

        if (endMs <= startMs) {
            return ClipValidationResult.Invalid(
                "End time must be after start time."
            )
        }

        if (durationMs > MAX_CLIP_DURATION_MS) {
            return ClipValidationResult.Invalid(
                "A clip cannot be longer than 3 minutes."
            )
        }

        if (aspectRatio !in SUPPORTED_ASPECT_RATIOS) {
            return ClipValidationResult.Invalid(
                "Unsupported aspect ratio."
            )
        }

        return ClipValidationResult.Valid
    }

    companion object {
        const val MAX_CLIP_DURATION_MS = 180_000L

        val SUPPORTED_ASPECT_RATIOS = setOf(
            "9:16",
            "1:1",
            "16:9"
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
            return ProjectEditResult.Rejected(
                "A clip with this ID already exists."
            )
        }

        if (sourceDurationMs > 0L && clip.endMs > sourceDurationMs) {
            return ProjectEditResult.Rejected(
                "Clip end time exceeds the source video duration."
            )
        }

        return ProjectEditResult.Success(
            copy(
                clips = clips + clip,
                selectedClipId = clip.id
            )
        )
    }

    fun selectClip(clipId: String): ClipProject {
        return if (clips.any { it.id == clipId }) {
            copy(selectedClipId = clipId)
        } else {
            this
        }
    }

    fun updateClip(updatedClip: ClipSegment): ProjectEditResult {
        val index = clips.indexOfFirst { it.id == updatedClip.id }

        if (index == -1) {
            return ProjectEditResult.Rejected("Clip not found.")
        }

        val validation = updatedClip.validate()

        if (validation is ClipValidationResult.Invalid) {
            return ProjectEditResult.Rejected(validation.reason)
        }

        if (
            sourceDurationMs > 0L &&
            updatedClip.endMs > sourceDurationMs
        ) {
            return ProjectEditResult.Rejected(
                "Clip end time exceeds the source video duration."
            )
        }

        val updatedClips = clips.toMutableList()
        updatedClips[index] = updatedClip

        return ProjectEditResult.Success(
            copy(clips = updatedClips)
        )
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

    data class Invalid(
        val reason: String
    ) : ClipValidationResult()
}

sealed class ProjectEditResult {
    data class Success(
        val project: ClipProject
    ) : ProjectEditResult()

    data class Rejected(
        val reason: String
    ) : ProjectEditResult()
}
