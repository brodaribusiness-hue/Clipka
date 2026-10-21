
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Frame Editor — ${clip.name}",
            color = Color.White
        )

        
        fun selectLayout(layout: String) {
            val presetFrames = when (layout) {
                "SPLIT_VERTICAL" -> listOf(
                    FrameRegion(
                        name = "Speaker 1",
                        x = 0f, y = 0f,
                        width = 1f, height = 0.5f
                    ),
                    FrameRegion(
                        name = "Speaker 2",
                        x = 0f, y = 0.5f,
                        width = 1f, height = 0.5f
                    )
                )

                "SPLIT_60_40" -> listOf(
                    FrameRegion(
                        name = "Speaker 1",
                        x = 0f, y = 0f,
                        width = 1f, height = 0.6f
                    ),
                    FrameRegion(
                        name = "Speaker 2",
                        x = 0f, y = 0.6f,
                        width = 1f, height = 0.4f
                    )
                )

                "SPLIT_40_60" -> listOf(
                    FrameRegion(
                        name = "Speaker 1",
                        x = 0f, y = 0f,
                        width = 1f, height = 0.4f
                    ),
                    FrameRegion(
                        name = "Speaker 2",
                        x = 0f, y = 0.4f,
                        width = 1f, height = 0.6f
                    )
                )

                "SPLIT_HORIZONTAL" -> listOf(
                    FrameRegion(
                        name = "Speaker 1",
                        x = 0f, y = 0f,
                        width = 0.5f, height = 1f
                    ),
                    FrameRegion(
                        name = "Speaker 2",
                        x = 0.5f, y = 0f,
                        width = 0.5f, height = 1f
                    )
                )

                "SPLIT_DIAGONAL" -> listOf(
                    FrameRegion(
                        name = "Speaker 1",
                        x = 0f, y = 0f,
                        width = 1f, height = 0.5f
                    ),
                    FrameRegion(
                        name = "Speaker 2",
                        x = 0f, y = 0.5f,
                        width = 1f, height = 0.5f
                    )
                )

                else -> clip.frames
            }

            if (layout.startsWith("SPLIT_")) {
                onFramesChange(presetFrames)
            }
            onLayoutChange(layout)
        }

        Text("Layout — Vertical 9:16", color = Color.White)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "50/50",
                selected = clip.frameLayout == "SPLIT_VERTICAL",
                onClick = { selectLayout("SPLIT_VERTICAL") },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "60/40",
                selected = clip.frameLayout == "SPLIT_60_40",
                onClick = { selectLayout("SPLIT_60_40") },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "40/60",
                selected = clip.frameLayout == "SPLIT_40_60",
                onClick = { selectLayout("SPLIT_40_60") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LayoutButton(
                label = "Side-by-Side",
                selected = clip.frameLayout == "SPLIT_HORIZONTAL",
                onClick = { selectLayout("SPLIT_HORIZONTAL") },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "Diagonal",
                selected = clip.frameLayout == "SPLIT_DIAGONAL",
                onClick = { selectLayout("SPLIT_DIAGONAL") },
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
                onClick = { selectLayout("SINGLE") },
                modifier = Modifier.weight(1f)
            )
            LayoutButton(
                label = "Custom",
                selected = clip.frameLayout == "CUSTOM",
                onClick = { selectLayout("CUSTOM") },
                modifier = Modifier.weight(1f)
            )
        }


            LayoutButton(
                label = "Custom",
                selected = clip.frameLayout == "CUSTOM",
                onClick = { onLayoutChange("CUSTOM") },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Frame layout preview",
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
                text = if (clip.frames.size < 2) {
                    "Add at least 2 frames to use a split layout."
                } else {
                    "Split layout selected. Export integration is handled separately."
                },
                color = FrameEditorAccent
            )
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
                text = "${frame.name} — Frame ${index + 1}",
                color = Color.White
            )

            Text(
                text = "Horizontal position: ${"%.2f".format(draftX)}",
                color = Color.LightGray
            )

            Slider(
                value = draftX,
                onValueChange = { value ->
                    draftX = value.coerceIn(
                        0f,
                        (1f - draftWidth).coerceAtLeast(0f)
                    )
                },
                onValueChangeFinished = {
                    commitFrameChange(
                        clip,
                        frame,
                        frame.copy(x = draftX),
                        onFramesChange
                    )
                },
                valueRange = 0f..(1f - draftWidth).coerceAtLeast(0f)
            )

            Text(
                text = "Vertical position: ${"%.2f".format(draftY)}",
                color = Color.LightGray
            )

            Slider(
                value = draftY,
                onValueChange = { value ->
                    draftY = value.coerceIn(
                        0f,
                        (1f - draftHeight).coerceAtLeast(0f)
                    )
                },
                onValueChangeFinished = {
                    commitFrameChange(
                        clip,
                        frame,
                        frame.copy(y = draftY),
                        onFramesChange
                    )
                },
                valueRange = 0f..(1f - draftHeight).coerceAtLeast(0f)
            )

            Text(
                text = "Frame width: ${"%.2f".format(draftWidth)}",
                color = Color.LightGray
            )

            Slider(
                value = draftWidth,
                onValueChange = { value ->
                    draftWidth = value.coerceIn(
                        0.10f,
                        (1f - draftX).coerceAtLeast(0.10f)
                    )
                },
                onValueChangeFinished = {
                    commitFrameChange(
                        clip,
                        frame,
                        frame.copy(
                            x = draftX,
                            width = draftWidth,
                            y = draftY,
                            height = draftHeight
                        ),
                        onFramesChange
                    )
                },
                valueRange = 0.10f..(1f - draftX).coerceAtLeast(0.10f)
            )

            Text(
                text = "Frame height: ${"%.2f".format(draftHeight)}",
                color = Color.LightGray
            )

            Slider(
                value = draftHeight,
                onValueChange = { value ->
                    draftHeight = value.coerceIn(
                        0.10f,
                        (1f - draftY).coerceAtLeast(0.10f)
                    )
                },
                onValueChangeFinished = {
                    commitFrameChange(
                        clip,
                        frame,
                        frame.copy(
                            x = draftX,
                            y = draftY,
                            width = draftWidth,
                            height = draftHeight
                        ),
                        onFramesChange
                    )
                },
                valueRange = 0.10f..(1f - draftY).coerceAtLeast(0.10f)
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

private fun commitFrameChange(
    clip: ClipSegment,
    oldFrame: FrameRegion,
    newFrame: FrameRegion,
    onFramesChange: (List<FrameRegion>) -> Unit
) {
    if (oldFrame == newFrame) return

    onFramesChange(
        clip.frames.map { frame ->
            if (frame.id == oldFrame.id) newFrame else frame
        }
    )
}
