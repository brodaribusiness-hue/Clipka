
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FrameEditorAccent = Color(0xFFB7A9FF)

@Composable
fun FrameEditor(
    clip: ClipSegment,
    onFramesChange: (List<FrameRegion>) -> Unit,
    onLayoutChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Frame Editor — ${clip.name}",
            color = Color.White
        )

        Text(
            text = "Adjust each frame's position and size. " +
                "Each clip keeps its own framing settings.",
            color = Color.LightGray
        )

        Text("Layout", color = Color.White)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "Single",
                selected = clip.frameLayout == "SINGLE",
                onClick = { onLayoutChange("SINGLE") },
                modifier = Modifier.weight(1f)
            )

            LayoutButton(
                label = "Top/Bottom",
                selected = clip.frameLayout == "SPLIT_VERTICAL",
                enabled = clip.frames.size >= 2,
                onClick = { onLayoutChange("SPLIT_VERTICAL") },
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
                enabled = clip.frames.size >= 2,
                onClick = { onLayoutChange("SPLIT_HORIZONTAL") },
                modifier = Modifier.weight(1f)
            )

            LayoutButton(
                label = "Custom",
                selected = clip.frameLayout == "CUSTOM",
                onClick = { onLayoutChange("CUSTOM") },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Frame preview (source-video coordinates)",
            color = Color.White
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(
                    width = 1.dp,
                    color = FrameEditorAccent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            // Capture the width here so nested Box scopes can use it.
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
                        1 -> Color(0xFF70D6B5)
                        else -> Color(0xFFFFB86B)
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
                                width = 2.dp,
                                color = frameColor,
                                shape = RoundedCornerShape(4.dp)
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
                        text = "Add a frame to begin",
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Button(
            onClick = {
                val index = clip.frames.size

                val newFrame = if (index == 0) {
                    FrameRegion(
                        name = "Frame 1",
                        x = 0.20f,
                        y = 0.10f,
                        width = 0.60f,
                        height = 0.80f
                    )
                } else {
                    val x = if (index % 2 == 0) 0.05f else 0.55f

                    FrameRegion(
                        name = "Frame ${index + 1}",
                        x = x,
                        y = 0.10f,
                        width = 0.40f,
                        height = 0.60f
                    )
                }

                onFramesChange(clip.frames + newFrame)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Frame")
        }

        if (
            clip.frameLayout == "SPLIT_VERTICAL" ||
            clip.frameLayout == "SPLIT_HORIZONTAL"
        ) {
            Text(
                text = "Split layouts require at least 2 frames.",
                color = FrameEditorAccent
            )
        }

        clip.frames.forEachIndexed { index, frame ->
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
                        text = "${frame.name} — Frame ${index + 1}",
                        color = Color.White
                    )

                    Text("Horizontal position", color = Color.LightGray)

                    Slider(
                        value = frame.x,
                        onValueChange = { value ->
                            updateFrame(
                                clip,
                                frame,
                                frame.copy(
                                    x = value.coerceIn(
                                        0f,
                                        (1f - frame.width).coerceAtLeast(0f)
                                    )
                                ),
                                onFramesChange
                            )
                        },
                        valueRange = 0f..(1f - frame.width)
                            .coerceAtLeast(0f)
                    )

                    Text("Vertical position", color = Color.LightGray)

                    Slider(
                        value = frame.y,
                        onValueChange = { value ->
                            updateFrame(
                                clip,
                                frame,
                                frame.copy(
                                    y = value.coerceIn(
                                        0f,
                                        (1f - frame.height).coerceAtLeast(0f)
                                    )
                                ),
                                onFramesChange
                            )
                        },
                        valueRange = 0f..(1f - frame.height)
                            .coerceAtLeast(0f)
                    )

                    Text("Frame width", color = Color.LightGray)

                    Slider(
                        value = frame.width,
                        onValueChange = { value ->
                            val newWidth = value.coerceIn(
                                0.10f,
                                (1f - frame.x).coerceAtLeast(0.10f)
                            )

                            updateFrame(
                                clip,
                                frame,
                                frame.copy(width = newWidth),
                                onFramesChange
                            )
                        },
                        valueRange = 0.10f..(1f - frame.x)
                            .coerceAtLeast(0.10f)
                    )

                    Text("Frame height", color = Color.LightGray)

                    Slider(
                        value = frame.height,
                        onValueChange = { value ->
                            val newHeight = value.coerceIn(
                                0.10f,
                                (1f - frame.y).coerceAtLeast(0.10f)
                            )

                            updateFrame(
                                clip,
                                frame,
                                frame.copy(height = newHeight),
                                onFramesChange
                            )
                        },
                        valueRange = 0.10f..(1f - frame.y)
                            .coerceAtLeast(0.10f)
                    )

                    OutlinedButton(
                        onClick = {
                            onFramesChange(
                                clip.frames.filterNot { it.id == frame.id }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove ${frame.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutButton(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Text(label)
        }
    }
}

private fun updateFrame(
    clip: ClipSegment,
    oldFrame: FrameRegion,
    newFrame: FrameRegion,
    onFramesChange: (List<FrameRegion>) -> Unit
) {
    onFramesChange(
        clip.frames.map { frame ->
            if (frame.id == oldFrame.id) newFrame else frame
        }
    )
}
