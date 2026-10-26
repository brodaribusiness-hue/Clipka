
package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.net.URLDecoder
import kotlinx.coroutines.delay

private val AppBg = Color(0xFF101114)
private val Panel = Color(0xFF1C1E23)
private val Accent = Color(0xFFB9F36B)
private val MainText = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9A9DA5)
private val Selected = Color(0xFF303B25)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBg,
                    surface = Panel,
                    primary = Accent,
                    onPrimary = Color(0xFF171914),
                    onBackground = MainText,
                    onSurface = MainText
                )
            ) {
                ClipkaApp()
            }
        }
    }
}

private enum class Screen {
    HOME, PROJECTS, EDITOR
}

private enum class ToolMode {
    MAIN, EDIT, PREVIEW
}

private data class SplitTemplate(
    val id: Int,
    val title: String,
    val description: String
)

private val splitTemplates = listOf(
    SplitTemplate(1, "Template 1", "Vertical split: speaker 1 top, speaker 2 bottom"),
    SplitTemplate(2, "Template 2", "Two speakers side by side"),
    SplitTemplate(3, "Template 3", "Diagonal split for two speakers"),
    SplitTemplate(4, "Template 4", "Picture-in-picture speaker layout"),
    SplitTemplate(5, "Template 5", "Main speaker with second speaker below"),
    SplitTemplate(6, "Template 6", "Main view with two speaker inset frames")
)

@Composable
private fun ClipkaApp() {
    val context = LocalContext.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Untitled video") }
    var playing by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            videoUri = uri

            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers do not support persistent permissions.
            }

            fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.let { name ->
                    try {
                        URLDecoder.decode(name, "UTF-8")
                    } catch (_: Exception) {
                        name
                    }
                }
                ?.takeIf { it.isNotBlank() }
                ?: "Imported video"

            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            screen = Screen.EDITOR
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBg
    ) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onImport = {
                    picker.launch(arrayOf("video/*"))
                },
                onProjects = {
                    screen = Screen.PROJECTS
                }
            )

            Screen.PROJECTS -> ProjectsScreen(
                onBack = {
                    screen = Screen.HOME
                },
                onImport = {
                    picker.launch(arrayOf("video/*"))
                }
            )

            Screen.EDITOR -> EditorScreen(
                fileName = fileName,
                videoUri = videoUri,
                player = player,
                playing = playing,
                onClose = {
                    player.pause()
                    screen = Screen.HOME
                },
                onPlayPause = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                onUndo = {
                    showMessage(context, "Undo integration is not connected yet")
                },
                onRedo = {
                    showMessage(context, "Redo integration is not connected yet")
                },
                onExport = {
                    showMessage(context, "Export integration is not connected yet")
                }
            )
        }
    }
}

private fun showMessage(
    context: android.content.Context,
    message: String
) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun HomeScreen(
    onImport: () -> Unit,
    onProjects: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CLIPKA",
            color = Accent,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 5.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your video. Your story.",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(44.dp))

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF171914)
            )
        ) {
            Icon(Icons.Default.Movie, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                "Import Video",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onProjects,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MainText
            )
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Projects", fontSize = 17.sp)
        }
    }
}

@Composable
private fun ProjectsScreen(
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Back",
                    tint = MainText
                )
            }

            Text(
                text = "Projects",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = MainText
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text("Your saved projects", color = MainText)

                Spacer(Modifier.height(6.dp))

                Text(
                    "Project loading is not connected yet.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF171914)
            )
        ) {
            Text("Import Video")
        }
    }
}

