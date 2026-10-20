
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
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    var isExporting by remember { mutableStateOf(false) }
    var pendingExportClip by remember { mutableStateOf<ClipSegment?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }

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
                // Step 1: Clip ko selected start/end time se trim karo.
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipToExport.startMs)
                            .setEndPositionMs(clipToExport.endMs)
                            .build()
                    )
                    .build()

                // Step 2: Is clip ka apna target aspect ratio set karo.
                val targetAspectRatio = when (clipToExport.aspectRatio) {
                    "9:16" -> 9f / 16f
                    "1:1" -> 1f
                    "16:9" -> 16f / 9f
                    else -> 9f / 16f
                }

                // Crop the video to fill the selected aspect ratio.
                val presentationEffect = Presentation.createForAspectRatio(
                    targetAspectRatio,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )

                // Step 3: Video effect ko export pipeline mein apply karo.
                val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem)
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(presentationEffect)
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

                // Export the edited item, not the original trimmed item.
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
            Text(
                text = "clipka",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ClipkaAccent
            )

            Text(
                text = "AI Video Clip Studio",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Text(
                text = if (previewClip != null) {
                    "Clip Preview • ${previewClip.aspectRatio}"
                } else {
                    "Original Video Preview"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

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

            Button(
                onClick = {
                    videoPicker.launch(arrayOf("video/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null
                )
                Spacer(Modifier.size(8.dp))
                Text("Choose video")
            }

            Text(
                text = videoName,
                color = Color.LightGray,
                fontSize = 13.sp
            )

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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (isPlaying) "Pause" else "Play")
                }

                Button(
                    onClick = {
                        previewingClipId = null
                        exoPlayer.seekTo(0L)
                        exoPlayer.pause()
                    },
                    enabled = videoUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Restart Original")
                }
            }

            Text(
                text = "Video Timeline",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Slider(
                value = if (isSeeking) {
                    seekValue
                } else if (durationMs > 0L) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(positionMs), color = Color.LightGray)
                Text(formatTime(durationMs), color = Color.LightGray)
            }

            Button(
                onClick = {
                    val clip = clips.firstOrNull {
                        it.id == selectedClipId
                    }

                    if (clip != null && videoUri != null && !isExporting) {
                        pendingExportClip = clip
                        exportDestinationPicker.launch(
                            "${clip.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp4"
                        )
                    }
                },
                enabled = videoUri != null &&
                    selectedClipId != null &&
                    !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isExporting) {
                        "Exporting MP4..."
                    } else {
                        "Export Selected Clip (MP4)"
                    }
                )
            }

            if (exportStatus.isNotEmpty()) {
                Text(
                    text = exportStatus,
                    color = if (exportStatus.startsWith("Export complete")) {
                        ClipkaAccent
                    } else {
                        Color.LightGray
                    },
                    fontSize = 13.sp
                )
            }

            Text(
                text = "Clip Editing",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Undo")
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Redo")
                }
            }

            Text(
                text = "Time format: MM:SS or HH:MM:SS",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = startInput,
                onValueChange = { startInput = it },
                label = { Text("Start time") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = endInput,
                onValueChange = { endInput = it },
                label = { Text("End time") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Clip")
            }

            Button(
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Edit")
            }

            if (clipError.isNotEmpty()) {
                Text(
                    text = clipError,
                    color = Color(0xFFFF7777)
                )
            }

            Text(
                text = "My Clips (${clips.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (clips.isEmpty()) {
                Text(
                    text = "Abhi koi clip add nahi hui.",
                    color = Color.LightGray
                )
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
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = segment.name +
                                if (isSelected) " • Selected" else "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${formatTime(segment.startMs)} - " +
                                formatTime(segment.endMs),
                            color = Color.LightGray
                        )

                        Text(
                            text = "Duration: ${formatTime(segment.durationMs)}",
                            color = ClipkaAccent
                        )

                        Text(
                            text = "This clip's aspect ratio",
                            color = Color.White,
                            fontSize = 13.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("9:16", "1:1", "16:9").forEach { ratio ->
                                if (segment.aspectRatio == ratio) {
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
                                        modifier = Modifier.weight(1f)
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
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(ratio)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showClipInPreview(segment, play = false)
                                },
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete")
                            }

                            Button(
                                onClick = {
                                    showClipInPreview(segment, play = true)
                                },
                                enabled = videoUri != null,
                                modifier = Modifier.weight(1f)
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
                                text = "Currently shown in main preview",
                                color = ClipkaAccent,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = "Each clip exports with its own selected aspect ratio.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
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
