
package com.brodari.clipka

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PreviewFrameColors = listOf(
    Color(0xFFB7A9FF),
    Color(0xFF70D6B5),
    Color(0xFFFFB86B),
    Color(0xFF71C9F8)
)

/**
 * Draws editable frame guides over a preview container.
 * Coordinates are normalized from 0.0 to 1.0.
 */
@Composable
fun FramePreviewOverlay(
    frames: List<FrameRegion>,
    modifier: Modifier = Modifier
) {
    if (frames.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        frames.forEachIndexed { index, frame ->
            val frameColor =
                PreviewFrameColors[index % PreviewFrameColors.size]

            Box(
                modifier = Modifier
                    .offset(
                        x = containerWidth * frame.x,
                        y = containerHeight * frame.y
                    )
                    .width(containerWidth * frame.width)
                    .height(containerHeight * frame.height)
                    .border(
                        width = 2.dp,
                        color = frameColor,
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Text(
                    text = frame.name,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .border(
                            width = 1.dp,
                            color = frameColor,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}
