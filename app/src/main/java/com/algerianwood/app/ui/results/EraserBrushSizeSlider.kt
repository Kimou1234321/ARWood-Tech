package com.algerianwood.app.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.algerianwood.app.ui.theme.LocalAppPalette
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

const val ERASER_BRUSH_MIN_PX = 8f
const val ERASER_BRUSH_MAX_PX = 48f
const val ERASER_BRUSH_DEFAULT_PX = 18f

/** Image-space stamp radius: constant on-screen size = [brushRadiusPx] × baseScale at any zoom ≥ 1. */
fun eraserStampRadiusPx(brushRadiusPx: Float, zoom: Float): Float =
    (brushRadiusPx / zoom.coerceAtLeast(1f)).coerceAtLeast(1f)

/** On-screen eraser radius in canvas pixels. */
fun eraserScreenRadiusPx(brushRadiusPx: Float, zoom: Float, baseScale: Float): Float =
    eraserStampRadiusPx(brushRadiusPx, zoom) * baseScale * zoom.coerceAtLeast(1f)

private const val TRACK_INSET_FRAC = 0.08f
private val PREVIEW_GAP = 12.dp
private const val BUBBLE_PAD_PX = 8f
private val TRACK_WIDTH = 48.dp
private val TRACK_VERTICAL_PAD = 48.dp

@Composable
fun EraserBrushSizeSliderOverlay(
    visible: Boolean,
    brushRadiusPx: Float,
    zoom: Float,
    imageBaseScale: Float,
    onBrushRadiusChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || imageBaseScale <= 0f) return

    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackWidthPx = with(density) { TRACK_WIDTH.toPx() }
    val trackVerticalPadPx = with(density) { TRACK_VERTICAL_PAD.toPx() }
    val previewGapPx = with(density) { PREVIEW_GAP.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (offset.x < size.width - trackWidthPx) onDismiss()
                }
            }
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val trackInnerHeightPx = (containerHeightPx - trackVerticalPadPx * 2f).coerceAtLeast(1f)
        val canvasBrushRadiusPx = eraserScreenRadiusPx(brushRadiusPx, zoom, imageBaseScale)
        val bubblePadPx = BUBBLE_PAD_PX
        val bubbleInnerDiameterPx = canvasBrushRadiusPx * 2f + bubblePadPx * 2f
        val bubbleOuterPx = bubbleInnerDiameterPx.coerceAtLeast(with(density) { 24.dp.toPx() })
        val bubbleOuterDp = ceil(bubbleOuterPx / density.density).dp

        val thumbFraction = brushFraction(brushRadiusPx)
        val trackTop = trackInnerHeightPx * TRACK_INSET_FRAC
        val trackBottom = trackInnerHeightPx * (1f - TRACK_INSET_FRAC)
        val thumbYInTrack = trackBottom - thumbFraction * (trackBottom - trackTop)
        val thumbYInContainer = trackVerticalPadPx + thumbYInTrack
        val trackLeftPx = containerWidthPx - trackWidthPx
        val previewLeftPx = trackLeftPx - previewGapPx - bubbleOuterPx
        val previewTopPx = thumbYInContainer - bubbleOuterPx / 2f

        // Preview lives in the full-width overlay — not inside the 48dp track (avoids width clamp).
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(previewLeftPx.roundToInt(), previewTopPx.roundToInt())
                }
                .requiredSize(bubbleOuterDp)
                .clip(CircleShape)
                .background(colors.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = min(size.width, size.height) / 2f - bubblePadPx
                val previewRadius = min(canvasBrushRadiusPx, maxRadius).coerceAtLeast(1f)
                drawCircle(
                    color = palette.forestGreenLight.copy(alpha = 0.55f),
                    radius = previewRadius,
                    center = center
                )
                drawCircle(
                    color = palette.goldenAmber,
                    radius = previewRadius,
                    center = center,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = Color.White,
                    radius = min(4f, previewRadius * 0.2f),
                    center = center
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(TRACK_WIDTH)
                .padding(vertical = TRACK_VERTICAL_PAD)
                .background(colors.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                updateBrushFromY(start.y, size.height.toFloat(), onBrushRadiusChange)
                            },
                            onDragEnd = { onDismiss() },
                            onDragCancel = { onDismiss() },
                            onDrag = { change, _ ->
                                change.consume()
                                updateBrushFromY(
                                    change.position.y,
                                    size.height.toFloat(),
                                    onBrushRadiusChange
                                )
                            }
                        )
                    }
            ) {
                val centerX = size.width / 2f
                drawLine(
                    color = palette.goldenAmber.copy(alpha = 0.35f),
                    start = Offset(centerX, trackTop),
                    end = Offset(centerX, trackBottom),
                    strokeWidth = 4f
                )
                drawCircle(
                    color = palette.goldenAmber,
                    radius = 10f,
                    center = Offset(centerX, thumbYInTrack)
                )
            }
        }
    }
}

private fun brushFraction(brushRadiusPx: Float): Float =
    ((brushRadiusPx - ERASER_BRUSH_MIN_PX) / (ERASER_BRUSH_MAX_PX - ERASER_BRUSH_MIN_PX)).coerceIn(0f, 1f)

private fun updateBrushFromY(
    y: Float,
    height: Float,
    onBrushRadiusChange: (Float) -> Unit
) {
    val trackTop = height * TRACK_INSET_FRAC
    val trackBottom = height * (1f - TRACK_INSET_FRAC)
    val brushFraction = (1f - ((y - trackTop) / (trackBottom - trackTop))).coerceIn(0f, 1f)
    val radius = ERASER_BRUSH_MIN_PX + brushFraction * (ERASER_BRUSH_MAX_PX - ERASER_BRUSH_MIN_PX)
    onBrushRadiusChange(radius)
}
