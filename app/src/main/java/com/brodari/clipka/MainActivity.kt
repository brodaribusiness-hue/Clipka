
package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B8CFF),
                    background = Color(0xFF101116),
                    surface = Color(0xFF1B1C24)
                )
            ) {
                ClipkaScreen()
            }
        }
    }
}

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

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUri = uri
            videoName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?: "Selected video"

            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            isPlaying = false
            positionMs = 0L
            durationMs = 0L
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                val duration = player.duration
                if (duration > 0L) {
                    durationMs = duration
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                if (duration > 0L) {
                    durationMs = duration
                }
            }
            delay(300L)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101116)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "clipka",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB7A9FF)
            )

            Text(
                text = "AI Video Clip Studio",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C24)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUri == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = Color(0xFFB7A9FF)
                            )
                            Text(
                                text = "Select a video to preview",
                                color = Color.White
                            )
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                }
                            },
                            update = { it.player = player }
                        )
                    }
                }
            }

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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else if (videoUri != null) {
                            player.play()
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
                        player.seekTo(0L)
                        player.pause()
                    },
                    enabled = videoUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Restart")
                }
            }

            Text(
                text = "Video timeline",
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

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
                        player.seekTo(
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C24)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Coming next",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("• Manual start and end times")
                    Text("• Multiple clips and undo/redo")
                    Text("• 9:16, 1:1 and 16:9 layouts")
                    Text("• Face tracking and smart clip suggestions")
                    Text(
                        text = "Captions are not included in this phase.",
                        color = Color(0xFFB7A9FF)
                    )
                }
            }
        }
    }
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
