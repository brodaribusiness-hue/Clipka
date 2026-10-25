
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
