package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ClipkaAccent = Color(0xFFB7A9FF)
private val ClipkaBackground = Color(0xFF101116)
private val ClipkaCard = Color(0xFF1B1C24)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ClipkaAccent,
                    background = ClipkaBackground,
                    surface = ClipkaCard
                )
            ) {
                ClipkaScreen()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipkaScreen() {
    val context = LocalContext.current

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("No video selected") }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    var startInput by remember { mutableStateOf("00:00") }
    var endInput by remember { mutableStateOf("00:30") }

    var clips by remember { mutableStateOf(listOf<ClipSegment>()) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var previewingClipId by remember { mutableStateOf<String?>(null) }

    var sourceVideoAspectRatio by remember {
        mutableStateOf(16f / 9f)
    }

    var clipError by remember { mutableStateOf("") }
    var exportStatus by remember { mutableStateOf("") }
    var faceDetectionStatus by remember { mutableStateOf("") }
    var projectStatus by remember { mutableStateOf("") }

    var isDetectingFaces by remember { mutableStateOf(false) }
    val faceDetectionScope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var pendingExportClip by remember {
        mutableStateOf<ClipSegment?>(null)
    }
    var activeTransformer by remember {
        mutableStateOf<Transformer?>(null)
    }

    var isBatchExporting by remember { mutableStateOf(false) }
    var pendingZipFile by remember { mutableStateOf<File?>(null) }
    var activeBatchExporter by remember {
        mutableStateOf<BatchZipExporter?>(null)
    }

    var undoStack by remember {
        mutableStateOf(listOf<List<ClipSegment>>())
    }
    var redoStack by remember {
        mutableStateOf(listOf<List<ClipSegment>>())
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val previewClip = clips.firstOrNull {
        it.id == previewingClipId
    }

    val previewRatio = when (previewClip?.aspectRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        else -> 16f / 9f
    }

    fun applyClipChange(updatedClips: List<ClipSegment>) {
        undoStack = undoStack + listOf(clips)
        redoStack = emptyList()
        clips = updatedClips
        clipError = ""
    }

    fun selectClip(segment: ClipSegment) {
        selectedClipId = segment.id
        startInput = formatTime(segment.startMs)
        endInput = formatTime(segment.endMs)
        clipError = ""
    }

    fun showClipInPreview(
        segment: ClipSegment,
        play: Boolean
    ) {
        selectClip(segment)
        previewingClipId = segment.id
        exoPlayer.pause()
        exoPlayer.seekTo(segment.startMs)

        if (play) {
            exoPlayer.play()
        }
    }

    fun saveProject() {
        val sourceUri = videoUri

        if (sourceUri == null) {
            projectStatus = "Pehle video select karo."
            return
        }

        val project = ClipProject(
            name = "Clipka Project",
            sourceVideoUri = sourceUri.toString(),
            sourceDurationMs = durationMs,
            clips = clips.toList(),
            selectedClipId = selectedClipId
        )

        ClipProjectStorage.save(context, project)
            .onSuccess {
                projectStatus = "Project locally save ho gaya."
            }
            .onFailure { error ->
                projectStatus =
                    "Save failed: ${error.message ?: "Unknown error"}"
            }
    }

    fun loadProject() {
        ClipProjectStorage.load(context)
            .onSuccess { project ->
                if (project == null) {
                    projectStatus = "Abhi koi saved project nahi hai."
                } else {
                    val restoredUri = project.sourceVideoUri
                        ?.let { Uri.parse(it) }

                    try {
                        if (restoredUri != null) {
                            exoPlayer.setMediaItem(
                                MediaItem.fromUri(restoredUri)
                            )
                            exoPlayer.prepare()
                            exoPlayer.pause()
                        } else {
                            exoPlayer.clearMediaItems()
                        }

                        videoUri = restoredUri
                        videoName = restoredUri
                            ?.lastPathSegment
                            ?.substringAfterLast('/')
                            ?: "No video selected"

                        clips = project.clips
                        selectedClipId = project.selectedClipId
                            ?.takeIf { id ->
                                project.clips.any { it.id == id }
                            }

                        previewingClipId = null
                        durationMs = project.sourceDurationMs
                        positionMs = 0L
                        isPlaying = false
                        isSeeking = false
                        seekValue = 0f
                        undoStack = emptyList()
                        redoStack = emptyList()
                        startInput = "00:00"
                        endInput = "00:30"
                        clipError = ""
                        exportStatus = ""

                        projectStatus = "Saved project load ho gaya."
                    } catch (error: Exception) {
                        projectStatus =
                            "Load failed: ${error.message ?: "Video access error"}"
                    }
                }
            }
            .onFailure { error ->
                projectStatus =
                    "Load failed: ${error.message ?: "Unknown error"}"
            }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Continue if persistent permission is unavailable.
            }

            videoUri = selectedUri
            videoName = selectedUri.lastPathSegment
                ?.substringAfterLast('/')
                ?: "Selected video"

            exoPlayer.setMediaItem(MediaItem.fromUri(selectedUri))
            exoPlayer.prepare()
            exoPlayer.pause()

            isPlaying = false
            positionMs = 0L
            durationMs = 0L
            clips = emptyList()
            selectedClipId = null
            previewingClipId = null
            undoStack = emptyList()
            redoStack = emptyList()
            startInput = "00:00"
            endInput = "00:30"
            clipError = ""
            exportStatus = ""
        }
    }
    
    val exportDestinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destinationUri: Uri? ->
        val clipToExport = pendingExportClip
        val sourceUri = videoUri

        if (destinationUri == null) {
            exportStatus = "Export cancelled."
            pendingExportClip = null
        } else if (clipToExport == null || sourceUri == null) {
            exportStatus = "Video ya clip select nahi hui."
            pendingExportClip = null
        } else {
            val outputFile = File(
                context.cacheDir,
                "clipka_export_${System.currentTimeMillis()}.mp4"
            )

            isExporting = true
            exportStatus = "Clip export ho rahi hai..."
            exoPlayer.pause()
            isPlaying = false

            try {
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipToExport.startMs)
                            .setEndPositionMs(clipToExport.endMs)
                            .build()
                    )
                    .build()

                if (clipToExport.frameLayout == "SPLIT_DIAGONAL") {
                    throw IllegalArgumentException(
                        "Diagonal split export abhi supported nahi hai."
                    )
                }

                val targetAspectRatio = when (clipToExport.aspectRatio) {
                    "9:16" -> 9f / 16f
                    "1:1" -> 1f
                    "16:9" -> 16f / 9f
                    else -> 9f / 16f
                }

                val presentationEffect = Presentation.createForAspectRatio(
                    targetAspectRatio,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )

                val splitLayouts = setOf(
                    "SPLIT_VERTICAL",
                    "SPLIT_60_40",
                    "SPLIT_40_60",
                    "SPLIT_HORIZONTAL"
                )

                val isSplitLayout =
                    clipToExport.frameLayout in splitLayouts

                var editedMediaItem: EditedMediaItem? = null
                var splitComposition: Composition? = null

                if (isSplitLayout) {
                    if (clipToExport.frames.size < 2) {
                        throw IllegalArgumentException(
                            "Split-screen ke liye 2 frame regions zaroori hain."
                        )
                    }

                    fun makeVideoItem(frame: FrameRegion): EditedMediaItem {
                        val left = -1f + 2f * frame.x
                        val right = -1f + 2f * (frame.x + frame.width)
                        val top = 1f - 2f * frame.y
                        val bottom = 1f - 2f * (frame.y + frame.height)

                        return EditedMediaItem.Builder(clippedMediaItem)
                            .setRemoveAudio(true)
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(Crop(left, right, bottom, top))
                                )
                            )
                            .build()
                    }

                    val firstVideo = makeVideoItem(clipToExport.frames[0])
                    val secondVideo = makeVideoItem(clipToExport.frames[1])

                    val audioItem = EditedMediaItem.Builder(clippedMediaItem)
                        .setRemoveVideo(true)
                        .build()

                    splitComposition = Composition.Builder(
                        EditedMediaItemSequence(listOf(firstVideo)),
                        EditedMediaItemSequence(listOf(secondVideo)),
                        EditedMediaItemSequence(listOf(audioItem))
                    )
                        .setVideoCompositorSettings(
                            SplitScreenCompositorSettings(
                                layout = clipToExport.frameLayout,
                                aspectRatio = clipToExport.aspectRatio
                            )
                        )
                        .build()
                } else {
                    val selectedFrame = clipToExport.frames.firstOrNull()

                    val videoEffects = if (
                        clipToExport.frameLayout in setOf("SINGLE", "CUSTOM") &&
                        selectedFrame != null
                    ) {
                        val left = -1f + 2f * selectedFrame.x
                        val right = -1f + 2f *
                            (selectedFrame.x + selectedFrame.width)
                        val top = 1f - 2f * selectedFrame.y
                        val bottom = 1f - 2f *
                            (selectedFrame.y + selectedFrame.height)

                        listOf(
                            Crop(left, right, bottom, top),
                            presentationEffect
                        )
                    } else {
                        listOf(presentationEffect)
                    }

                    editedMediaItem = EditedMediaItem.Builder(clippedMediaItem)
                        .setEffects(
                            Effects(emptyList(), videoEffects)
                        )
                        .build()
                }

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            Thread {
                                try {
                                    val outputStream =
                                        context.contentResolver
                                            .openOutputStream(destinationUri)
                                            ?: throw IOException(
                                                "Save location open nahi ho saki."
                                            )

                                    outputStream.use { destination ->
                                        outputFile.inputStream().use { input ->
                                            input.copyTo(destination)
                                        }
                                    }

                                    outputFile.delete()

                                    Handler(Looper.getMainLooper()).post {
                                        isExporting = false
                                        activeTransformer = null
                                        pendingExportClip = null
                                        exportStatus =
                                            "Export complete: ${clipToExport.name}"
                                    }
                                } catch (exception: Exception) {
                                    outputFile.delete()

                                    Handler(Looper.getMainLooper()).post {
                                        isExporting = false
                                        activeTransformer = null
                                        pendingExportClip = null
                                        exportStatus =
                                            "Save failed: ${exception.message}"
                                    }
                                }
                            }.start()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            outputFile.delete()
                            isExporting = false
                            activeTransformer = null
                            pendingExportClip = null
                            exportStatus =
                                "Export failed: ${exportException.message}"
                        }
                    })
                    .build()

                activeTransformer = transformer

                if (splitComposition != null) {
                    transformer.start(
                        splitComposition!!,
                        outputFile.absolutePath
                    )
                } else {
                    transformer.start(
                        checkNotNull(editedMediaItem),
                        outputFile.absolutePath
                    )
                }
            } catch (exception: Exception) {
                outputFile.delete()
                isExporting = false
                activeTransformer = null
                pendingExportClip = null
                exportStatus =
                    "Export start nahi hua: ${exception.message}"
            }
        }
    }

    val zipDestinationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destinationUri: Uri? ->
        val zipFile = pendingZipFile

        if (destinationUri == null) {
            zipFile?.delete()
            pendingZipFile = null
            isBatchExporting = false
            activeBatchExporter = null
            exportStatus = "ZIP save cancelled."
        } else if (zipFile == null || !zipFile.exists()) {
            pendingZipFile = null
            isBatchExporting = false
            activeBatchExporter = null
            exportStatus = "ZIP file nahi mili."
        } else {
            exportStatus = "ZIP save ho rahi hai..."

            Thread {
                try {
                    val outputStream = context.contentResolver
                        .openOutputStream(destinationUri)
                        ?: throw IOException("Save location open nahi hui.")

                    outputStream.use { destination ->
                        zipFile.inputStream().use { input ->
                            input.copyTo(destination)
                        }
                    }

                    zipFile.delete()

                    Handler(Looper.getMainLooper()).post {
                        pendingZipFile = null
                        isBatchExporting = false
                        activeBatchExporter = null
                        exportStatus = "Export complete: ZIP saved."
                    }
                } catch (exception: Exception) {
                    zipFile.delete()

                    Handler(Looper.getMainLooper()).post {
                        pendingZipFile = null
                        isBatchExporting = false
                        activeBatchExporter = null
                        exportStatus =
                            "ZIP save failed: ${exception.message}"
                    }
                }
            }.start()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(
                videoSize: androidx.media3.common.VideoSize
            ) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    sourceVideoAspectRatio =
                        videoSize.width.toFloat() *
                            videoSize.pixelWidthHeightRatio /
                            videoSize.height.toFloat()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val actualDuration = exoPlayer.duration
                if (actualDuration > 0L) {
                    durationMs = actualDuration
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)

                val actualDuration = exoPlayer.duration
                if (actualDuration > 0L) {
                    durationMs = actualDuration
                }
            }

            val activeClip = clips.firstOrNull {
                it.id == previewingClipId
            }

            if (
                activeClip != null &&
                exoPlayer.isPlaying &&
                exoPlayer.currentPosition >= activeClip.endMs
            ) {
                exoPlayer.pause()
                exoPlayer.seekTo(activeClip.endMs)
                positionMs = activeClip.endMs
            }

            delay(200L)
        }
    }

    // ─────────────────────────────────────────────
    // CAPCUT-STYLE EDITOR: HEADER + PREVIEW + TIMELINE
    // ─────────────────────────────────────────────

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ClipkaBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top editor toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Clipka",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "VIDEO EDITOR",
                        color = ClipkaAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack = redoStack + listOf(clips)
                                clips = undoStack.last()
                                undoStack = undoStack.dropLast(1)
                                selectedClipId = clips.firstOrNull()?.id
                                previewingClipId = null
                                clipError = ""
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Text("Undo", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack = undoStack + listOf(clips)
                                clips = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                selectedClipId = clips.firstOrNull()?.id
                                previewingClipId = null
                                clipError = ""
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Text("Redo", fontSize = 11.sp)
                    }
                }
            }

            // Video preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1B1C24),
                border = BorderStroke(1.dp, Color(0xFF34313F))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (previewClip != null) {
                                "CLIP PREVIEW"
                            } else {
                                "PREVIEW"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (videoUri != null) videoName else "No video",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val safeRatio = previewRatio.coerceIn(0.45f, 2.2f)
                        val previewWidth = minOf(
                            maxWidth,
                            300.dp * safeRatio
                        )
                        val previewHeight = minOf(
                            300.dp,
                            previewWidth / safeRatio
                        )

                        Card(
                            modifier = Modifier
                                .width(previewWidth)
                                .height(previewHeight),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black
                            )
                        ) {
                            if (videoUri == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = ClipkaAccent,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            "Import a video to start",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                Box(Modifier.fillMaxSize()) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { viewContext ->
                                            PlayerView(viewContext).apply {
                                                player = exoPlayer
                                                useController = false
                                                resizeMode =
                                                    if (previewClip != null) {
                                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    } else {
                                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                    }
                                            }
                                        },
                                        update = { playerView ->
                                            playerView.player = exoPlayer
                                            playerView.resizeMode =
                                                if (previewClip != null) {
                                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                } else {
                                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                }
                                        }
                                    )

                                    if (
                                        previewClip != null &&
                                        previewClip.frames.isNotEmpty()
                                    ) {
                                        FramePreviewOverlay(
                                            frames = previewClip.frames,
                                            modifier = Modifier.fillMaxSize(),
                                            sourceVideoAspectRatio =
                                                sourceVideoAspectRatio,
                                            targetAspectRatio = previewRatio
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                previewingClipId = null
                                exoPlayer.seekTo(0L)
                                exoPlayer.pause()
                            },
                            enabled = videoUri != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart"
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else if (videoUri != null) {
                                    val activeClip = clips.firstOrNull {
                                        it.id == previewingClipId
                                    }

                                    if (
                                        activeClip != null &&
                                        exoPlayer.currentPosition >= activeClip.endMs
                                    ) {
                                        exoPlayer.seekTo(activeClip.startMs)
                                    }

                                    exoPlayer.play()
                                }
                            },
                            enabled = videoUri != null
                        ) {
                            Icon(
                                imageVector = if (isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                    }

                    Text(
                        text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                        modifier = Modifier.fillMaxWidth(),
                        color = ClipkaAccent,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Output aspect ratio
                    Text(
                        text = "CANVAS RATIO",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("9:16", "1:1", "16:9").forEach { ratio ->
                            val active = previewClip?.aspectRatio == ratio

                            OutlinedButton(
                                onClick = {
    val selectedId = selectedClipId

    if (selectedId != null) {
        val updatedClips = clips.map { clip ->
            if (clip.id == selectedId) {
                clip.copy(aspectRatio = ratio)
            } else {
                clip
            }
        }

        applyClipChange(updatedClips)
    } else {
        clipError =
            "Select a clip before changing its ratio."
    }
},
                                enabled = selectedClipId != null,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) ClipkaAccent
                                    else Color(0xFF454550)
                                )
                            ) {
                                Text(
                                    text = ratio,
                                    color = if (active) ClipkaAccent
                                    else Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (clipError.isNotBlank()) {
                        Text(
                            text = clipError,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Timeline
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF191A22),
                border = BorderStroke(1.dp, Color(0xFF34313F))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TIMELINE",
                            color = ClipkaAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${clips.size} clips",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }

                    Slider(
                        value = if (isSeeking) {
                            seekValue
                        } else if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs)
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { value ->
                            isSeeking = true
                            seekValue = value
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0L) {
                                previewingClipId = null
                                exoPlayer.seekTo(
                                    (seekValue * durationMs).toLong()
                                )
                            }
                            isSeeking = false
                        },
                        enabled = videoUri != null && durationMs > 0L
                    )

                    Text(
                        "VIDEO TRACK",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (clips.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = ClipkaBackground
                        ) {
                            Text(
                                text = "Your clips will appear here",
                                modifier = Modifier.padding(14.dp),
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            clips.forEachIndexed { index, segment ->
                                val selected =
                                    segment.id == selectedClipId

                                Surface(
                                    modifier = Modifier
                                        .width(145.dp)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) {
                                                ClipkaAccent
                                            } else {
                                                Color(0xFF393944)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) {
                                        Color(0xFF302944)
                                    } else {
                                        ClipkaBackground
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(10.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "CLIP ${index + 1}",
                                            color = ClipkaAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            segment.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )
                                        Text(
                                            "${formatTime(segment.startMs)} – " +
                                                formatTime(segment.endMs),
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                showClipInPreview(segment, false)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (selected) "Selected"
                                                else "Select clip",
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────
            // CLIP TRIM & ADD CLIP
            // ─────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = ClipkaCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "EDIT CLIP",
                        color = ClipkaAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Set the start and end time",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startInput,
                            onValueChange = { startInput = it },
                            label = { Text("Start (MM:SS)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it },
                            label = { Text("End (MM:SS)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            val startTime = parseTime(startInput)
                            val endTime = parseTime(endInput)

                            clipError = validateClipTimes(
                                startMs = startTime,
                                endMs = endTime,
                                videoDurationMs = durationMs,
                                videoMissing = videoUri == null
                            )

                            if (
                                clipError.isEmpty() &&
                                startTime != null &&
                                endTime != null
                            ) {
                                val newClip = ClipSegment(
                                    name = "Clip ${clips.size + 1}",
                                    startMs = startTime,
                                    endMs = endTime,
                                    aspectRatio = "9:16"
                                )

                                applyClipChange(clips + newClip)
                                selectedClipId = newClip.id
                                previewingClipId = newClip.id
                                exoPlayer.pause()
                                exoPlayer.seekTo(newClip.startMs)
                            }
                        },
                        enabled = videoUri != null &&
                            durationMs > 0L &&
                            !isExporting &&
                            !isBatchExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("+ Add Clip")
                    }

                    OutlinedButton(
                        onClick = {
                            val selected = clips.firstOrNull {
                                it.id == selectedClipId
                            }
                            val startTime = parseTime(startInput)
                            val endTime = parseTime(endInput)

                            clipError = if (selected == null) {
                                "Select a clip first."
                            } else {
                                validateClipTimes(
                                    startMs = startTime,
                                    endMs = endTime,
                                    videoDurationMs = durationMs,
                                    videoMissing = videoUri == null
                                )
                            }

                            if (
                                clipError.isEmpty() &&
                                selected != null &&
                                startTime != null &&
                                endTime != null
                            ) {
                                val updated = selected.copy(
                                    startMs = startTime,
                                    endMs = endTime
                                )

                                applyClipChange(
                                    clips.map {
                                        if (it.id == updated.id) updated else it
                                    }
                                )

                                selectedClipId = updated.id
                                previewingClipId = updated.id
                                exoPlayer.pause()
                                exoPlayer.seekTo(updated.startMs)
                            }
                        },
                        enabled = selectedClipId != null &&
                            !isExporting &&
                            !isBatchExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Trim")
                    }

                    if (clipError.isNotBlank()) {
                        Text(
                            clipError,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ─────────────────────────────────────
            // CLIP LIST
            // ─────────────────────────────────────
            Text(
                "MY CLIPS (${clips.size})",
                color = ClipkaAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (clips.isEmpty()) {
                Text(
                    "Your added clips will appear here.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            clips.forEachIndexed { index, clip ->
                val selected = clip.id == selectedClipId
                val playingPreview = clip.id == previewingClipId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                ClipkaAccent
                            } else {
                                Color(0xFF353541)
                            },
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = ClipkaCard
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "CLIP ${index + 1}  •  ${clip.name}",
                            color = if (selected) ClipkaAccent else Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "${formatTime(clip.startMs)} – " +
                                "${formatTime(clip.endMs)}  •  " +
                                "${formatTime(clip.durationMs)}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        Text(
                            "Ratio: ${clip.aspectRatio}  |  Layout: ${clip.frameLayout}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedClipId = clip.id
                                    startInput = formatTime(clip.startMs)
                                    endInput = formatTime(clip.endMs)
                                    showClipInPreview(clip, false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    applyClipChange(
                                        clips.filterNot { it.id == clip.id }
                                    )

                                    if (selectedClipId == clip.id) {
                                        selectedClipId = null
                                        startInput = "00:00"
                                        endInput = "00:30"
                                    }

                                    if (previewingClipId == clip.id) {
                                        previewingClipId = null
                                        exoPlayer.pause()
                                    }
                                },
                                enabled = !isExporting && !isBatchExporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    selectedClipId = clip.id
                                    startInput = formatTime(clip.startMs)
                                    endInput = formatTime(clip.endMs)
                                    showClipInPreview(clip, true)
                                },
                                enabled = videoUri != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (playingPreview && isPlaying) {
                                        "Playing"
                                    } else {
                                        "Preview"
                                    },
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("9:16", "1:1", "16:9").forEach { ratio ->
                                OutlinedButton(
                                    onClick = {
                                        applyClipChange(
                                            clips.map {
                                                if (it.id == clip.id) {
                                                    it.copy(aspectRatio = ratio)
                                                } else {
                                                    it
                                                }
                                            }
                                        )
                                    }
                                ) {
                                    Text(
                                        ratio,
                                        color = if (clip.aspectRatio == ratio) {
                                            ClipkaAccent
                                        } else {
                                            Color.White
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────
            // EXPORT
            // ─────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = ClipkaCard
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "EXPORT",
                        color = ClipkaAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Export selected clip as MP4 or export all clips as ZIP.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            val clip = clips.firstOrNull {
                                it.id == selectedClipId
                            }

                            if (
                                clip != null &&
                                videoUri != null &&
                                !isExporting &&
                                !isBatchExporting
                            ) {
                                pendingExportClip = clip
                                exoPlayer.pause()
                                exportDestinationPicker.launch(
                                    "${clip.name.replace(
                                        Regex("[^A-Za-z0-9_-]"),
                                        "_"
                                    )}.mp4"
                                )
                            }
                        },
                        enabled = videoUri != null &&
                            selectedClipId != null &&
                            !isExporting &&
                            !isBatchExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            if (isExporting) "Exporting MP4…" else "Export Selected MP4"
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val sourceUri = videoUri
                            val clipsToExport = clips.toList()

                            if (
                                sourceUri != null &&
                                clipsToExport.isNotEmpty() &&
                                !isExporting &&
                                !isBatchExporting
                            ) {
                                exoPlayer.pause()
                                isPlaying = false
                                isBatchExporting = true
                                exportStatus = "Preparing ZIP export…"

                                val exporter = BatchZipExporter(
                                    context = context,
                                    sourceUri = sourceUri,
                                    clips = clipsToExport,
                                    onProgress = { message ->
                                        Handler(Looper.getMainLooper()).post {
                                            exportStatus = message
                                        }
                                    },
                                    onComplete = { zipFile ->
                                        activeBatchExporter = null

                                        if (zipFile == null) {
                                            isBatchExporting = false
                                            if (
                                                !exportStatus.startsWith("Failed:") &&
                                                !exportStatus.startsWith("ZIP failed:")
                                            ) {
                                                exportStatus = "ZIP export failed."
                                            }
                                        } else {
                                            pendingZipFile = zipFile
                                            zipDestinationPicker.launch(
                                                "Clipka_Clips.zip"
                                            )
                                        }
                                    }
                                )

                                activeBatchExporter = exporter
                                exporter.start()
                            }
                        },
                        enabled = videoUri != null &&
                            clips.isNotEmpty() &&
                            !isExporting &&
                            !isBatchExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            if (isBatchExporting) {
                                "Exporting ZIP…"
                            } else {
                                "Export All Clips (ZIP)"
                            }
                        )
                    }

                    if (exportStatus.isNotBlank()) {
                        Text(
                            exportStatus,
                            color = ClipkaAccent,
                            fontSize = 12.sp
                        )
                    }

                    // Save and load project
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveProject() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Project")
                        }

                        OutlinedButton(
                            onClick = { loadProject() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load Project")
                        }
                    }

                    if (projectStatus.isNotBlank()) {
                        Text(
                            projectStatus,
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ─────────────────────────────────────
            // FRAME & FACE EDITOR
            // ─────────────────────────────────────
            val frameEditorClip = clips.firstOrNull {
                it.id == selectedClipId
            }

            if (frameEditorClip != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ClipkaCard
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "FRAME & FACE EDITOR",
                            color = ClipkaAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Adjust framing for ${frameEditorClip.name}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Detect faces, then adjust speaker framing and split-screen layout.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                val sourceUri = videoUri
                                val clip = frameEditorClip

                                if (sourceUri == null) {
                                    faceDetectionStatus =
                                        "Pehle video select karo."
                                } else if (!isDetectingFaces) {
                                    isDetectingFaces = true
                                    faceDetectionStatus =
                                        "Faces detect ho rahe hain..."

                                    faceDetectionScope.launch {
                                        try {
                                            val detectedFrames =
                                                ClipkaFaceDetection.detectFaces(
                                                    context = context,
                                                    videoUri = sourceUri,
                                                    startMs = clip.startMs,
                                                    endMs = clip.endMs
                                                )

                                            if (detectedFrames.isEmpty()) {
                                                faceDetectionStatus =
                                                    "Koi face nahi mila. Doosra clip try karo."
                                            } else {
                                                val updatedLayout =
                                                    if (detectedFrames.size >= 2) {
                                                        "SPLIT_VERTICAL"
                                                    } else {
                                                        "SINGLE"
                                                    }

                                                val framesForLayout =
                                                    if (detectedFrames.size >= 2) {
                                                        detectedFrames.take(2)
                                                    } else {
                                                        detectedFrames
                                                    }

                                                applyClipChange(
                                                    clips.map { item ->
                                                        if (item.id == clip.id) {
                                                            item.copy(
                                                                frames = framesForLayout,
                                                                frameLayout = updatedLayout
                                                            )
                                                        } else {
                                                            item
                                                        }
                                                    }
                                                )

                                                faceDetectionStatus =
                                                    "${detectedFrames.size} face(s) detected."
                                            }
                                        } catch (error: Exception) {
                                            faceDetectionStatus =
                                                "Detection failed: " +
                                                    (error.message ?: "unknown error")
                                        } finally {
                                            isDetectingFaces = false
                                        }
                                    }
                                }
                            },
                            enabled = !isDetectingFaces && videoUri != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isDetectingFaces) {
                                    "Detecting Faces..."
                                } else {
                                    "Detect Faces"
                                }
                            )
                        }

                        if (faceDetectionStatus.isNotEmpty()) {
                            Text(
                                text = faceDetectionStatus,
                                color = ClipkaAccent,
                                fontSize = 13.sp
                            )
                        }

                        Divider(
                            color = Color(0xFF393544),
                            thickness = 1.dp
                        )

                        FrameEditor(
                            clip = frameEditorClip,
                            onFramesChange = { updatedFrames ->
                                applyClipChange(
                                    clips.map { item ->
                                        if (item.id == frameEditorClip.id) {
                                            item.copy(frames = updatedFrames)
                                        } else {
                                            item
                                        }
                                    }
                                )
                            },
                            onLayoutChange = { updatedLayout ->
                                applyClipChange(
                                    clips.map { item ->
                                        if (item.id == frameEditorClip.id) {
                                            item.copy(frameLayout = updatedLayout)
                                        } else {
                                            item
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // ─────────────────────────────────────
            // EXPORT TIP
            // ─────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF211E2B),
                border = BorderStroke(
                    1.dp,
                    Color(0xFF393344)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "TIP",
                        color = ClipkaAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Export each clip your way",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Choose each clip's aspect ratio, then export individual MP4 files or a ZIP batch.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// CLIP TIME VALIDATION
// ─────────────────────────────────────────────
private fun validateClipTimes(
    startMs: Long?,
    endMs: Long?,
    videoDurationMs: Long,
    videoMissing: Boolean
): String {
    return when {
        videoMissing ->
            "Pehle video select karo."

        startMs == null || endMs == null ->
            "Time format sahi nahi. Example: 01:25"

        startMs < 0L ->
            "Start time negative nahi ho sakta."

        endMs <= startMs ->
            "End time, start time se aage hona chahiye."

        endMs - startMs > ClipSegment.MAX_CLIP_DURATION_MS ->
            "Clip maximum 3 minutes ka ho sakta hai."

        videoDurationMs > 0L && endMs > videoDurationMs ->
            "End time video duration se zyada hai."

        else -> ""
    }
}

// ─────────────────────────────────────────────
// TIME PARSER
// Accepts MM:SS or H:MM:SS
// ─────────────────────────────────────────────
private fun parseTime(value: String): Long? {
    val parts = value.trim().split(":")

    if (parts.size !in 2..3) return null

    val numbers = parts.map { part ->
        part.toLongOrNull() ?: return null
    }

    if (numbers.any { it < 0L }) return null
    if (numbers.last() >= 60L) return null

    if (parts.size == 3 && numbers[1] >= 60L) {
        return null
    }

    val totalSeconds = if (parts.size == 2) {
        numbers[0] * 60L + numbers[1]
    } else {
        numbers[0] * 3600L +
            numbers[1] * 60L +
            numbers[2]
    }

    return totalSeconds * 1000L
}

// ─────────────────────────────────────────────
// TIME FORMATTER
// ─────────────────────────────────────────────
private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