@Composable
private fun EditorScreen(
    fileName: String,
    videoUri: Uri?,
    player: ExoPlayer,
    playing: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current

    var stretch by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("00:00") }
    var endTime by remember { mutableStateOf("00:30") }
    var toolMode by remember { mutableStateOf(ToolMode.MAIN) }
    var autoFace by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableIntStateOf(1) }

    var clips by remember {
        mutableStateOf<List<ClipSegment>>(emptyList())
    }
    var selectedClipId by remember {
        mutableStateOf<String?>(null)
    }
    var nextClipNumber by remember {
        mutableIntStateOf(1)
    }

    if (toolMode == ToolMode.PREVIEW) {
        PreviewScreen(
            fileName = fileName,
            videoUri = videoUri,
            player = player,
            playing = playing,
            selectedClip = clips.firstOrNull {
                it.id == selectedClipId
            },
            onBack = {
                toolMode = ToolMode.MAIN
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close editor",
                    tint = MainText
                )
            }

            Text(
                text = fileName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
                color = MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = onExport,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 5.dp
                ),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Text("Export", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(5.dp))

        // Main video preview stays in place in every tool mode.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode = if (stretch) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.resizeMode = if (stretch) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        // Playback controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = { stretch = !stretch },
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (stretch) Icons.Default.FullscreenExit
                    else Icons.Default.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(3.dp))

                Text(
                    if (stretch) "Fit" else "Stretch",
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(43.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    if (playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onUndo,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = MainText
                )
            }

            IconButton(
                onClick = onRedo,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = MainText
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Start and end time fields
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeField(
                label = "START",
                value = startTime,
                onValueChange = { startTime = it },
                modifier = Modifier.weight(1f)
            )

            TimeField(
                label = "END",
                value = endTime,
                onValueChange = { endTime = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(5.dp))

        // Real clip timeline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TIMELINE · ${clips.size} CLIPS",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "$startTime — $endTime",
                    color = Accent,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (clips.isEmpty()) {
                    Text(
                        "No clips yet · Preview → Add Clip",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                clips.forEach { clip ->
                    Column(
                        modifier = Modifier
                            .width(105.dp)
                            .background(
                                if (clip.id == selectedClipId) {
                                    Selected
                                } else {
                                    Color(0xFF303238)
                                },
                                RoundedCornerShape(7.dp)
                            )
                            .clickable {
                                selectedClipId = clip.id
                                startTime = formatTime(clip.startMs)
                                endTime = formatTime(clip.endMs)
                            }
                            .padding(7.dp)
                    ) {
                        Text(
                            clip.name,
                            color = MainText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            "${formatTime(clip.startMs)}–${formatTime(clip.endMs)}",
                            color = Muted,
                            fontSize = 9.sp,
                            maxLines = 1
                        )

                        Text(
                            "${clip.durationMs / 1000L}s",
                            color = Accent,
                            fontSize = 10.sp
                        )

                        TextButton(
                            onClick = {
                                clips = clips.filterNot {
                                    it.id == clip.id
                                }

                                if (selectedClipId == clip.id) {
                                    selectedClipId = null
                                    startTime = "00:00"
                                    endTime = "00:30"
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(25.dp)
                        ) {
                            Text(
                                "Delete",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        // Bottom tools: one toolbar, horizontally scrollable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (toolMode) {
                ToolMode.MAIN -> {
                    ToolButton(
                        label = "Edit",
                        icon = Icons.Default.Tune,
                        onClick = {
                            toolMode = ToolMode.EDIT
                        }
                    )

                    ToolButton(
                        label = "Trim",
                        icon = Icons.Default.ContentCut,
                        onClick = {
                            val parsedStartMs = parseTimeToMs(startTime)
                            val parsedEndMs = parseTimeToMs(endTime)
                            val selected = clips.firstOrNull {
                                it.id == selectedClipId
                            }

                            if (selected == null) {
                                showMessage(
                                    context,
                                    "Select a clip from the timeline first"
                                )
                            } else if (
                                parsedStartMs == null ||
                                parsedEndMs == null
                            ) {
                                showMessage(
                                    context,
                                    "Enter valid start and end times"
                                )
                            } else {
                                val validStartMs: Long = parsedStartMs
                                val validEndMs: Long = parsedEndMs

                                when {
                                    validEndMs <= validStartMs -> {
                                        showMessage(
                                            context,
                                            "End time must be after start time"
                                        )
                                    }

                                    player.duration > 0L &&
                                        validEndMs > player.duration -> {
                                        showMessage(
                                            context,
                                            "End time exceeds video duration"
                                        )
                                    }

                                    else -> {
                                        val updated = selected.copy(
                                            startMs = validStartMs,
                                            endMs = validEndMs
                                        )

                                        when (val result = updated.validate()) {
                                            is ClipValidationResult.Invalid -> {
                                                showMessage(
                                                    context,
                                                    result.reason
                                                )
                                            }

                                            ClipValidationResult.Valid -> {
                                                clips = clips.map { clip ->
                                                    if (clip.id == updated.id) {
                                                        updated
                                                    } else {
                                                        clip
                                                    }
                                                }
                                                startTime = formatTime(updated.startMs)
                                                endTime = formatTime(updated.endMs)
                                                showMessage(
                                                    context,
                                                    "Clip trimmed successfully"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )

                    ToolButton(
                        label = "Auto Clip",
                        icon = Icons.Default.AutoAwesome,
                        onClick = {
                            showMessage(
                                context,
                                "Automatic clip detection is not connected yet"
                            )
                        }
                    )

                    ToolButton(
                        label = if (autoFace) "Face ON" else "Face Detect",
                        icon = Icons.Default.Face,
                        active = autoFace,
                        onClick = {
                            autoFace = !autoFace
                            showMessage(
                                context,
                                if (autoFace) {
                                    "Face detection UI enabled; tracking is not connected"
                                } else {
                                    "Face detection UI disabled"
                                }
                            )
                        }
                    )

                    ToolButton(
                        label = "Preview",
                        icon = Icons.Default.ViewCarousel,
                        onClick = {
                            toolMode = ToolMode.PREVIEW
                        }
                    )
                }

                ToolMode.EDIT -> {
                    ToolButton(
                        label = "Back",
                        icon = Icons.Default.ArrowBack,
                        onClick = {
                            toolMode = ToolMode.MAIN
                        }
                    )

                    ToolButton(
                        label = "Crop",
                        icon = Icons.Default.Crop,
                        onClick = {
                            showMessage(
                                context,
                                "Crop editing is not connected yet"
                            )
                        }
                    )

                    ToolButton(
                        label = "Resize",
                        icon = Icons.Default.AspectRatio,
                        onClick = {
                            showMessage(
                                context,
                                "Resize editing is not connected yet"
                            )
                        }
                    )

                    ToolButton(
                        label = "Zoom",
                        icon = Icons.Default.ZoomIn,
                        onClick = {
                            showMessage(
                                context,
                                "Zoom editing is not connected yet"
                            )
                        }
                    )

                    ToolButton(
                        label = "Adjust",
                        icon = Icons.Default.Tune,
                        onClick = {
                            showMessage(
                                context,
                                "Adjust controls are not connected yet"
                            )
                        }
                    )

                    splitTemplates.forEach { template ->
                        ToolButton(
                            label = template.title,
                            icon = Icons.Default.ViewQuilt,
                            active = selectedTemplate == template.id,
                            onClick = {
                                selectedTemplate = template.id
                                showMessage(
                                    context,
                                    "${template.title}: ${template.description}. " +
                                        "Rendering integration is not connected yet."
                                )
                            }
                        )
                    }

                    ToolButton(
                        label = "Ratio",
                        icon = Icons.Default.CropSquare,
                        onClick = {
                            showMessage(
                                context,
                                "Aspect ratio options: 9:16, 16:9, 1:1. " +
                                    "Ratio editing is not connected yet."
                            )
                        }
                    )

                    ToolButton(
                        label = "Manual Frame",
                        icon = Icons.Default.OpenWith,
                        onClick = {
                            showMessage(
                                context,
                                "Manual framing is not connected to this editor yet"
                            )
                        }
                    )

                    ToolButton(
                        label = "Auto Frame",
                        icon = Icons.Default.Face,
                        onClick = {
                            showMessage(
                                context,
                                "Automatic framing is not connected yet"
                            )
                        }
                    )

                    ToolButton(
                        label = "Add Clip",
                        icon = Icons.Default.AddCircleOutline,
                        onClick = {
                            val parsedStartMs = parseTimeToMs(startTime)
                            val parsedEndMs = parseTimeToMs(endTime)

                            if (videoUri == null) {
                                showMessage(context, "Import a video first")
                            } else if (
                                parsedStartMs == null ||
                                parsedEndMs == null
                            ) {
                                showMessage(
                                    context,
                                    "Enter valid start and end times"
                                )
                            } else {
                                val validStartMs: Long = parsedStartMs
                                val validEndMs: Long = parsedEndMs

                                when {
                                    validStartMs < 0L -> {
                                        showMessage(
                                            context,
                                            "Start time cannot be negative"
                                        )
                                    }

                                    validEndMs <= validStartMs -> {
                                        showMessage(
                                            context,
                                            "End time must be after start time"
                                        )
                                    }

                                    validEndMs - validStartMs >
                                        ClipSegment.MAX_CLIP_DURATION_MS -> {
                                        showMessage(
                                            context,
                                            "A clip cannot be longer than 3 minutes"
                                        )
                                    }

                                    player.duration > 0L &&
                                        validEndMs > player.duration -> {
                                        showMessage(
                                            context,
                                            "End time exceeds video duration"
                                        )
                                    }

                                    else -> {
                                        val newClip = ClipSegment(
                                            name = "Clip $nextClipNumber",
                                            startMs = validStartMs,
                                            endMs = validEndMs
                                        )

                                        when (
                                            val validation = newClip.validate()
                                        ) {
                                            is ClipValidationResult.Invalid -> {
                                                showMessage(
                                                    context,
                                                    validation.reason
                                                )
                                            }

                                            ClipValidationResult.Valid -> {
                                                clips = clips + newClip
                                                selectedClipId = newClip.id
                                                nextClipNumber += 1
                                                showMessage(
                                                    context,
                                                    "${newClip.name} added"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                ToolMode.PREVIEW -> {
                    // PreviewScreen is displayed before the editor layout.
                }
            }
        }
    }
}

@Composable
private fun PreviewScreen(
    fileName: String,
    videoUri: Uri?,
    player: ExoPlayer,
    playing: Boolean,
    selectedClip: ClipSegment?,
    onBack: () -> Unit
) {
    var positionMs by remember { mutableStateOf(0L) }

    val sourceDuration = player.duration.coerceAtLeast(1L)
    val rangeStart = (selectedClip?.startMs ?: 0L).coerceAtLeast(0L)
    val rangeEnd = (selectedClip?.endMs ?: sourceDuration)
        .coerceAtLeast(rangeStart + 1L)
    val sliderMax = rangeEnd.toFloat()
    val sliderMin = rangeStart.toFloat()

    LaunchedEffect(player, selectedClip?.id) {
        player.pause()
        player.seekTo(rangeStart)
        positionMs = rangeStart
    }

    LaunchedEffect(player, playing, selectedClip?.id) {
        while (true) {
            val current = player.currentPosition.coerceAtLeast(0L)

            if (selectedClip != null && playing && current >= rangeEnd) {
                player.pause()
                player.seekTo(rangeStart)
                positionMs = rangeStart
            } else {
                positionMs = current
            }

            delay(100L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    player.pause()
                    onBack()
                }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back to editor",
                    tint = MainText
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Preview",
                    color = MainText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = selectedClip?.name ?: fileName,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                )
            } else {
                Text("Import a video first", color = Muted)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(positionMs),
                color = MainText,
                fontSize = 12.sp
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = formatTime(
                    selectedClip?.endMs ?: player.duration.coerceAtLeast(0L)
                ),
                color = Muted,
                fontSize = 12.sp
            )
        }

        Slider(
            value = positionMs.coerceIn(
                rangeStart,
                rangeEnd
            ).toFloat(),
            onValueChange = { value ->
                positionMs = value.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(positionMs.coerceIn(rangeStart, rangeEnd))
            },
            valueRange = sliderMin..sliderMax,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF44464D)
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        val current = player.currentPosition
                        if (
                            selectedClip != null &&
                            (current < rangeStart || current >= rangeEnd)
                        ) {
                            player.seekTo(rangeStart)
                        }
                        player.play()
                    }
                },
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    if (playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Text(
            text = if (selectedClip != null) {
                "Clip range: ${formatTime(selectedClip.startMs)} – " +
                    formatTime(selectedClip.endMs)
            } else {
                "Full imported video"
            },
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )
    }
}

private fun parseTimeToMs(value: String): Long? {
    val parts = value.trim().split(":")

    if (parts.size !in 2..3 || parts.any { it.isBlank() }) {
        return null
    }

    val numbers = parts.map {
        it.toLongOrNull() ?: return null
    }

    if (numbers.any { it < 0L }) return null
    if (numbers.last() >= 60L) return null

    // In MM:SS format, minutes may exceed 59.
    if (numbers.size == 3 && numbers[1] >= 60L) return null

    val totalSeconds = when (numbers.size) {
        2 -> numbers[0] * 60L + numbers[1]
        3 -> numbers[0] * 3600L + numbers[1] * 60L + numbers[2]
        else -> return null
    }

    return totalSeconds * 1000L
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(
                input.filter {
                    it.isDigit() || it == ':'
                }.take(8)
            )
        },
        modifier = modifier.height(52.dp),
        label = {
            Text(label, fontSize = 9.sp)
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color(0xFF44464D),
            focusedLabelColor = Accent,
            unfocusedLabelColor = Muted,
            focusedTextColor = MainText,
            unfocusedTextColor = MainText
        )
    )
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .fillMaxHeight()
            .background(
                if (active) Selected else Panel,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Accent else MainText,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            color = if (active) Accent else MainText,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
