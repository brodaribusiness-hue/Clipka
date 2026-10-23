
package com.brodari.clipka

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FrameEditorAccent = Color(0xFFB7A9FF)
private val FrameEditorGreen = Color(0xFF70D6B5)
private val FrameEditorOrange = Color(0xFFFFB86B)

@Composable
fun FrameEditor(
    clip: ClipSegment,
    onFramesChange: (List<FrameRegion>) -> Unit,
    onLayoutChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "FRAME EDITOR · ${clip.name}",
            color = FrameEditorAccent
        )

        Text(
            text = "Vertical split-screen layouts",
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "50/50",
                selected = clip.frameLayout == "SPLIT_VERTICAL",
                onClick = {
                    selectPreset(
                        clip,
                        "SPLIT_VERTICAL",
                        onFramesChange,
                        onLayoutChange
                    )
                },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "60/40",
                selected = clip.frameLayout == "SPLIT_60_40",
                onClick = {
                    selectPreset(
                        clip,
                        "SPLIT_60_40",
                        onFramesChange,
                        onLayoutChange
                    )
                },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "40/60",
                selected = clip.frameLayout == "SPLIT_40_60",
                onClick = {
                    selectPreset(
                        clip,
                        "SPLIT_40_60",
                        onFramesChange,
                        onLayoutChange
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "Side-by-side",
                selected = clip.frameLayout == "SPLIT_HORIZONTAL",
                onClick = {
                    selectPreset(
                        clip,
                        "SPLIT_HORIZONTAL",
                        onFramesChange,
                        onLayoutChange
                    )
                },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "Diagonal*",
                selected = clip.frameLayout == "SPLIT_DIAGONAL",
                onClick = {
                    selectPreset(
                        clip,
                        "SPLIT_DIAGONAL",
                        onFramesChange,
                        onLayoutChange
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "Single",
                selected = clip.frameLayout == "SINGLE",
                onClick = {
                    onLayoutChange("SINGLE")
                },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "Custom",
                selected = clip.frameLayout == "CUSTOM",
                onClick = {
                    onLayoutChange("CUSTOM")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "*Diagonal export is not supported by the current exporter.",
            color = Color.LightGray
        )

        Text(
            text = "Frame layout preview",
            color = Color.White
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(
                    1.dp,
                    FrameEditorAccent,
                    RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            val previewWidth = maxWidth
            val previewHeight = 224.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
            ) {
                clip.frames.forEachIndexed { index, frame ->
                    val frameColor = when (index % 3) {
                        0 -> FrameEditorAccent
                        1 -> FrameEditorGreen
                        else -> FrameEditorOrange
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = previewWidth * frame.x,
                                y = previewHeight * frame.y
                            )
                            .width(previewWidth * frame.width)
                            .height(previewHeight * frame.height)
                            .border(
                                2.dp,
                                frameColor,
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = frame.name,
                            color = frameColor
                        )
                    }
                }

                if (clip.frames.isEmpty()) {
                    Text(
                        text = "No frames yet",
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Button(
            onClick = {
                val index = clip.frames.size
                val newFrame = FrameRegion(
                    name = "Frame ${index + 1}",
                    x = if (index % 2 == 0) 0.1f else 0.5f,
                    y = 0.1f,
                    width = 0.4f,
                    height = 0.6f
                )
                onFramesChange(clip.frames + newFrame)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Frame")
        }

        clip.frames.forEachIndexed { index, frame ->
            FrameControls(
                clip = clip,
                frame = frame,
                index = index,
                onFramesChange = onFramesChange
            )
        }
    }
}

private fun selectPreset(
    clip: ClipSegment,
    layout: String,
    onFramesChange: (List<FrameRegion>) -> Unit,
    onLayoutChange: (String) -> Unit
) {
    val presetFrames = when (layout) {
        "SPLIT_VERTICAL" -> listOf(
            FrameRegion(
                name = "Speaker 1",
                x = 0f,
                y = 0f,
                width = 1f,
                height = 0.5f
            ),
            FrameRegion(
                name = "Speaker 2",
                x = 0f,
                y = 0.5f,
                width = 1f,
                height = 0.5f
            )
        )

        "SPLIT_60_40" -> listOf(
            FrameRegion(
                name = "Speaker 1",
                x = 0f,
                y = 0f,
                width = 1f,
                height = 0.6f
            ),
            FrameRegion(
                name = "Speaker 2",
                x = 0f,
                y = 0.6f,
                width = 1f,
                height = 0.4f
            )
        )

        "SPLIT_40_60" -> listOf(
            FrameRegion(
                name = "Speaker 1",
                x = 0f,
                y = 0f,
                width = 1f,
                height = 0.4f
            ),
            FrameRegion(
                name = "Speaker 2",
                x = 0f,
                y = 0.4f,
                width = 1f,
                height = 0.6f
            )
        )

        "SPLIT_HORIZONTAL" -> listOf(
            FrameRegion(
                name = "Speaker 1",
                x = 0f,
                y = 0f,
                width = 0.5f,
                height = 1f
            ),
            FrameRegion(
                name = "Speaker 2",
                x = 0.5f,
                y = 0f,
                width = 0.5f,
                height = 1f
            )
        )

        "SPLIT_DIAGONAL" -> listOf(
            FrameRegion(
                name = "Speaker 1",
                x = 0f,
                y = 0f,
                width = 1f,
                height = 0.5f
            ),
            FrameRegion(
                name = "Speaker 2",
                x = 0f,
                y = 0.5f,
                width = 1f,
                height = 0.5f
            )
        )

        else -> clip.frames
    }

    if (layout.startsWith("SPLIT_")) {
        val detectedFrames = clip.frames
            .filter { it.detectedFace }
            .take(2)

        onFramesChange(
            if (detectedFrames.size >= 2) {
                detectedFrames
            } else {
                presetFrames
            }
        )
    }

    onLayoutChange(layout)
}

@Composable
private fun FrameControls(
    clip: ClipSegment,
    frame: FrameRegion,
    index: Int,
    onFramesChange: (List<FrameRegion>) -> Unit
) {
    var draftX by remember(clip.id, frame.id, frame.x) {
        mutableFloatStateOf(frame.x)
    }
    var draftY by remember(clip.id, frame.id, frame.y) {
        mutableFloatStateOf(frame.y)
    }
    var draftWidth by remember(clip.id, frame.id, frame.width) {
        mutableFloatStateOf(frame.width)
    }
    var draftHeight by remember(clip.id, frame.id, frame.height) {
        mutableFloatStateOf(frame.height)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1C24)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${frame.name} · ${index + 1}",
                color = Color.White
            )

            Text("Horizontal position", color = Color.LightGray)
            Slider(
                value = draftX,
                onValueChange = { value ->
                    draftX = value.coerceIn(0f, 1f - draftWidth)
                },
                valueRange = 0f..(1f - draftWidth).coerceAtLeast(0f),
                onValueChangeFinished = {
                    onFramesChange(
                        clip.frames.map {
                            if (it.id == frame.id) {
                                it.copy(x = draftX)
                            } else it
                        }
                    )
                }
            )

            Text("Vertical position", color = Color.LightGray)
            Slider(
                value = draftY,
                onValueChange = { value ->
                    draftY = value.coerceIn(0f, 1f - draftHeight)
                },
                valueRange = 0f..(1f - draftHeight).coerceAtLeast(0f),
                onValueChangeFinished = {
                    onFramesChange(
                        clip.frames.map {
                            if (it.id == frame.id) {
                                it.copy(y = draftY)
                            } else it
                        }
                    )
                }
            )

            Text("Width", color = Color.LightGray)
            Slider(
                value = draftWidth,
                onValueChange = { value ->
                    draftWidth = value.coerceIn(
                        0.05f,
                        1f - draftX
                    )
                },
                valueRange = 0.05f..(1f - draftX).coerceAtLeast(0.05f),
                onValueChangeFinished = {
                    onFramesChange(
                        clip.frames.map {
                            if (it.id == frame.id) {
                                it.copy(width = draftWidth)
                            } else it
                        }
                    )
                }
            )

            Text("Height", color = Color.LightGray)
            Slider(
                value = draftHeight,
                onValueChange = { value ->
                    draftHeight = value.coerceIn(
                        0.05f,
                        1f - draftY
                    )
                },
                valueRange = 0.05f..(1f - draftY).coerceAtLeast(0.05f),
                onValueChangeFinished = {
                    onFramesChange(
                        clip.frames.map {
                            if (it.id == frame.id) {
                                it.copy(height = draftHeight)
                            } else it
                        }
                    )
                }
            )

            OutlinedButton(
                onClick = {
                    onFramesChange(
                        clip.frames.filterNot { it.id == frame.id }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete ${frame.name}")
            }
        }
    }
}

@Composable
private fun LayoutButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label)
        }
    }
}
