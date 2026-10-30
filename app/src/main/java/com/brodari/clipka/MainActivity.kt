
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
           
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
    MAIN, EDIT, CROP, OUTPUT, PREVIEW
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


private fun templateLayout(id: Int): String {
    return when (id) {
        1 -> "SPLIT_VERTICAL"
        2 -> "SPLIT_HORIZONTAL"
        3 -> "SPLIT_DIAGONAL"
        4 -> "CUSTOM"
        5 -> "SPLIT_60_40"
        6 -> "CUSTOM"
        else -> "SINGLE"
    }
}


private fun templateFrames(id: Int): List<FrameRegion> {
    fun frame(
        name: String,
        outputX: Float,
        outputY: Float,
        outputWidth: Float,
        outputHeight: Float
    ) = FrameRegion(
        name = name,
        x = 0.20f,
        y = 0.10f,
        width = 0.60f,
        height = 0.80f,
        outputX = outputX,
        outputY = outputY,
        outputWidth = outputWidth,
        outputHeight = outputHeight
    )

    return when (id) {
        1 -> listOf(
            frame("Speaker 1", 0f, 0f, 1f, 0.5f),
            frame("Speaker 2", 0f, 0.5f, 1f, 0.5f)
        )
        2 -> listOf(
            frame("Speaker 1", 0f, 0f, 0.5f, 1f),
            frame("Speaker 2", 0.5f, 0f, 0.5f, 1f)
        )
        3 -> listOf(
            frame("Speaker 1", 0f, 0f, 0.62f, 1f),
            frame("Speaker 2", 0.38f, 0f, 0.62f, 1f)
        )
        4 -> listOf(
            frame("Main speaker", 0f, 0f, 1f, 1f),
            frame("Inset speaker", 0.62f, 0.06f, 0.32f, 0.28f)
        )
        5 -> listOf(
            frame("Main speaker", 0f, 0f, 1f, 0.6f),
            frame("Second speaker", 0f, 0.6f, 1f, 0.4f)
        )
        6 -> listOf(
            frame("Main view", 0f, 0f, 1f, 1f),
            frame("Inset 1", 0.04f, 0.06f, 0.28f, 0.25f),
            frame("Inset 2", 0.68f, 0.06f, 0.28f, 0.25f)
        )
        else -> emptyList()
    }
}

private fun applyTemplateToFrames(
    templateId: Int,
    existingFrames: List<FrameRegion>
): List<FrameRegion> {
    val placements = templateFrames(templateId)

    return placements.mapIndexed { index, placement ->
        val existing = existingFrames.getOrNull(index)

        if (existing == null) {
            placement
        } else {
            existing.copy(
                name = placement.name,
                outputX = placement.outputX,
                outputY = placement.outputY,
                outputWidth = placement.outputWidth,
                outputHeight = placement.outputHeight
            )
        }
    }
}




private data class EditorSnapshot(
    val clips: List<ClipSegment>,
    val selectedClipId: String?,
    val selectedFrameId: String?,
        val nextClipNumber: Int,
    val startTime: String,
    val endTime: String,
    val selectedTemplate: Int
)

