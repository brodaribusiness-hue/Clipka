
package com.brodari.clipka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Stores Clipka projects locally on the device.
 * No network, account, or paid API is required.
 */
object ClipProjectStorage {

    private const val FILE_NAME = "clipka_project.json"

    private fun projectFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun save(
        context: Context,
        project: ClipProject
    ): Result<Unit> = runCatching {
        val root = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("sourceVideoUri", project.sourceVideoUri)
            put("sourceDurationMs", project.sourceDurationMs)
            put("selectedClipId", project.selectedClipId)

            put("clips", JSONArray().apply {
                project.clips.forEach { clip ->
                    put(JSONObject().apply {
                        put("id", clip.id)
                        put("name", clip.name)
                        put("startMs", clip.startMs)
                        put("endMs", clip.endMs)
                        put("aspectRatio", clip.aspectRatio)
                        put("frameLayout", clip.frameLayout)

                        put("frames", JSONArray().apply {
                            clip.frames.forEach { frame ->
                                put(JSONObject().apply {
                                    put("id", frame.id)
                                    put("name", frame.name)
                                    put("x", frame.x.toDouble())
                                    put("y", frame.y.toDouble())
                                    put("width", frame.width.toDouble())
                                    put("height", frame.height.toDouble())
                                    put("detectedFace", frame.detectedFace)

                                    put(
                                        "detectionConfidence",
                                        frame.detectionConfidence
                                            ?.toDouble()
                                            ?: JSONObject.NULL
                                    )
                                })
                            }
                        })
                    })
                }
            })
        }

        
        val destination = projectFile(context)
        val temporary = File(
            context.filesDir,
            "$FILE_NAME.tmp"
        )

        try {
            temporary.writeText(
                root.toString(2),
                Charsets.UTF_8
            )

            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (
                _: java.nio.file.AtomicMoveNotSupportedException
            ) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }


    fun load(
        context: Context
    ): Result<ClipProject?> = runCatching {
        val file = projectFile(context)

        if (!file.isFile) {
            return@runCatching null
        }

        val root = JSONObject(
            file.readText(Charsets.UTF_8)
        )

        val clipsArray = root.optJSONArray("clips")
            ?: JSONArray()

        val loadedClips = mutableListOf<ClipSegment>()

        for (clipIndex in 0 until clipsArray.length()) {
            val clipObject =
                clipsArray.optJSONObject(clipIndex) ?: continue

            val framesArray =
                clipObject.optJSONArray("frames") ?: JSONArray()

            val loadedFrames = mutableListOf<FrameRegion>()

            for (frameIndex in 0 until framesArray.length()) {
                val frameObject =
                    framesArray.optJSONObject(frameIndex) ?: continue

                val confidenceValue =
                    frameObject.opt("detectionConfidence")

                val confidence =
                    if (
                        confidenceValue == null ||
                        confidenceValue === JSONObject.NULL
                    ) {
                        null
                    } else {
                        frameObject
                            .optDouble("detectionConfidence")
                            .toFloat()
                    }

                loadedFrames.add(
                    FrameRegion(
                        id = frameObject.optString("id")
                            .ifBlank { UUID.randomUUID().toString() },
                        name = frameObject.optString("name", "Frame"),
                        x = frameObject.optDouble("x", 0.25).toFloat(),
                        y = frameObject.optDouble("y", 0.10).toFloat(),
                        width = frameObject.optDouble("width", 0.50).toFloat(),
                        height = frameObject.optDouble("height", 0.80).toFloat(),
                        detectedFace = frameObject.optBoolean(
                            "detectedFace",
                            false
                        ),
                        detectionConfidence = confidence
                    )
                )
            }

            val clip = ClipSegment(
                id = clipObject.optString("id")
                    .ifBlank { UUID.randomUUID().toString() },
                name = clipObject.optString("name", "Clip"),
                startMs = clipObject.optLong("startMs", 0L),
                endMs = clipObject.optLong("endMs", 30_000L),
                aspectRatio = clipObject.optString("aspectRatio", "9:16"),
                frames = loadedFrames,
                frameLayout = clipObject.optString("frameLayout", "SINGLE")
            )

            if (clip.validate() is ClipValidationResult.Valid) {
                loadedClips.add(clip)
            }
        }

        val selectedId = root.optString("selectedClipId")
            .takeIf { id -> loadedClips.any { it.id == id } }

        ClipProject(
            id = root.optString("id")
                .ifBlank { UUID.randomUUID().toString() },
            name = root.optString("name", "Untitled Project"),
            sourceVideoUri = root.optString("sourceVideoUri")
                .takeUnless { it.isBlank() || it == "null" },
            sourceDurationMs = root.optLong(
                "sourceDurationMs",
                0L
            ).coerceAtLeast(0L),
            clips = loadedClips,
            selectedClipId = selectedId
        )
    }

    fun delete(
        context: Context
    ): Result<Unit> = runCatching {
        val file = projectFile(context)

        if (file.exists() && !file.delete()) {
            error("Project file delete nahi ho saki.")
        }
    }
}
