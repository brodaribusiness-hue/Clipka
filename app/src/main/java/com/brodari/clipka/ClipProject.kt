package com.brodari.clipka

import java.util.UUID

/**

* One independently editable frame region inside a clip.

* 

* Coordinates are normalized from 0.0 to 1.0 relative to the source video.

* x and y represent the top-left corner.
  */
  data class FrameRegion(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Frame",
  val x: Float = 0.25f,
  val y: Float = 0.10f,
  val width: Float = 0.50f,
  val height: Float = 0.80f,
  val detectedFace: Boolean = false,
  val detectionConfidence: Float? = null
  ) {
  fun validate(): String? {
  if (
  !x.isFinite() ||
  !y.isFinite() ||
  !width.isFinite() ||
  !height.isFinite()
  ) {
  return "Frame coordinates must be valid numbers."
  }
  
   if (
     x < 0f ||
     y < 0f ||
     width <= 0f ||
     height <= 0f ||
     x > 1f ||
     y > 1f ||
     width > 1f ||
     height > 1f
 ) {
     return "Frame position and size must be between 0 and 1."
 }

 if (x + width > 1.0001f || y + height > 1.0001f) {
     return "Frame must remain inside the source video."
 }

 val confidence = detectionConfidence
 if (confidence != null && (!confidence.isFinite() || confidence !in 0f..1f)) {
     return "Face detection confidence must be between 0 and 1."
 }

 return null
  
  }
  }

/**

* One editable clip cut from a source video.

* 

* Each clip has its own aspect ratio, frame regions, and frame layout.

* Frame regions are independent between clips.
  */
  data class ClipSegment(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "Clip",
  val startMs: Long,
  val endMs: Long,
  val aspectRatio: String = "9:16",
  val frames: List<FrameRegion> = emptyList(),
  val frameLayout: String = "SINGLE"
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

 if (frameLayout !in SUPPORTED_FRAME_LAYOUTS) {
     return ClipValidationResult.Invalid(
         "Unsupported frame layout."
     )
 }

 if (frames.map { it.id }.distinct().size != frames.size) {
     return ClipValidationResult.Invalid(
         "Frame IDs must be unique within a clip."
     )
 }

 for (frame in frames) {
     val frameError = frame.validate()
     if (frameError != null) {
         return ClipValidationResult.Invalid(
             "${frame.name}: $frameError"
         )
     }
 }

 if (frameLayout == "SPLIT_VERTICAL" && frames.size < 2) {
     return ClipValidationResult.Invalid(
         "Top-and-bottom split layout needs at least 2 frames."
     )
 }

 if (frameLayout == "SPLIT_HORIZONTAL" && frames.size < 2) {
     return ClipValidationResult.Invalid(
         "Side-by-side split layout needs at least 2 frames."
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
  
  fun updateClipFrames(
  clipId: String,
  updatedFrames: List<FrameRegion>,
  layout: String
  ): ProjectEditResult {
  val clip = clips.firstOrNull { it.id == clipId }
  ?: return ProjectEditResult.Rejected("Clip not found.")
  
   return updateClip(
     clip.copy(
         frames = updatedFrames,
         frameLayout = layout
     )
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
