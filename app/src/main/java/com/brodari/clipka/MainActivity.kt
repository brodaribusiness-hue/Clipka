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
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.transformer.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.Crop
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material3.Divider
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
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
                    primary = Color(0xFF9B8CFF),
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

    var clipError by remember { mutableStateOf("") }
    var exportStatus by remember { mutableStateOf("") }
    var faceDetectionStatus by remember { mutableStateOf("") }
    var isDetectingFaces by remember { mutableStateOf(false) }
    val faceDetectionScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportClip by remember { mutableStateOf<ClipSegment?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }

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

    fun showClipInPreview(segment: ClipSegment, play: Boolean) {
        selectClip(segment)
        previewingClipId = segment.id
        exoPlayer.pause()
        exoPlayer.seekTo(segment.startMs)

        if (play) {
            exoPlayer.play()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
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
            exportStatus = "Clip crop aur export ho rahi hai..."
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

                val selectedFrame = clipToExport.frames.firstOrNull()

                val videoEffects = if (
                    clipToExport.frameLayout == "SINGLE" &&
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

                val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem)
                    .setEffects(
                        Effects(
                            emptyList(),
                            videoEffects
                        )
                    )
                    .build()

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
                                            "Export complete: ${clipToExport.name} (${clipToExport.aspectRatio})."
                                    }
                                } catch (exception: Exception) {
                                    outputFile.delete()

                                    Handler(Looper.getMainLooper()).post {
                                        isExporting = false
                                        activeTransformer = null
                                        pendingExportClip = null
                                        exportStatus =
                                            "Save failed: ${exception.message ?: "unknown error"}"
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
                                "Export failed: ${exportException.message ?: "unsupported video"}"
                        }
                    })
                    .build()

                activeTransformer = transformer

                transformer.start(
                    editedMediaItem,
                    outputFile.absolutePath
                )
            } catch (exception: Exception) {
                outputFile.delete()
                isExporting = false
                activeTransformer = null
                pendingExportClip = null
                exportStatus =
                    "Export start nahi hua: ${exception.message ?: "unknown error"}"
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
                            "ZIP save failed: ${exception.message ?: "unknown error"}"
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ClipkaBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ClipkaCard
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "CLIPKA",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ClipkaAccent
                        )

                        Text(
                            text = "AI VIDEO CLIP STUDIO",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = if (videoUri != null) {
                                "Project ready to edit"
                            } else {
                                "Create your next short"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ClipkaBackground
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 15.dp,
                                vertical = 11.dp
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = clips.size.toString(),
                                color = ClipkaAccent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "CLIPS",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
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

                        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (previewClip != null) {
                            "CLIP PREVIEW"
                        } else {
                            "VIDEO PREVIEW"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text = if (previewClip != null) {
                            "${previewClip.aspectRatio} • ${previewClip.name}"
                        } else {
                            "Select a clip to preview its framing"
                        },
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF353044)
                ) {
                    Text(
                        text = if (previewClip != null) {
                            previewClip.aspectRatio
                        } else {
                            "ORIGINAL"
                        },
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),
                        color = ClipkaAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                val previewWidth = minOf(
                    maxWidth,
                    250.dp * previewRatio
                )
                val previewHeight = minOf(
                    250.dp,
                    previewWidth / previewRatio
                )

                Card(
                    modifier = Modifier
                        .width(previewWidth)
                        .height(previewHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black
                    ),
                    shape = RoundedCornerShape(18.dp)
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
                                    modifier = Modifier.size(52.dp),
                                    tint = ClipkaAccent
                                )
                                Text(
                                    text = "Select a video to preview",
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        player = exoPlayer
                                        useController = false
                                        resizeMode = if (previewClip != null) {
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        } else {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = exoPlayer
                                    playerView.resizeMode = if (previewClip != null) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            )

                            if (previewClip != null && previewClip.frames.isNotEmpty()) {
                                FramePreviewOverlay(
                                    frames = previewClip.frames,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
                    
            Text(
                text = if (previewClip != null) {
                    "Previewing ${previewClip.name}: " +
                        "${formatTime(previewClip.startMs)} - " +
                        formatTime(previewClip.endMs)
                } else {
                    "Original video • No clip ratio applied"
                },
                color = ClipkaAccent,
                fontSize = 12.sp
            )

                            }
                        }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ClipkaCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "VIDEO SOURCE",
                                color = ClipkaAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = "Import and preview",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Video source",
                            tint = ClipkaAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = ClipkaBackground
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = ClipkaAccent,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = if (videoUri != null) {
                                        "Selected video"
                                    } else {
                                        "No video selected"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )

                                Text(
                                    text = videoName,
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            videoPicker.launch(arrayOf("video/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (videoUri == null) {
                                "Choose video"
                            } else {
                                "Change video"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                            enabled = videoUri != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
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

                        OutlinedButton(
                            onClick = {
                                previewingClipId = null
                                exoPlayer.seekTo(0L)
                                exoPlayer.pause()
                            },
                            enabled = videoUri != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Restart")
                        }
                    }
                }
            }


            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ClipkaCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TIMELINE",
                                color = ClipkaAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = "Navigate your video",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ClipkaBackground
                        ) {
                            Text(
                                text = if (durationMs > 0L) {
                                    "${(durationMs / 1000L)} sec"
                                } else {
                                    "No video"
                                },
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 7.dp
                                ),
                                color = ClipkaAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
                        onValueChange = { newValue ->
                            isSeeking = true
                            seekValue = newValue
                        },
                        onValueChangeFinished = {
                            previewingClipId = null
                            if (durationMs > 0L) {
                                exoPlayer.seekTo(
                                    (seekValue * durationMs).toLong()
                                )
                            }
                            isSeeking = false
                        },
                        enabled = videoUri != null && durationMs > 0L
                    )

                                        Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF15161D)
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 10.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "CURRENT POSITION",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = formatTime(
                                        if (isSeeking && durationMs > 0L) {
                                            (seekValue * durationMs).toLong()
                                        } else {
                                            positionMs
                                        }
                                    ),
                                    color = ClipkaAccent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TOTAL DURATION",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = formatTime(durationMs),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }


                
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
                        text = "EXPORT CLIPS",
                        color = ClipkaAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Save your edited videos",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Selected clip: individual MP4 file",
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
                                exportDestinationPicker.launch(
                                    "${clip.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp4"
                                )
                            }
                        },
                        enabled = videoUri != null &&
                            selectedClipId != null &&
                            !isExporting &&
                            !isBatchExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (isExporting) {
                                "Exporting MP4..."
                            } else {
                                "Export Selected Clip (MP4)"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Divider(color = Color.DarkGray)

                    Text(
                        text = "All clips: one ZIP archive",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

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
                                exportStatus = "ZIP export shuru ho raha hai..."

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
                                                exportStatus =
                                                    "ZIP export fail hua. Status check karo."
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
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (isBatchExporting) {
                                "Exporting Clips / ZIP..."
                            } else {
                                "Export All Clips (ZIP)"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (exportStatus.isNotEmpty()) {
                        Text(
                            text = exportStatus,
                            color = if (
                                exportStatus.startsWith("Export complete")
                            ) {
                                ClipkaAccent
                            } else {
                                Color.LightGray
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }


            
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
                        text = "CLIP EDITOR",
                        color = ClipkaAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Create and refine clips",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Undo or redo your latest clip changes.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    val previousClips = undoStack.last()

                                    redoStack = redoStack + listOf(clips)
                                    undoStack = undoStack.dropLast(1)
                                    clips = previousClips

                                    if (clips.none { it.id == selectedClipId }) {
                                        selectedClipId = null
                                        startInput = "00:00"
                                        endInput = "00:30"
                                    }

                                    if (clips.none { it.id == previewingClipId }) {
                                        previewingClipId = null
                                    }

                                    clipError = ""
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("↶  Undo")
                        }

                        OutlinedButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val nextClips = redoStack.last()

                                    undoStack = undoStack + listOf(clips)
                                    redoStack = redoStack.dropLast(1)
                                    clips = nextClips

                                    if (clips.none { it.id == selectedClipId }) {
                                        selectedClipId = null
                                        startInput = "00:00"
                                        endInput = "00:30"
                                    }

                                    if (clips.none { it.id == previewingClipId }) {
                                        previewingClipId = null
                                    }

                                    clipError = ""
                                }
                            },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Redo  ↷")
                        }
                    }
                }
            }


            
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
                        text = "CLIP TRIM",
                        color = ClipkaAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Set clip boundaries",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Time format: MM:SS or HH:MM:SS",
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
                            label = { Text("Start time") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = endInput,
                            onValueChange = { endInput = it },
                            label = { Text("End time") },
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
                                val newSegment = ClipSegment(
                                    name = "Clip ${clips.size + 1}",
                                    startMs = startTime,
                                    endMs = endTime,
                                    aspectRatio = "9:16"
                                )

                                applyClipChange(clips + newSegment)
                                selectedClipId = newSegment.id
                                previewingClipId = newSegment.id
                                exoPlayer.pause()
                                exoPlayer.seekTo(newSegment.startMs)
                            }
                        },
                        enabled = videoUri != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("＋  Add Clip")
                    }

                    OutlinedButton(
                        onClick = {
                            val selectedSegment = clips.firstOrNull {
                                it.id == selectedClipId
                            }

                            val startTime = parseTime(startInput)
                            val endTime = parseTime(endInput)

                            clipError = when {
                                selectedSegment == null ->
                                    "Pehle list se clip select karo."

                                else -> validateClipTimes(
                                    startMs = startTime,
                                    endMs = endTime,
                                    videoDurationMs = durationMs,
                                    videoMissing = videoUri == null
                                )
                            }

                            if (
                                clipError.isEmpty() &&
                                selectedSegment != null &&
                                startTime != null &&
                                endTime != null
                            ) {
                                val updatedSegment = selectedSegment.copy(
                                    startMs = startTime,
                                    endMs = endTime
                                )

                                applyClipChange(
                                    clips.map { segment ->
                                        if (segment.id == updatedSegment.id) {
                                            updatedSegment
                                        } else {
                                            segment
                                        }
                                    }
                                )

                                selectedClipId = updatedSegment.id
                                previewingClipId = updatedSegment.id
                                exoPlayer.pause()
                                exoPlayer.seekTo(updatedSegment.startMs)
                            }
                        },
                        enabled = selectedClipId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Edit")
                    }

                    if (clipError.isNotEmpty()) {
                        Text(
                            text = clipError,
                            color = Color(0xFFFF7777),
                            fontSize = 13.sp
                        )
                    }
                }
            }


            
            Text(
                text = "MY CLIPS  •  ${clips.size}",
                color = ClipkaAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            if (clips.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ClipkaCard
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No clips yet",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Video select karo, start aur end time set karo, phir Add Clip dabao.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }


            clips.forEach { segment ->
                val isSelected = segment.id == selectedClipId
                val isPreviewing = segment.id == previewingClipId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) {
                                ClipkaAccent
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            Color(0xFF29253B)
                        } else {
                            ClipkaCard
                        }
                    )
                ) {
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = segment.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = if (isSelected) {
                                        "SELECTED CLIP"
                                    } else {
                                        "VIDEO CLIP"
                                    },
                                    color = if (isSelected) {
                                        ClipkaAccent
                                    } else {
                                        Color.LightGray
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF353044)
                            ) {
                                Text(
                                    text = formatTime(segment.durationMs),
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 7.dp
                                    ),
                                    color = ClipkaAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF15161D)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "START",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatTime(segment.startMs),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "→",
                                    color = ClipkaAccent,
                                    fontSize = 20.sp
                                )

                                Column {
                                    Text(
                                        text = "END",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatTime(segment.endMs),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Text(
                            text = "OUTPUT ASPECT RATIO",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("9:16", "1:1", "16:9").forEach { ratio ->
                                val ratioSelected = segment.aspectRatio == ratio

                                if (ratioSelected) {
                                    Button(
                                        onClick = {
                                            applyClipChange(
                                                clips.map { item ->
                                                    if (item.id == segment.id) {
                                                        item.copy(aspectRatio = ratio)
                                                    } else {
                                                        item
                                                    }
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(ratio)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            applyClipChange(
                                                clips.map { item ->
                                                    if (item.id == segment.id) {
                                                        item.copy(aspectRatio = ratio)
                                                    } else {
                                                        item
                                                    }
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(ratio)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showClipInPreview(segment, play = false)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Edit")
                            }

                            OutlinedButton(
                                onClick = {
                                    applyClipChange(
                                        clips.filterNot { item ->
                                            item.id == segment.id
                                        }
                                    )

                                    if (selectedClipId == segment.id) {
                                        selectedClipId = null
                                        startInput = "00:00"
                                        endInput = "00:30"
                                    }

                                    if (previewingClipId == segment.id) {
                                        previewingClipId = null
                                        exoPlayer.pause()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Delete")
                            }

                            Button(
                                onClick = {
                                    showClipInPreview(segment, play = true)
                                },
                                enabled = videoUri != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (isPreviewing && isPlaying) {
                                        "Playing"
                                    } else {
                                        "Preview"
                                    }
                                )
                            }
                        }

                        if (isPreviewing) {
                            Text(
                                text = "●  Currently shown in main preview",
                                color = ClipkaAccent,
                                fontSize = 12.sp
                            )
                        }
                    }
                } 
            } 

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
                            text = "Detect faces automatically or customize this clip's frame and layout below.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                val sourceUri = videoUri
                                val clip = frameEditorClip

                                if (sourceUri == null) {
                                    faceDetectionStatus = "Pehle video select karo."
                                } else if (!isDetectingFaces) {
                                    isDetectingFaces = true
                                    faceDetectionStatus = "Faces detect ho rahe hain..."

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
                                                applyClipChange(
                                                    clips.map { item ->
                                                        if (item.id == clip.id) {
                                                            item.copy(
                                                                frames = detectedFrames,
                                                                frameLayout = "SINGLE"
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
                                                "Detection failed: ${error.message ?: "unknown error"}"
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
                        
