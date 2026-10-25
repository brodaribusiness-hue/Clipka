
package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private val AppBackground = Color(0xFF101114)
private val PanelColor = Color(0xFF1C1E23)
private val AccentColor = Color(0xFFB9F36B)
private val MainText = Color(0xFFF5F5F5)
private val MutedText = Color(0xFF9A9DA5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBackground,
                    surface = PanelColor,
                    primary = AccentColor,
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

private enum class AppScreen {
    HOME, PROJECTS, EDITOR
}

@Composable
private fun ClipkaApp() {
    val context = LocalContext.current

    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Untitled video") }
    var isPlaying by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
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
            screen = AppScreen.EDITOR
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackground
    ) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                onImport = {
                    videoPicker.launch(arrayOf("video/*"))
                },
                onProjects = {
                    screen = AppScreen.PROJECTS
                }
            )

            AppScreen.PROJECTS -> ProjectsScreen(
                onBack = { screen = AppScreen.HOME },
                onImport = {
                    videoPicker.launch(arrayOf("video/*"))
                }
            )

            AppScreen.EDITOR -> EditorScreen(
                fileName = fileName,
                player = player,
                videoUri = videoUri,
                isPlaying = isPlaying,
                onClose = {
                    player.pause()
                    screen = AppScreen.HOME
                },
                onPlayPause = {
                    if (player.isPlaying) player.pause()
                    else player.play()
                },
                onUndo = {
                    Toast.makeText(
                        context,
                        "Undo will be connected in the editing phase",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onRedo = {
                    Toast.makeText(
                        context,
                        "Redo will be connected in the editing phase",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onExport = {
                    Toast.makeText(
                        context,
                        "Export will be connected in the next phase",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CLIPKA",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 5.sp,
            color = AccentColor
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your video. Your story.",
            color = MutedText,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
                contentColor = Color(0xFF171914)
            )
        ) {
            Icon(Icons.Default.Movie, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Import Video", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onProjects,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Back")
            }

            Text(
                "Projects",
                modifier = Modifier.weight(1f),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Your projects will appear here", color = MainText)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saved-project loading will be connected next.",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentColor,
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
    player: ExoPlayer,
    videoUri: Uri?,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top bar: close | file name | export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(42.dp)
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
                    .padding(horizontal = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MainText
            )

            Button(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = Color(0xFF171914)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Export", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Main video preview occupies most of the screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode =
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        view.player = player
                    }
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Playback controls, followed immediately by undo and redo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(PanelColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AccentColor,
                    contentColor = Color(0xFF171914)
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(29.dp)
                )
            }

            Spacer(Modifier.width(18.dp))

            IconButton(onClick = onUndo) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = MainText
                )
            }

            IconButton(onClick = onRedo) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = MainText
                )
            }
        }
    }
}

package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private enum class Screen { HOME, PROJECTS, EDITOR }

@Composable
private fun ClipkaApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Untitled video") }
    var playing by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }

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
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?: "Imported video"
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            screen = Screen.EDITOR
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppBg) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onImport = { picker.launch(arrayOf("video/*")) },
                onProjects = { screen = Screen.PROJECTS }
            )

            Screen.PROJECTS -> ProjectsScreen(
                onBack = { screen = Screen.HOME },
                onImport = { picker.launch(arrayOf("video/*")) }
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
                    if (player.isPlaying) player.pause() else player.play()
                },
                onUndo = {
                    toast(context, "Undo integration pending")
                },
                onRedo = {
                    toast(context, "Redo integration pending")
                },
                onExport = {
                    toast(context, "Export integration pending")
                }
            )
        }
    }
}

private fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun HomeScreen(
    onImport: () -> Unit,
    onProjects: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "CLIPKA",
            color = Accent,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 5.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("Your video. Your story.", color = Muted)
        Spacer(Modifier.height(44.dp))

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color(0xFF171914)
            )
        ) {
            Icon(Icons.Default.Movie, null)
            Spacer(Modifier.width(10.dp))
            Text("Import Video", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onProjects,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null)
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
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Back")
            }
            Text(
                "Projects",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderOpen,
                    null,
                    tint = Muted,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("Your saved projects", color = MainText)
                Text(
                    "Saved-project loading will be connected next.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Import Video", color = Color(0xFF171914))
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
    var showEdit by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var autoFace by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(
            horizontal = 10.dp,
            vertical = 5.dp
        )
    ) {
        // TOP BAR: Close | file name | Export
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Close, "Close editor", tint = MainText)
            }

            Text(
                fileName,
                Modifier.weight(1f).padding(horizontal = 5.dp),
                color = MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Export", color = Color(0xFF171914), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(5.dp))

        // VIDEO PREVIEW
        Box(
            Modifier
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
                Icon(Icons.Default.Movie, null, tint = Muted, modifier = Modifier.size(50.dp))
            }
        }

        Spacer(Modifier.height(7.dp))

        // PLAYBACK + FULL SCREEN / STRETCH + UNDO / REDO
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = { stretch = !stretch },
                modifier = Modifier.weight(1.35f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (stretch) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    null,
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
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(onClick = onUndo, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Undo, "Undo", tint = MainText)
            }
            IconButton(onClick = onRedo, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Redo, "Redo", tint = MainText)
            }
        }

        Spacer(Modifier.height(6.dp))

        // START + END TIME IN ONE ROW
        Row(
            Modifier.fillMaxWidth().height(52.dp),
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

        // THUMBNAIL TIMELINE STRIP
        Column(
            Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TIMELINE",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text("$startTime  —  $endTime", color = Accent, fontSize = 10.sp)
            }

            Spacer(Modifier.height(5.dp))

            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(10) { index ->
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(40.dp)
                            .background(
                                if (index == 3) Selected else Color(0xFF303238),
                                RoundedCornerShape(5.dp)
                            )
                            .then(
                                if (index == 3) Modifier.background(
                                    Selected,
                                    RoundedCornerShape(5.dp)
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            null,
                            tint = if (index == 3) Accent else Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        // BOTTOM TOOL ROW
        Row(
            Modifier.fillMaxWidth().height(70.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                label = "Edit",
                icon = Icons.Default.Tune,
                modifier = Modifier.weight(1f),
                onClick = { showEdit = true }
            )

            ToolButton(
                label = "Trim",
                icon = Icons.Default.ContentCut,
                modifier = Modifier.weight(1f),
                onClick = {
                    toast(context, "Trim UI ready; video trimming integration pending")
                }
            )

            ToolButton(
                label = "Auto Clip",
                icon = Icons.Default.AutoAwesome,
                modifier = Modifier.weight(1f),
                onClick = {
                    toast(context, "Viral clip detection engine pending")
                }
            )

            ToolButton(
                label = if (autoFace) "Face ON" else "Face Detect",
                icon = Icons.Default.Face,
                modifier = Modifier.weight(1.15f),
                active = autoFace,
                onClick = {
                    autoFace = !autoFace
                    toast(
                        context,
                        if (autoFace) "Face detection selected" else "Face detection paused"
                    )
                }
            )

            ToolButton(
                label = "Preview",
                icon = Icons.Default.ViewCarousel,
                modifier = Modifier.weight(1f),
                onClick = { showPreview = true }
            )
        }
    }

    if (showEdit) {
        OptionsDialog(
            title = "Edit Video",
            options = listOf("Crop", "Resize", "Zoom", "Adjust"),
            onDismiss = { showEdit = false },
            onSelect = { option ->
                showEdit = false
                toast(context, "$option selected — editing action integration pending")
            }
        )
    }

    if (showPreview) {
        OptionsDialog(
            title = "Preview & Layout",
            options = listOf(
                "Split Screen — Template 1",
                "Split Screen — Template 2",
                "Split Screen — Template 3",
                "Split Screen — Template 4",
                "Split Screen — Template 5",
                "Split Screen — Template 6",
                "Aspect Ratio",
                "Manual Framing",
                "Automatic Framing",
                "Add Multiple Clips"
            ),
            onDismiss = { showPreview = false },
            onSelect = { option ->
                showPreview = false
                toast(context, "$option selected — feature integration pending")
            }
        )
    }
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
            onValueChange(input.filter { it.isDigit() || it == ':' }.take(8))
        },
        modifier = modifier.height(52.dp),
        label = { Text(label, fontSize = 9.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (active) Selected else Panel,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Accent else MainText,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) Accent else MainText,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OptionsDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text(title, color = MainText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                options.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) },
                        color = Color(0xFF282A30),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            option,
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 12.dp
                            ),
                            color = MainText,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Accent)
            }
        }
    )
}
