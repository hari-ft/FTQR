package com.hari.ftqr.scanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    hint: String = "Point at a QR code or barcode",
) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val scanLineProgress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLine",
    )

    BoxWithConstraints(modifier = modifier) {
        val cutoutSize = min(min(maxWidth, maxHeight) * 0.68f, 300.dp)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val side = cutoutSize.toPx()
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val right = left + side
            val bottom = top + side
            val radius = 28.dp.toPx()

            drawRect(color = Color.Black.copy(alpha = 0.55f))
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
                blendMode = BlendMode.Clear,
            )

            val bracketLength = side * 0.2f
            val brackets = Path()
            fun addCorner(x: Float, y: Float, dx: Float, dy: Float) {
                brackets.moveTo(x, y + dy * bracketLength)
                brackets.lineTo(x, y + dy * radius)
                brackets.quadraticTo(x, y, x + dx * radius, y)
                brackets.lineTo(x + dx * bracketLength, y)
            }
            addCorner(left, top, 1f, 1f)
            addCorner(right, top, -1f, 1f)
            addCorner(left, bottom, 1f, -1f)
            addCorner(right, bottom, -1f, -1f)
            drawPath(
                path = brackets,
                color = Color.White,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val lineInset = 16.dp.toPx()
            val lineY = top + side * scanLineProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent, Color.Transparent),
                    startX = left + lineInset,
                    endX = right - lineInset,
                ),
                start = Offset(left + lineInset, lineY),
                end = Offset(right - lineInset, lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = cutoutSize / 2 + 36.dp)
                .padding(horizontal = 24.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
