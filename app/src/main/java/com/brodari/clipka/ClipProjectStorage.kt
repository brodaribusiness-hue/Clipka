
package com.brodari.clipka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Stores multiple Clipka projects locally on the device.
 * No network or paid service is required.
 */
object ClipProjectStorage {

    private const val LEGACY_FILE_NAME = "clipka_project.json"
    private const val PROJECTS_DIR = "clipka_projects"

    private fun projectsDirectory(context: Context): File =
        File(context.filesDir, PROJECTS_DIR).apply {
            if (!exists()) mkdirs()
        }

    private fun projectFile(
        context: Context,
        projectId: String
    ): File {
        val safeId = projectId.replace(
            Regex("[^A-Za-z0-9_-]"),
            "_"
        )
        return File(projectsDirectory(context), "$safeId.json")
    }

    fun save(
        context: Context,
        project: ClipProject
    ): Result<Unit> = runCatching {
        val root = encodeProject(project)
        val destination = projectFile(context, project.id)
        val temporary = File(
            destination.parentFile,
            "${destination.name}.tmp"
        )

        try {
            temporary.writeText(root.toString(2), Charsets.UTF_8)

            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun loadAll(
        context: Context
    ): Result<List<ClipProject>> = runCatching {
        val directory = projectsDirectory(context)

        // Import the previous single-project format, if present.
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (legacyFile.isFile) {
            runCatching {
                val legacyProject = decodeProject(
                    JSONObject(legacyFile.readText(Charsets.UTF_8))
                )
                val destination = projectFile(context, legacyProject.id)

                if (!destination.exists()) {
                    save(context, legacyProject).getOrThrow()
                }

                legacyFile.delete()
            }
        }

        directory.listFiles { file ->
            file.isFile &&
                file.extension == "json"
        }
            ?.mapNotNull { file ->
                runCatching {
                    decodeProject(
                        JSONObject(file.readText(Charsets.UTF_8))
                    )
                }.getOrNull()
            }
            ?.distinctBy { it.id }
            ?.sortedByDescending { it.name.lowercase() }
            ?: emptyList()
    }

    fun loadById(
        context: Context,
        projectId: String
    ): Result<ClipProject?> = runCatching {
        val file = projectFile(context, projectId)

        if (!file.isFile) {
            loadAll(context).getOrThrow()
                .firstOrNull { it.id == projectId }
        } else {
            decodeProject(
                JSONObject(file.readText(Charsets.UTF_8))
            )
        }
    }

    /**
     * Backwards-compatible helper: returns the first available project.
     * New project-list code should use loadAll().
     */
    fun load(
        context: Context
    ): Result<ClipProject?> = runCatching {
        loadAll(context).getOrThrow().firstOrNull()
    }

    fun delete(
        context: Context,
        projectId: String
    ): Result<Unit> = runCatching {
        val file = projectFile(context, projectId)

        if (file.exists() && !file.delete()) {
            error("Project delete nahi ho saka.")
        }
    }

    /**
     * Deletes all saved projects. Use only when explicitly requested.
     */
    fun delete(
        context: Context
    ): Result<Unit> = runCatching {
        val directory = projectsDirectory(context)

        directory.listFiles()?.forEach { file ->
            if (file.isFile && !file.delete()) {
                error("Project file delete nahi ho saki: ${file.name}")
            }
        }

        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (legacyFile.exists() && !legacyFile.delete()) {
            error("Legacy project file delete nahi ho saki.")
        }
    }

    private fun encodeProject(
        project: ClipProject
    ): JSONObject = JSONObject().apply {
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
                                        put("brightness", clip.brightness.toDouble())
                    put("contrast", clip.contrast.toDouble())
                    put("saturation", clip.saturation.toDouble())

                    put("frames", JSONArray().apply {
                        clip.frames.forEach { frame ->
                            put(JSONObject().apply {
                                put("id", frame.id)
                                put("name", frame.name)
                                put("x", frame.x.toDouble())
                                put("y", frame.y.toDouble())
                                put("width", frame.width.toDouble())
                                put("height", frame.height.toDouble())
                                
put("outputX", frame.outputX.toDouble())
put("outputY", frame.outputY.toDouble())
put("outputWidth", frame.outputWidth.toDouble())
put("outputHeight", frame.outputHeight.toDouble())

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

    private fun decodeProject(
        root: JSONObject
    ): ClipProject {
        val clipsArray = root.optJSONArray("clips") ?: JSONArray()
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
                        frameObject.optDouble(
                            "detectionConfidence"
                        ).toFloat()
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
                        
outputX = frameObject.optDouble("outputX", 0.0).toFloat(),
outputY = frameObject.optDouble("outputY", 0.0).toFloat(),
outputWidth = frameObject.optDouble("outputWidth", 1.0).toFloat(),
outputHeight = frameObject.optDouble("outputHeight", 1.0).toFloat(),

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
                                frameLayout = clipObject.optString("frameLayout", "SINGLE"),
                brightness = clipObject.optDouble("brightness", 0.0).toFloat(),
                contrast = clipObject.optDouble("contrast", 0.0).toFloat(),
                saturation = clipObject.optDouble("saturation", 0.0).toFloat()
            )

            if (clip.validate() is ClipValidationResult.Valid) {
                loadedClips.add(clip)
            }
        }

        val selectedId = root.optString("selectedClipId")
            .takeIf { id -> loadedClips.any { it.id == id } }

        return ClipProject(
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
}