@Composable
private fun ClipkaApp() {
    val context = LocalContext.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Untitled video") }
    var playing by remember { mutableStateOf(false) }
        var currentProject by remember {
        mutableStateOf<ClipProject?>(null)
    }
    var savedProjects by remember {
        mutableStateOf<List<ClipProject>>(emptyList())
    }
        var pendingZipFile by remember {
        mutableStateOf<File?>(null)
    }

    val saveZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destinationUri ->
        val zipFile = pendingZipFile
        pendingZipFile = null

        if (destinationUri == null) {
            zipFile?.delete()
            showMessage(context, "Export cancelled")
        } else if (zipFile == null || !zipFile.exists()) {
            showMessage(context, "Export file is missing")
        } else {
            runCatching {
                val output = context.contentResolver
                    .openOutputStream(destinationUri)
                    ?: error("Could not open destination")

                output.use { stream ->
                    zipFile.inputStream().use { input ->
                        input.copyTo(stream)
                    }
                }
            }.onSuccess {
                showMessage(context, "ZIP exported successfully")
            }.onFailure { error ->
                showMessage(
                    context,
                    "Save failed: ${error.localizedMessage ?: "Unknown error"}"
                )
            }

            zipFile.delete()
        }
    }

    LaunchedEffect(Unit) {
        savedProjects = ClipProjectStorage.loadAll(context)
            .getOrElse { emptyList() }
    }

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

                        currentProject = ClipProject(
                name = fileName,
                sourceVideoUri = uri.toString()
            )

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
                projects = savedProjects,
                onBack = {
                    screen = Screen.HOME
                },
                onImport = {
                    picker.launch(arrayOf("video/*"))
                },
                onOpenProject = { project ->
                    val source = project.sourceVideoUri

                    if (source.isNullOrBlank()) {
                        showMessage(context, "Project video URI is missing")
                    } else {
                        try {
                            val uri = Uri.parse(source)

                            videoUri = uri
                            fileName = project.name
                            currentProject = project

                            player.setMediaItem(MediaItem.fromUri(uri))
                            player.prepare()
                            player.playWhenReady = false

                            screen = Screen.EDITOR
                        } catch (_: Exception) {
                            showMessage(context, "Could not open this project")
                        }
                    }
                }
            )
            Screen.EDITOR -> EditorScreen(
                fileName = fileName,
                                project = currentProject,
                onSaveProject = { projectToSave ->
                    ClipProjectStorage.save(context, projectToSave)
                        .onSuccess {
                            savedProjects = ClipProjectStorage.loadAll(context)
                                .getOrElse { emptyList() }
                            currentProject = projectToSave
                            showMessage(context, "Project saved")
                        }
                        .onFailure {
                            showMessage(context, "Project save failed")
                        }
                },
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
                                onExport = { clipsToExport ->
                    val sourceUri = videoUri

                    if (sourceUri == null) {
                        showMessage(context, "Import a video first")
                    } else if (clipsToExport.isEmpty()) {
                        showMessage(context, "Add clips before exporting")
                    } else {
                        BatchZipExporter(
                            context = context,
                            sourceUri = sourceUri,
                            clips = clipsToExport,
                            onProgress = { message ->
                                showMessage(context, message)
                            },
                            onComplete = { zipFile ->
                                if (zipFile == null) {
                                    showMessage(
                                        context,
                                        "Export failed. Check clip settings."
                                    )
                                } else {
                                    pendingZipFile = zipFile
                                    saveZipPicker.launch(
                                        "Clipka_Clips.zip"
                                    )
                                }
                            }
                        ).start()
                    }
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
    projects: List<ClipProject>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenProject: (ClipProject) -> Unit
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

        if (projects.isEmpty()) {
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

                    Text("No saved projects yet", color = MainText)

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Open a video and tap Save Project.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                projects.forEach { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(12.dp))
                            .clickable { onOpenProject(project) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                color = MainText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "${project.clips.size} clips",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Open project",
                            tint = Muted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

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
        project: ClipProject?,
    onSaveProject: (ClipProject) -> Unit,
    videoUri: Uri?,
    player: ExoPlayer,
    playing: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
        onExport: (List<ClipSegment>) -> Unit
) {
    val context = LocalContext.current
           val coroutineScope = rememberCoroutineScope()

        var stretch by remember { mutableStateOf(false) }

    var startTime by remember(project?.id) {
        mutableStateOf(
            project?.clips?.firstOrNull()?.let {
                formatTime(it.startMs)
            } ?: "00:00"
        )
    }

    var endTime by remember(project?.id) {
        mutableStateOf(
            project?.clips?.firstOrNull()?.let {
                formatTime(it.endMs)
            } ?: "00:30"
        )
    }

    var toolMode by remember { mutableStateOf(ToolMode.MAIN) }
    var autoFace by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableIntStateOf(1) }
    
    var sourcePositionMs by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(player, playing, videoUri) {
        while (true) {
            sourcePositionMs =
                player.currentPosition.coerceAtLeast(0L)
            delay(100L)
        }
    }
    var clips by remember(project?.id) {
        mutableStateOf(project?.clips ?: emptyList())
    }

        var selectedClipId by remember(project?.id) {
        mutableStateOf(
            project?.selectedClipId
                ?.takeIf { id ->
                    project?.clips?.any { it.id == id } == true
                }
                ?: project?.clips?.firstOrNull()?.id
        )
    }
            var selectedFrameId by remember(project?.id) {
        mutableStateOf(
            project?.clips
                ?.firstOrNull { it.id == project?.selectedClipId }
                ?.frames
                ?.firstOrNull()
                ?.id
        )
    }

    var nextClipNumber by remember(project?.id) {
        mutableIntStateOf((project?.clips?.size ?: 0) + 1)
    }
    

    
    val undoStack = remember(project?.id) {
        mutableStateListOf<EditorSnapshot>()
    }

    val redoStack = remember(project?.id) {
        mutableStateListOf<EditorSnapshot>()
    }

    fun currentSnapshot() = EditorSnapshot(
        clips = clips.toList(),
        selectedClipId = selectedClipId,
        selectedFrameId = selectedFrameId,
                nextClipNumber = nextClipNumber,
        startTime = startTime,
                startTime = startTime,
        endTime = endTime,
        selectedTemplate = selectedTemplate
    )

    fun saveUndoState() {
        undoStack.add(currentSnapshot())

        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }

        redoStack.clear()
    }

    fun undoEdit() {
        if (undoStack.isEmpty()) {
            showMessage(context, "Nothing to undo")
            return
        }

        redoStack.add(currentSnapshot())

        val previous = undoStack.removeAt(
            undoStack.lastIndex
        )

        clips = previous.clips
        selectedClipId = previous.selectedClipId
        selectedFrameId = previous.selectedFrameId
                nextClipNumber = previous.nextClipNumber
        startTime = previous.startTime
        endTime = previous.endTime
        selectedTemplate = previous.selectedTemplate
    }    

    fun redoEdit() {
        if (redoStack.isEmpty()) {
            showMessage(context, "Nothing to redo")
            return
        }

        undoStack.add(currentSnapshot())

        val next = redoStack.removeAt(
            redoStack.lastIndex
        )

        clips = next.clips
        selectedClipId = next.selectedClipId
        selectedFrameId = next.selectedFrameId
                nextClipNumber = next.nextClipNumber
        startTime = next.startTime
        endTime = next.endTime
        selectedTemplate = next.selectedTemplate
    }

    
    fun addClipFromRange(): Boolean {
        if (videoUri == null) {
            showMessage(context, "Import a video first")
            return false
        }

        val startMs = parseTimeToMs(startTime)
        val endMs = parseTimeToMs(endTime)
        val durationMs = player.duration

        if (startMs == null || endMs == null) {
            showMessage(context, "Enter valid start and end times")
            return false
        }

        if (startMs < 0L || endMs <= startMs) {
            showMessage(context, "End time must be after start time")
            return false
        }

        if (durationMs <= 0L) {
            showMessage(context, "Wait for the video to load")
            return false
        }

        if (endMs > durationMs) {
            showMessage(context, "End time exceeds video duration")
            return false
        }

        val newClip = ClipSegment(
            name = "Clip $nextClipNumber",
            startMs = startMs,
            endMs = endMs
        )

        when (val result = newClip.validate()) {
            is ClipValidationResult.Invalid -> {
                showMessage(context, result.reason)
                return false
            }

            ClipValidationResult.Valid -> {
                saveUndoState()
                clips = clips + newClip
                selectedClipId = newClip.id
                nextClipNumber += 1
                player.seekTo(startMs)
                showMessage(context, "${newClip.name} added")
                return true
            }
        }
    }
    
    
    fun generateAutoClips() {
        val durationMs = player.duration
        val sourceUri = videoUri

        if (sourceUri == null || durationMs <= 0L) {
            showMessage(context, "Wait for the video to load first")
            return
        }

        coroutineScope.launch {
            showMessage(
                context,
                "Analyzing video scenes on this device..."
            )

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AutoClipAnalyzer.detectSegments(
                        context.applicationContext,
                        sourceUri,
                        durationMs
                    )
                }
            }

            result.onSuccess { ranges ->
                if (ranges.isEmpty()) {
                    showMessage(context, "No clips could be generated")
                    return@onSuccess
                }

                val generatedClips = ranges.mapIndexed {
                        index, range ->
                    ClipSegment(
                        name = "Clip ${nextClipNumber + index}",
                        startMs = range.first,
                        endMs = range.second
                    )
                }

                val invalidClip = generatedClips.firstOrNull {
                    it.validate() is ClipValidationResult.Invalid
                }

                if (invalidClip != null) {
                    showMessage(
                        context,
                        "A generated clip failed validation"
                    )
                    return@onSuccess
                }

                saveUndoState()
                clips = clips + generatedClips
                selectedClipId = generatedClips.first().id
                nextClipNumber += generatedClips.size
                startTime = formatTime(generatedClips.first().startMs)
                endTime = formatTime(generatedClips.first().endMs)
                player.seekTo(generatedClips.first().startMs)

                showMessage(
                    context,
                    "${generatedClips.size} scene-based clips generated"
                )
            }.onFailure { error ->
                showMessage(
                    context,
                    "Scene analysis failed: ${
                        error.localizedMessage ?: "Unable to read video"
                    }"
                )
            }
        }
    }

    
    fun detectFacesForSelectedClip() {
        val sourceUri = videoUri

        val selected = clips.firstOrNull {
            it.id == selectedClipId
        }

        if (sourceUri == null) {
            showMessage(context, "Import a video first")
            return
        }

        if (selected == null) {
            showMessage(context, "Select or add a clip first")
            return
        }

        coroutineScope.launch {
            showMessage(
                context,
                "Analyzing sampled video frames..."
            )

            val result = runCatching {
                ClipkaFaceDetection.detectFaces(
                    context.applicationContext,
                    sourceUri,
                    selected.startMs,
                    selected.endMs,
                    maxFaces = 6
                )
            }

            result.onSuccess { detectedFrames ->
                if (detectedFrames.isEmpty()) {
                    autoFace = false
                    showMessage(context, "No faces detected")
                    return@onSuccess
                }

                val detectedCount = detectedFrames.size
                val rows = (detectedCount + 1) / 2

                val placedFrames = detectedFrames.mapIndexed {
                    index,
                    frame ->

                    val row = index / 2
                    val column = index % 2

                    val isLastSingleFrame =
                        detectedCount % 2 == 1 &&
                            index == detectedCount - 1

                    val outputX = if (
                        isLastSingleFrame
                    ) {
                        0f
                    } else {
                        column * 0.5f
                    }

                    val outputWidth = if (
                        isLastSingleFrame || detectedCount == 1
                    ) {
                        1f
                    } else {
                        0.5f
                    }

                    val outputHeight = if (
                        detectedCount == 1
                    ) {
                        1f
                    } else {
                        1f / rows.toFloat()
                    }

                    frame.copy(
                        name = "Face ${index + 1}",
                        outputX = outputX,
                        outputY = row * outputHeight,
                        outputWidth = outputWidth,
                        outputHeight = outputHeight
                    )
                }

                saveUndoState()

                clips = clips.map { clip ->
                    if (clip.id == selected.id) {
                        clip.copy(
                            frames = placedFrames,
                            frameLayout = if (
                                placedFrames.size > 1
                            ) {
                                "CUSTOM"
                            } else {
                                "SINGLE"
                            }
                        )
                    } else {
                        clip
                    }
                }

                selectedFrameId = placedFrames.first().id
                autoFace = true

                showMessage(
                    context,
                    "$detectedCount face regions added"
                )
            }.onFailure { error ->
                autoFace = false

                showMessage(
                    context,
                    "Face detection failed: ${
                        error.localizedMessage
                            ?: "Unable to analyze this video"
                    }"
                )
            }
        }
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
            selectedTemplate = selectedTemplate,
            onSelectTemplate = { template ->
                if (template.id == 3) {
                    showMessage(
                        context,
                        "Diagonal masking is not supported yet"
                    )
                } else {
                    val currentId = selectedClipId

                    if (currentId == null) {
                        showMessage(
                            context,
                            "Select or add a clip first"
                        )
                    } else {
                        saveUndoState()

                        clips = clips.map { clip ->
                            if (clip.id == currentId) {
                                clip.copy(
                                    frameLayout = templateLayout(
                                        template.id
                                    ),
                                    frames = applyTemplateToFrames(
                                        templateId = template.id,
                                        existingFrames = clip.frames
                                    )
                                )
                            } else {
                                clip
                            }
                        }

                        selectedTemplate = template.id

                        showMessage(
                            context,
                            "${template.title} applied"
                        )
                    }
                }
            },
            onSelectRatio = { ratio ->
                val currentId = selectedClipId

                if (currentId == null) {
                    showMessage(
                        context,
                        "Select or add a clip first"
                    )
                } else {
                    saveUndoState()

                    clips = clips.map { clip ->
                        if (clip.id == currentId) {
                            clip.copy(aspectRatio = ratio)
                        } else {
                            clip
                        }
                    }

                    showMessage(
                        context,
                        "Aspect ratio set to $ratio"
                    )
                }
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
                        IconButton(
                onClick = {
                    val projectToSave = ClipProject(
                        id = project?.id ?: ClipProject().id,
                        name = project?.name ?: fileName,
                        sourceVideoUri = videoUri?.toString(),
                        sourceDurationMs = player.duration.coerceAtLeast(0L),
                        clips = clips,
                        selectedClipId = selectedClipId
                    )

                    onSaveProject(projectToSave)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = "Save Project",
                    tint = Accent
                )
            }

            Button(
                onClick = {
    onExport(clips)
},
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
                        val cropFrames = clips
    .firstOrNull { it.id == selectedClipId }
    ?.frames
    ?: emptyList()

val activeFrame = cropFrames.firstOrNull {
    it.id == selectedFrameId
} ?: cropFrames.firstOrNull()

// Keep the gesture alive while frame coordinates change.
val latestClips by rememberUpdatedState(clips)
val latestActiveFrame by rememberUpdatedState(activeFrame)

if (toolMode == ToolMode.CROP && activeFrame != null) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                toolMode,
                selectedClipId,
                selectedFrameId
            ) {
                var dragX = 0f
                var dragY = 0f
                var initialFrame: FrameRegion? = null
                var resizeMode = false

                detectDragGestures(
                    onDragStart = { offset ->
                        val frame = latestActiveFrame
                            ?: return@detectDragGestures

                        initialFrame = frame
                        dragX = 0f
                        dragY = 0f

                        val cornerX =
                            (frame.x + frame.width) * size.width

                        val cornerY =
                            (frame.y + frame.height) * size.height

                        val dx = offset.x - cornerX
                        val dy = offset.y - cornerY
                        val handleRadius = 44.dp.toPx()

                        resizeMode =
                            dx * dx + dy * dy <=
                                handleRadius * handleRadius

                        saveUndoState()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        val startFrame = initialFrame
                            ?: return@detectDragGestures

                        if (size.width <= 0 || size.height <= 0) {
                            return@detectDragGestures
                        }

                        dragX += dragAmount.x / size.width.toFloat()
                        dragY += dragAmount.y / size.height.toFloat()

                        clips = latestClips.map { clip ->
                            if (clip.id != selectedClipId) {
                                clip
                            } else {
                                clip.copy(
                                    frames = clip.frames.map { frame ->
                                        if (frame.id != startFrame.id) {
                                            frame
                                        } else if (resizeMode) {
                                            frame.copy(
                                                width = (
                                                    startFrame.width + dragX
                                                ).coerceIn(
                                                    0.12f,
                                                    (1f - startFrame.x)
                                                        .coerceAtLeast(0.12f)
                                                ),
                                                height = (
                                                    startFrame.height + dragY
                                                ).coerceIn(
                                                    0.12f,
                                                    (1f - startFrame.y)
                                                        .coerceAtLeast(0.12f)
                                                )
                                            )
                                        } else {
                                            frame.copy(
                                                x = (
                                                    startFrame.x + dragX
                                                ).coerceIn(
                                                    0f,
                                                    1f - startFrame.width
                                                ),
                                                y = (
                                                    startFrame.y + dragY
                                                ).coerceIn(
                                                    0f,
                                                    1f - startFrame.height
                                                )
                                            )
                                        }
                                    },
                                    frameLayout = "CUSTOM"
                                )
                            }
                        }
                    }
                )
            }
    ) {
        cropFrames.forEach { frame ->
            val left = frame.x * size.width
            val top = frame.y * size.height
            val right = (frame.x + frame.width) * size.width
            val bottom = (frame.y + frame.height) * size.height

            val isSelected = frame.id == activeFrame.id

            drawRect(
                color = if (isSelected) Accent else Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(
                    left,
                    top
                ),
                size = androidx.compose.ui.geometry.Size(
                    right - left,
                    bottom - top
                ),
                style = Stroke(
                    width = if (isSelected) {
                        4.dp.toPx()
                    } else {
                        2.dp.toPx()
                    }
                )
            )

            if (isSelected) {
                val handleSize = 16.dp.toPx()

                drawRect(
                    color = Accent,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        right - handleSize / 2f,
                        bottom - handleSize / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        handleSize,
                        handleSize
                    )
                )
            }
        }
    }
}

                
if (toolMode == ToolMode.OUTPUT && activeFrame != null) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
                        .pointerInput(
                toolMode,
                selectedClipId
            ) {
                var initialFrame: FrameRegion? = null
                var dragX = 0f
                var dragY = 0f
                var resizeMode = false

                detectDragGestures(
                    onDragStart = { offset ->
                        val currentFrames = latestClips
                            .firstOrNull { it.id == selectedClipId }
                            ?.frames
                            ?: emptyList()

                        val pointX = offset.x / size.width
                        val pointY = offset.y / size.height

                        val hitFrame = currentFrames
                            .asReversed()
                            .firstOrNull { frame ->
                                pointX >= frame.outputX &&
                                pointX <= frame.outputX + frame.outputWidth &&
                                pointY >= frame.outputY &&
                                pointY <= frame.outputY + frame.outputHeight
                            }

                        if (hitFrame == null) {
                            initialFrame = null
                            return@detectDragGestures
                        }

                        initialFrame = hitFrame
                        selectedFrameId = hitFrame.id

                        dragX = 0f
                        dragY = 0f

                        val cornerX =
                            (hitFrame.outputX + hitFrame.outputWidth) *
                                size.width

                        val cornerY =
                            (hitFrame.outputY + hitFrame.outputHeight) *
                                size.height

                        val dx = offset.x - cornerX
                        val dy = offset.y - cornerY
                        val handleRadius = 44.dp.toPx()

                        resizeMode =
                            dx * dx + dy * dy <=
                                handleRadius * handleRadius

                        saveUndoState()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        val startFrame = initialFrame
                            ?: return@detectDragGestures

                        if (size.width <= 0 || size.height <= 0) {
                            return@detectDragGestures
                        }

                        dragX += dragAmount.x / size.width.toFloat()
                        dragY += dragAmount.y / size.height.toFloat()

                        clips = latestClips.map { clip ->
                            if (clip.id != selectedClipId) {
                                clip
                            } else {
                                clip.copy(
                                    frames = clip.frames.map { frame ->
                                        if (frame.id != startFrame.id) {
                                            frame
                                        } else if (resizeMode) {
                                            val availableWidth =
                                                (1f - startFrame.outputX)
                                                    .coerceAtLeast(0.001f)

                                            val availableHeight =
                                                (1f - startFrame.outputY)
                                                    .coerceAtLeast(0.001f)

                                            frame.copy(
                                                outputWidth = (
                                                    startFrame.outputWidth +
                                                        dragX
                                                ).coerceIn(
                                                    minOf(0.08f, availableWidth),
                                                    availableWidth
                                                ),
                                                outputHeight = (
                                                    startFrame.outputHeight +
                                                        dragY
                                                ).coerceIn(
                                                    minOf(0.08f, availableHeight),
                                                    availableHeight
                                                )
                                            )
                                        } else {
                                            frame.copy(
                                                outputX = (
                                                    startFrame.outputX + dragX
                                                ).coerceIn(
                                                    0f,
                                                    1f - startFrame.outputWidth
                                                ),
                                                outputY = (
                                                    startFrame.outputY + dragY
                                                ).coerceIn(
                                                    0f,
                                                    1f - startFrame.outputHeight
                                                )
                                            )
                                        }
                                    },
                                    frameLayout = "CUSTOM"
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        initialFrame = null
                    },
                    onDragCancel = {
                        initialFrame = null
                    }
                )
            }
    ) {
        cropFrames.forEach { frame ->
            val left = frame.outputX * size.width
            val top = frame.outputY * size.height
            val right =
                (frame.outputX + frame.outputWidth) * size.width
            val bottom =
                (frame.outputY + frame.outputHeight) * size.height

            val isSelected = frame.id == activeFrame.id

            drawRect(
                color = if (isSelected) Accent else Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(
                    left,
                    top
                ),
                size = androidx.compose.ui.geometry.Size(
                    right - left,
                    bottom - top
                ),
                style = Stroke(
                    width = if (isSelected) {
                        4.dp.toPx()
                    } else {
                        2.dp.toPx()
                    }
                )
            )

            if (isSelected) {
                val handleSize = 16.dp.toPx()

                drawRect(
                    color = Accent,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        right - handleSize / 2f,
                        bottom - handleSize / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        handleSize,
                        handleSize
                    )
                )
            }
        }
    }
}
        Spacer(Modifier.height(7.dp))

                // Playback controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(
                onClick = { addClipFromRange() },
                modifier = Modifier
                    .width(82.dp)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("Add Clip", fontSize = 10.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = { stretch = !stretch },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 3.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    if (stretch) Icons.Default.FullscreenExit
                    else Icons.Default.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    if (stretch) "Fit" else "Stretch",
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    if (playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = { undoEdit() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = MainText
                )
            }

            IconButton(
                onClick = { redoEdit() },
                modifier = Modifier.size(32.dp)
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
                // Full imported source video timeline (separate from clip cards)
        val sourceDurationMs = player.duration.coerceAtLeast(1L)
        val safePositionMs = sourcePositionMs.coerceIn(
            0L,
            sourceDurationMs
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ORIGINAL VIDEO",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "${formatTime(safePositionMs)} / " +
                        formatTime(sourceDurationMs),
                    color = Accent,
                    fontSize = 10.sp
                )
            }

            Slider(
                value = safePositionMs.toFloat(),
                onValueChange = { value ->
                    sourcePositionMs = value.toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(
                        sourcePositionMs.coerceIn(
                            0L,
                            sourceDurationMs
                        )
                    )
                },
                valueRange = 0f..sourceDurationMs.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color(0xFF44464D)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        startTime = formatTime(safePositionMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Text(
                        "Set Start  ${formatTime(safePositionMs)}",
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        endTime = formatTime(safePositionMs)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Text(
                        "Set End  ${formatTime(safePositionMs)}",
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
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
                                player.seekTo(clip.startMs)
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
                                saveUndoState()

                                val remainingClips = clips.filterNot {
                                    it.id == clip.id
                                }

                                clips = remainingClips

                                if (selectedClipId == clip.id) {
                                    val nextClip = remainingClips.firstOrNull()

                                    selectedClipId = nextClip?.id

                                    if (nextClip != null) {
                                        startTime = formatTime(nextClip.startMs)
                                        endTime = formatTime(nextClip.endMs)
                                        player.seekTo(nextClip.startMs)
                                    } else {
                                        startTime = "00:00"
                                        endTime = "00:30"
                                    }
                                }

                                showMessage(context, "${clip.name} deleted")
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

              fun applyCropFrame(frame: FrameRegion) {
    val currentId = selectedClipId

    if (currentId == null) {
        showMessage(context, "Select a clip first")
        return
    }

    val selectedClip = clips.firstOrNull {
        it.id == currentId
    }

    if (selectedClip == null) {
        showMessage(context, "Selected clip not found")
        return
    }

    saveUndoState()

    val existingFrame = selectedClip.frames.firstOrNull {
        it.id == selectedFrameId
    } ?: selectedClip.frames.firstOrNull()

    val updatedFrame = if (existingFrame != null) {
        existingFrame.copy(
            x = frame.x,
            y = frame.y,
            width = frame.width,
            height = frame.height
        )
    } else {
        frame
    }

    clips = clips.map { clip ->
        if (clip.id != currentId) {
            clip
        } else {
            val updatedFrames = if (existingFrame != null) {
                clip.frames.map { currentFrame ->
                    if (currentFrame.id == existingFrame.id) {
                        updatedFrame
                    } else {
                        currentFrame
                    }
                }
            } else {
                clip.frames + updatedFrame
            }

            clip.copy(
                frameLayout = "CUSTOM",
                frames = updatedFrames
            )
        }
    }

    selectedFrameId = updatedFrame.id
    showMessage(context, "Selected speaker crop updated")
              }
                      
        fun addSpeakerFrame() {
            val selected = clips.firstOrNull {
                it.id == selectedClipId
            }

            if (selected == null) {
                showMessage(context, "Select a clip first")
                return
            }

            if (selected.frames.size >= 6) {
                showMessage(
                    context,
                    "Maximum 6 speaker frames allowed"
                )
                return
            }

            saveUndoState()

            val index = selected.frames.size

            val placement = when (index) {
                0 -> listOf(0f, 0f, 1f, 1f)
                1 -> listOf(0.68f, 0.05f, 0.28f, 0.28f)
                2 -> listOf(0.68f, 0.38f, 0.28f, 0.28f)
                3 -> listOf(0.04f, 0.68f, 0.28f, 0.28f)
                4 -> listOf(0.04f, 0.36f, 0.28f, 0.28f)
                else -> listOf(0.04f, 0.05f, 0.28f, 0.28f)
            }

            val newFrame = FrameRegion(
                name = "Speaker ${index + 1}",
                x = 0.20f,
                y = 0.10f,
                width = 0.60f,
                height = 0.80f,
                outputX = placement[0],
                outputY = placement[1],
                outputWidth = placement[2],
                outputHeight = placement[3]
            )

            val updatedFrames = selected.frames + newFrame

            clips = clips.map { clip ->
                if (clip.id == selected.id) {
                    clip.copy(
                        frames = updatedFrames,
                        frameLayout = "CUSTOM"
                    )
                } else {
                    clip
                }
            }

            selectedFrameId = newFrame.id

            showMessage(
                context,
                "${newFrame.name} added"
            )
        }
        
        fun updateSelectedFramePlacement(
            dx: Float = 0f,
            dy: Float = 0f,
            dw: Float = 0f,
            dh: Float = 0f
        ) {
            val clip = clips.firstOrNull {
                it.id == selectedClipId
            } ?: return

            val frame = clip.frames.firstOrNull {
                it.id == selectedFrameId
            } ?: return

            val newWidth = (
                frame.outputWidth + dw
            ).coerceIn(0.08f, 1f)

            val newHeight = (
                frame.outputHeight + dh
            ).coerceIn(0.08f, 1f)

            val newX = (
                frame.outputX + dx
            ).coerceIn(0f, 1f - newWidth)

            val newY = (
                frame.outputY + dy
            ).coerceIn(0f, 1f - newHeight)

            val updatedFrame = frame.copy(
                outputX = newX,
                outputY = newY,
                outputWidth = newWidth,
                outputHeight = newHeight
            )

            if (updatedFrame == frame) return

            saveUndoState()

            clips = clips.map { currentClip ->
                if (currentClip.id == clip.id) {
                    currentClip.copy(
                        frames = currentClip.frames.map {
                            if (it.id == frame.id) {
                                updatedFrame
                            } else {
                                it
                            }
                        }
                    )
                } else {
                    currentClip
                }
            }
        }

        fun deleteSelectedFrame() {
            val clip = clips.firstOrNull {
                it.id == selectedClipId
            } ?: return

            if (selectedFrameId == null) return

            val remaining = clip.frames.filterNot {
                it.id == selectedFrameId
            }

            if (remaining.size == clip.frames.size) return

            saveUndoState()

            clips = clips.map { currentClip ->
                if (currentClip.id == clip.id) {
                    currentClip.copy(
                        frames = remaining,
                        frameLayout = if (remaining.isEmpty()) {
                            "SINGLE"
                        } else {
                            "CUSTOM"
                        }
                    )
                } else {
                    currentClip
                }
            }

            selectedFrameId = remaining.firstOrNull()?.id

            showMessage(context, "Speaker frame deleted")
        }



              
        // Trim the selected timeline clip.
        fun trimSelectedClip() {
            val selected = clips.firstOrNull {
                it.id == selectedClipId
            }

            if (selected == null) {
                showMessage(context, "Select a clip from the timeline first")
                return
            }

            val parsedStartMs = parseTimeToMs(startTime)
            val parsedEndMs = parseTimeToMs(endTime)

            if (parsedStartMs == null || parsedEndMs == null) {
                showMessage(context, "Enter valid start and end times")
                return
            }

            if (parsedEndMs <= parsedStartMs) {
                showMessage(context, "End time must be after start time")
                return
            }

            if (
                parsedEndMs - parsedStartMs >
                ClipSegment.MAX_CLIP_DURATION_MS
            ) {
                showMessage(context, "A clip cannot be longer than 3 minutes")
                return
            }

            if (player.duration > 0L && parsedEndMs > player.duration) {
                showMessage(context, "End time exceeds video duration")
                return
            }

            val updated = selected.copy(
                startMs = parsedStartMs,
                endMs = parsedEndMs
            )

            when (val result = updated.validate()) {
                is ClipValidationResult.Invalid -> {
                    showMessage(context, result.reason)
                }

                ClipValidationResult.Valid -> {
                    saveUndoState()

                    clips = clips.map { clip ->
                        if (clip.id == updated.id) updated else clip
                    }

                    startTime = formatTime(updated.startMs)
                    endTime = formatTime(updated.endMs)
                    player.seekTo(updated.startMs)

                    showMessage(context, "Clip trimmed successfully")
                }
            }
        }

        // One bottom toolbar.
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
                            trimSelectedClip()
                        }
                    )

                    
                    
                    ToolButton(
                        label = "Auto Clip",
                        icon = Icons.Default.AutoAwesome,
                        onClick = {
                            generateAutoClips()
                        }
                    )


                    ToolButton(
    label = if (autoFace) "Face Applied" else "Face Detect",
    icon = Icons.Default.Face,
    active = autoFace,
    onClick = {
        detectFacesForSelectedClip()
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
                            val selected = clips.firstOrNull {
                                it.id == selectedClipId
                            }

                            if (selected == null) {
                                showMessage(context, "Select a clip first")
                            } else {
                                if (selected.frames.isEmpty()) {
                                    val initialFrame = FrameRegion(
                                        name = "Speaker 1",
                                        x = 0.1f,
                                        y = 0.1f,
                                        width = 0.8f,
                                        height = 0.8f
                                    )

                                    applyCropFrame(initialFrame)
                                    selectedFrameId = initialFrame.id
                                } else {
                                    selectedFrameId = selected.frames.first().id
                                }

                                toolMode = ToolMode.CROP
                            }
                        }
                    )
                    ToolButton(
    label = "Resize",
    icon = Icons.Default.AspectRatio,
    onClick = {
        val selected = clips.firstOrNull {
            it.id == selectedClipId
        }

        if (selected == null || selected.frames.isEmpty()) {
            showMessage(context, "Add speaker frames first")
        } else {
            selectedFrameId = selected.frames.first().id
            toolMode = ToolMode.OUTPUT
        }
    }
)

                    ToolButton(
                        label = "Zoom",
                        icon = Icons.Default.ZoomIn,
                        onClick = {
                            showMessage(
                                context,
                                "Zoom editing will be implemented in Phase 2"
                            )
                        }
                    )

                    ToolButton(
                        label = "Adjust",
                        icon = Icons.Default.Tune,
                        onClick = {
                            showMessage(
                                context,
                                "Adjust controls will be implemented in Phase 2"
                            )
                        }
                    )
                }
                                ToolMode.CROP -> {
                    ToolButton(
                        label = "Back",
                        icon = Icons.Default.ArrowBack,
                        onClick = {
                            toolMode = ToolMode.EDIT
                        }
                    
                   )
                                        ToolButton(
                        label = "Add Frame",
                        icon = Icons.Default.Add,
                        onClick = {
                            addSpeakerFrame()
                        }
                    )
                                        clips.firstOrNull { it.id == selectedClipId }
                        ?.frames
                        ?.forEach { frame ->
                            ToolButton(
                                label = frame.name,
                                icon = Icons.Default.Face,
                                active = frame.id == selectedFrameId,
                                onClick = {
                                    selectedFrameId = frame.id
                                }
                            )
                        }

                    ToolButton(
                        label = "Center",
                        icon = Icons.Default.Crop,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0.1f,
                                    y = 0.1f,
                                    width = 0.8f,
                                    height = 0.8f
                                )
                            )
                        }
                    )

                    ToolButton(
                        label = "Left",
                        icon = Icons.Default.Crop,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0f,
                                    y = 0f,
                                    width = 0.65f,
                                    height = 1f
                                )
                            )
                        }
                    )

                    ToolButton(
                        label = "Right",
                        icon = Icons.Default.Crop,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0.35f,
                                    y = 0f,
                                    width = 0.65f,
                                    height = 1f
                                )
                            )
                        }
                    )

                    ToolButton(
                        label = "Top",
                        icon = Icons.Default.Crop,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0f,
                                    y = 0f,
                                    width = 1f,
                                    height = 0.65f
                                )
                            )
                        }
                    )

                    ToolButton(
                        label = "Bottom",
                        icon = Icons.Default.Crop,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0f,
                                    y = 0.35f,
                                    width = 1f,
                                    height = 0.65f
                                )
                            )
                        }
                    )

                    ToolButton(
                        label = "Reset",
                        icon = Icons.Default.Fullscreen,
                        onClick = {
                            applyCropFrame(
                                FrameRegion(
                                    name = "Crop",
                                    x = 0f,
                                    y = 0f,
                                    width = 1f,
                                    height = 1f
                                )
                            )
                        }
                    )
                }
                                
                ToolMode.OUTPUT -> {
                    ToolButton(
                        label = "Back",
                        icon = Icons.Default.ArrowBack,
                        onClick = {
                            toolMode = ToolMode.EDIT
                        }
                    )

                    clips.firstOrNull {
                        it.id == selectedClipId
                    }?.frames?.forEach { frame ->
                        ToolButton(
                            label = frame.name,
                            icon = Icons.Default.Face,
                            active = frame.id == selectedFrameId,
                            onClick = {
                                selectedFrameId = frame.id
                            }
                        )
                    }

                    ToolButton(
                        label = "Left",
                        icon = Icons.Default.ArrowLeft,
                        onClick = {
                            updateSelectedFramePlacement(dx = -0.05f)
                        }
                    )

                    ToolButton(
                        label = "Right",
                        icon = Icons.Default.ArrowRight,
                        onClick = {
                            updateSelectedFramePlacement(dx = 0.05f)
                        }
                    )

                    ToolButton(
                        label = "Up",
                        icon = Icons.Default.ArrowUpward,
                        onClick = {
                            updateSelectedFramePlacement(dy = -0.05f)
                        }
                    )

                    ToolButton(
                        label = "Down",
                        icon = Icons.Default.ArrowDownward,
                        onClick = {
                            updateSelectedFramePlacement(dy = 0.05f)
                        }
                    )

                    ToolButton(
                        label = "Wider",
                        icon = Icons.Default.ZoomIn,
                        onClick = {
                            updateSelectedFramePlacement(dw = 0.05f)
                        }
                    )

                    ToolButton(
                        label = "Narrow",
                        icon = Icons.Default.ZoomOut,
                        onClick = {
                            updateSelectedFramePlacement(dw = -0.05f)
                        }
                    )

                    ToolButton(
                        label = "Taller",
                        icon = Icons.Default.ZoomIn,
                        onClick = {
                            updateSelectedFramePlacement(dh = 0.05f)
                        }
                    )

                    ToolButton(
                        label = "Shorter",
                        icon = Icons.Default.ZoomOut,
                        onClick = {
                            updateSelectedFramePlacement(dh = -0.05f)
                        }
                    )

                    ToolButton(
                        label = "Delete",
                        icon = Icons.Default.Delete,
                        onClick = {
                            deleteSelectedFrame()
                        }
                    )
                }


                                ToolMode.PREVIEW -> {
                    // PreviewScreen is displayed by the early return above.
                }
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
    selectedTemplate: Int,
    onSelectTemplate: (SplitTemplate) -> Unit,
    onSelectRatio: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val previewPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    var previewFile by remember {
        mutableStateOf<File?>(null)
    }

    var previewReady by remember {
        mutableStateOf(false)
    }

    var previewPlaying by remember {
        mutableStateOf(false)
    }

    var previewStatus by remember {
        mutableStateOf("Preparing preview...")
    }

    var previewPositionMs by remember {
        mutableLongStateOf(0L)
    }

    var previewDurationMs by remember {
        mutableLongStateOf(1L)
    }

    var previewRequestId by remember {
        mutableIntStateOf(0)
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                previewPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    previewReady = true
                    previewDurationMs =
                        previewPlayer.duration.coerceAtLeast(1L)

                    previewStatus = "Combined preview ready"
                }

                if (playbackState == Player.STATE_ENDED) {
                    previewPlayer.seekTo(0L)
                    previewPositionMs = 0L
                }
            }
        }

        previewPlayer.addListener(listener)

        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    DisposableEffect(player, videoUri, selectedClip) {
        previewRequestId += 1
        val requestId = previewRequestId

        player.pause()
        previewPlayer.pause()
        previewPlayer.clearMediaItems()

        previewFile?.delete()
        previewFile = null

        previewReady = false
        previewPlaying = false
        previewPositionMs = 0L
        previewDurationMs =
            selectedClip?.durationMs?.coerceAtLeast(1L) ?: 1L

        var exporter: ClipPreviewExporter? = null

        if (videoUri == null || selectedClip == null) {
            previewStatus = "Select a clip to generate preview"
        } else {
            previewStatus = "Preparing combined composition..."

            exporter = ClipPreviewExporter(
                context = context.applicationContext,
                sourceUri = videoUri,
                clip = selectedClip,

                onProgress = { message ->
                    if (requestId == previewRequestId) {
                        previewStatus = message
                    }
                },

                onComplete = { file ->
                    if (requestId != previewRequestId) {
                        file?.delete()
                    } else if (file == null) {
                        previewReady = false
                    } else {
                        previewFile = file

                        previewPlayer.setMediaItem(
                            MediaItem.fromUri(Uri.fromFile(file))
                        )

                        previewPlayer.prepare()
                        previewPlayer.seekTo(0L)
                        previewPositionMs = 0L
                        previewStatus = "Loading combined preview..."
                    }
                }
            )

            exporter?.start()
        }

        onDispose {
            exporter?.cancel()
            previewPlayer.pause()
            previewPlayer.clearMediaItems()
            previewFile?.delete()
        }
    }

    LaunchedEffect(previewReady, previewPlaying, previewDurationMs) {
        while (previewReady && previewPlaying) {
            val current = previewPlayer.currentPosition
                .coerceAtLeast(0L)

            if (current >= previewDurationMs) {
                previewPlayer.pause()
                previewPlayer.seekTo(0L)
                previewPositionMs = 0L
            } else {
                previewPositionMs = current
            }

            delay(100L)
        }
    }

    val previewAspectRatio = when (selectedClip?.aspectRatio) {
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        else -> 9f / 16f
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
                    previewPlayer.pause()
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
                .aspectRatio(previewAspectRatio)
                .background(Color.Black, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (previewReady) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = previewPlayer
                            useController = false
                            resizeMode =
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        view.player = previewPlayer
                        view.resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                )
            } else {
                Text(
                    text = previewStatus,
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(previewPositionMs),
                color = MainText,
                fontSize = 12.sp
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = formatTime(previewDurationMs),
                color = Muted,
                fontSize = 12.sp
            )
        }

        Slider(
            value = previewPositionMs
                .coerceIn(0L, previewDurationMs)
                .toFloat(),
            onValueChange = { value ->
                previewPositionMs = value.toLong()
            },
            onValueChangeFinished = {
                if (previewReady) {
                    previewPlayer.seekTo(
                        previewPositionMs.coerceIn(
                            0L,
                            previewDurationMs
                        )
                    )
                }
            },
            valueRange = 0f..previewDurationMs
                .coerceAtLeast(1L).toFloat(),
            enabled = previewReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF44464D)
            )
        )

        Text(
            text = "SPLIT-SCREEN TEMPLATES",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("9:16", "1:1", "16:9").forEach { ratio ->
                FilterChip(
                    selected = selectedClip?.aspectRatio == ratio,
                    onClick = { onSelectRatio(ratio) },
                    label = {
                        Text(ratio, fontSize = 10.sp)
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            splitTemplates.forEach { template ->
                ToolButton(
                    label = template.title,
                    icon = Icons.Default.ViewQuilt,
                    active = selectedClip?.frameLayout ==
                        templateLayout(template.id),
                    onClick = {
                        onSelectTemplate(template)
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    if (!previewReady) {
                        return@FilledIconButton
                    }

                    if (previewPlayer.isPlaying) {
                        previewPlayer.pause()
                    } else {
                        if (
                            previewPlayer.currentPosition >=
                            previewDurationMs
                        ) {
                            previewPlayer.seekTo(0L)
                        }

                        previewPlayer.play()
                    }
                },
                enabled = previewReady,
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    if (previewPlaying) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (previewPlaying) {
                        "Pause"
                    } else {
                        "Play"
                    },
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Text(
            text = previewStatus,
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
