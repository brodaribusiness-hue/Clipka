File path: "app/src/main/java/com/brodari/clipka/MainActivity.kt"

package com.brodari.clipka

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    var startInput by remember { mutableStateOf("00:00") }
    var endInput by remember { mutableStateOf("00:30") }
    var clips by remember { mutableStateOf(listOf<ClipSegment>()) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var clipError by remember { mutableStateOf("") }

    var undoStack by remember {
        mutableStateOf(listOf<List<ClipSegment>>())
    }
    var redoStack by remember {
        mutableStateOf(listOf<List<ClipSegment>>())
    }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    val previewPlayer = player

    fun saveClipHistory() {
        undoStack = undoStack + listOf(clips)
        redoStack = emptyList()
    }

    fun applyClipChange(updatedClips: List<ClipSegment>) {
        saveClipHistory()
        clips = updatedClips
        clipError = ""
    }

    fun selectClip(clip: ClipSegment) {
        selectedClipId = clip.id
        startInput = formatTime(clip.startMs)
        endInput = formatTime(clip.endMs)
        clipError = ""
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
            player.pause()

            isPlaying = false
            positionMs = 0L
            durationMs = 0L

            clips = emptyList()
            selectedClipId = null
            undoStack = emptyList()
            redoStack = emptyList()
            clipError = ""
            startInput = "00:00"
            endInput = "00:30"
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
                .verticalScroll(rememberScrollState())
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
                                    player = previewPlayer
                                    useController = false
                                }
                            },
                            update = { view ->
                                view.player = previewPlayer
                            }
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
                            val previous = undoStack.last()
                            redoStack = redoStack + listOf(clips)
                            undoStack = undoStack.dropLast(1)
                            clips = previous

                            if (clips.none { it.id == selectedClipId }) {
                                selectedClipId = null
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
                            val next = redoStack.last()
                            undoStack = undoStack + listOf(clips)
                            redoStack = redoStack.dropLast(1)
                            clips = next

                            if (clips.none { it.id == selectedClipId }) {
                                selectedClipId = null
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
                    val start = parseTime(startInput)
                    val end = parseTime(endInput)

                    clipError = when {
                        videoUri == null ->
                            "Pehle video select karo."

                        start == null || end == null ->
                            "Time format sahi nahi. Example: 01:25"

                        end <= start ->
                            "End time, start time se aage hona chahiye."

                        end - start > ClipSegment.MAX_CLIP_DURATION_MS ->
                            "Clip maximum 3 minutes ka ho sakta hai."

                        durationMs > 0L && end > durationMs ->
                            "End time video duration se zyada hai."

                        else -> ""
                    }

                    if (
                        clipError.isEmpty() &&
                        start != null &&
                        end != null
                    ) {
                        val newClip = ClipSegment(
                            name = "Clip ${clips.size + 1}",
                            startMs = start,
                            endMs = end
                        )

                        applyClipChange(clips + newClip)
                        selectedClipId = newClip.id
                    }
                },
                enabled = videoUri != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Clip")
            }

            Button(
                onClick = {
                    val selected = clips.find {
                        it.id == selectedClipId
                    }
                    val start = parseTime(startInput)
                    val end = parseTime(endInput)

                    clipError = when {
                        selected == null ->
                            "Pehle list se clip select karo."

                        start == null || end == null ->
                            "Time format sahi nahi."

                        end <= start ->
                            "End time, start time se aage hona chahiye."

                        end - start > ClipSegment.MAX_CLIP_DURATION_MS ->
                            "Clip maximum 3 minutes ka ho sakta hai."

                        durationMs > 0L && end > durationMs ->
                            "End time video duration se zyada hai."

                        else -> ""
                    }

                    if (
                        clipError.isEmpty() &&
                        selected != null &&
                        start != null &&
                        end != null
                    ) {
                        val updated = selected.copy(
                            startMs = start,
                            endMs = end
                        )

                        applyClipChange(
                            clips.map {
                                if (it.id == selected.id) updated else it
                            }
                        )
                        selectedClipId = updated.id
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

            clips.forEach { clip ->
                val selected = clip.id == selectedClipId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) {
                                Color(0xFFB7A9FF)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            selectClip(clip)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            Color(0xFF29253B)
                        } else {
                            Color(0xFF1B1C24)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = clip.name +
                                if (selected) " • Selected" else "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${formatTime(clip.startMs)} - ${formatTime(clip.endMs)}",
                            color = Color.LightGray
                        )

                        Text(
                            text = "Duration: ${formatTime(clip.durationMs)}",
                            color = Color(0xFFB7A9FF)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectClip(clip)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Edit")
                            }

                            OutlinedButton(
                                onClick = {
                                    applyClipChange(
                                        clips.filterNot {
                                            it.id == clip.id
                                        }
                                    )

                                    if (selectedClipId == clip.id) {
                                        selectedClipId = null
                                        startInput = "00:00"
                                        endInput = "00:30"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete")
                            }

                            OutlinedButton(
                                onClick = {
                                    player.seekTo(clip.startMs)
                                    player.play()
                                },
                                enabled = videoUri != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Preview")
                            }
                        }
                    }
                }
            }

            Text(
                text = "Phase 2: Clip selection, editing, delete, undo and redo.",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            Text(
                text = "Actual video export is the next phase.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
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
