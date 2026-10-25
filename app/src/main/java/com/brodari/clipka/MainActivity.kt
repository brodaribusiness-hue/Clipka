
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
                // Some providers do not support persistable permissions.
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
            modifier = Modifier.fillMaxWidth(),
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

                Text(
                    "Your saved projects",
                    color = MainText
                )

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
    var showEdit by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var autoFace by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        // Top bar: Close | File name | Export
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
                Text(
                    "Export",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        // Main video preview.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Color.Black,
                    RoundedCornerShape(12.dp)
                ),
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

        // Fit/Stretch, Play/Pause, Undo and Redo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = {
                    stretch = !stretch
                },
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

        // Start and End time on the same row.
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

        // Timeline strip. These are currently visual placeholders.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Panel,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TIMELINE",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "$startTime  —  $endTime",
                    color = Accent,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(10) { index ->
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(40.dp)
                            .background(
                                if (index == 3) Selected
                                else Color(0xFF303238),
                                RoundedCornerShape(5.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = "Timeline segment ${index + 1}",
                            tint = if (index == 3) Accent else Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        // Bottom tool row; no vertical scrolling.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
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
                    showMessage(
                        context,
                        "Trim action is not connected yet"
                    )
                }
            )

            ToolButton(
                label = "Auto Clip",
                icon = Icons.Default.AutoAwesome,
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1.15f),
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
                modifier = Modifier.weight(1f),
                onClick = { showPreview = true }
            )
        }
    }

    if (showEdit) {
        OptionsDialog(
            title = "Edit Video",
            options = listOf(
                "Crop",
                "Resize",
                "Zoom",
                "Adjust"
            ),
            onDismiss = { showEdit = false },
            onSelect = { option ->
                showEdit = false
                showMessage(
                    context,
                    "$option selected; editing action is not connected yet"
                )
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
                showMessage(
                    context,
                    "$option selected; layout integration is not connected yet"
                )
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
            text = label,
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
            Text(
                title,
                color = MainText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                            },
                        color = Color(0xFF282A30),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text(
                            text = option,
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
