package com.hari.ftqr.scanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

data class ViewfinderLayout(
    val cutout: Rect,
    val showHint: Boolean,
    val hintTop: Float,
    val hintLeft: Float,
    val hintWidth: Float,
)

private val ViewfinderMargin = 16.dp
private val ViewfinderMaxSide = 300.dp
private val ViewfinderMinSideWithHint = 140.dp
private val ViewfinderCornerRadius = 28.dp
private val HintGap = 16.dp
private val HintVerticalPadding = 8.dp
private val HintLineHeight = 22.sp
private const val ViewfinderWidthFraction = 0.85f

fun Density.computeViewfinderLayout(freeArea: Rect): ViewfinderLayout? {
    val margin = ViewfinderMargin.toPx()
    val area = Rect(
        left = freeArea.left + margin,
        top = freeArea.top + margin,
        right = freeArea.right - margin,
        bottom = freeArea.bottom - margin,
    )
    if (area.width <= 0f || area.height <= 0f) return null

    val hintGap = HintGap.toPx()
    val hintReserve = hintGap + HintVerticalPadding.toPx() * 2 + HintLineHeight.toPx()
    val widthLimit = min(area.width * ViewfinderWidthFraction, ViewfinderMaxSide.toPx())
    val sideWithHint = min(widthLimit, area.height - hintReserve)
    val showHint = sideWithHint >= ViewfinderMinSideWithHint.toPx()
    val side = if (showHint) sideWithHint else min(widthLimit, area.height)
    if (side <= 0f) return null

    val blockHeight = side + if (showHint) hintReserve else 0f
    val top = area.top + (area.height - blockHeight) / 2f
    val left = area.left + (area.width - side) / 2f
    return ViewfinderLayout(
        cutout = Rect(left, top, left + side, top + side),
        showHint = showHint,
        hintTop = top + side + hintGap,
        hintLeft = area.left,
        hintWidth = area.width,
    )
}

@Composable
fun ViewfinderOverlay(
    layout: ViewfinderLayout?,
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

    if (layout == null) {
        Box(modifier = modifier)
        return
    }

    Box(modifier = modifier) {
        val cutout = layout.cutout

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val side = cutout.width
            val left = cutout.left
            val top = cutout.top
            val right = cutout.right
            val bottom = cutout.bottom
            val radius = min(ViewfinderCornerRadius.toPx(), side / 4f)

            drawRect(color = Color.Black.copy(alpha = 0.55f))
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = cutout.size,
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

            val lineInset = min(16.dp.toPx(), side / 8f)
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

        if (layout.showHint) {
            val hintWidth = with(LocalDensity.current) { layout.hintWidth.toDp() }
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .offset { IntOffset(layout.hintLeft.roundToInt(), layout.hintTop.roundToInt()) }
                    .width(hintWidth),
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = HintLineHeight),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = HintVerticalPadding),
                )
            }
        }
    }
}
