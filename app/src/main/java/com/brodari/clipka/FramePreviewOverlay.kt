package com.brodari.clipka

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PreviewFrameColors = listOf(
    Color(0xFFB7A9FF),
    Color(0xFF70D6B5),
    Color(0xFFFFB86B),
    Color(0xFF71C9F8),
    Color(0xFFFF7597)
)

@Composable
fun FramePreviewOverlay(
    frames: List<FrameRegion>,
    modifier: Modifier = Modifier,
    sourceVideoAspectRatio: Float = 16f / 9f,
    targetAspectRatio: Float = sourceVideoAspectRatio
) {
    if (frames.isEmpty()) return

    val sourceAR = sourceVideoAspectRatio.coerceAtLeast(0.01f)
    val targetAR = targetAspectRatio.coerceAtLeast(0.01f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val visibleWidth =
            if (sourceAR > targetAR) targetAR / sourceAR else 1f

        val visibleHeight =
            if (sourceAR < targetAR) sourceAR / targetAR else 1f

        val cropOffsetX = (1f - visibleWidth) / 2f
        val cropOffsetY = (1f - visibleHeight) / 2f

        frames.forEachIndexed { index, frame ->
            val frameColor =
                PreviewFrameColors[index % PreviewFrameColors.size]

            val displayX =
                ((frame.x - cropOffsetX) / visibleWidth).coerceIn(-1f, 1f)
            val displayY =
                ((frame.y - cropOffsetY) / visibleHeight).coerceIn(-1f, 1f)

            val displayWidth =
                (frame.width / visibleWidth).coerceAtLeast(0f)
            val displayHeight =
                (frame.height / visibleHeight).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .offset(
                        x = containerWidth * displayX,
                        y = containerHeight * displayY
                    )
                    .width(containerWidth * displayWidth)
                    .height(containerHeight * displayHeight)
                    .border(
                        width = 2.dp,
                        color = frameColor,
                        shape = RoundedCornerShape(5.dp)
                    )
            ) {
                Text(
                    text = frame.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(
                                topStart = 3.dp,
                                bottomEnd = 5.dp
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = frameColor,
                            shape = RoundedCornerShape(
                                topStart = 3.dp,
                                bottomEnd = 5.dp
                            )
                        )
                )
            }
        }
    }
}
