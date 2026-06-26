package com.algerianwood.app.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.onSizeChanged

data class ImageTransform(
    val baseScale: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val baseOffsetX: Float,
    val baseOffsetY: Float
) {
    val scale: Float get() = baseScale * zoom

    fun canvasToImage(pos: Offset): Offset {
        val s = scale
        return Offset(
            (pos.x - (baseOffsetX + panX)) / s,
            (pos.y - (baseOffsetY + panY)) / s
        )
    }

    fun imageToCanvas(pos: Offset): Offset {
        val s = scale
        return Offset(
            pos.x * s + baseOffsetX + panX,
            pos.y * s + baseOffsetY + panY
        )
    }
}

@Composable
fun ZoomableImageCanvas(
    bitmap: Bitmap,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    panX: Float,
    onPanXChange: (Float) -> Unit,
    panY: Float,
    onPanYChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    overlay: DrawScope.(ImageTransform) -> Unit = {}
) {
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var baseOffsetX by remember { mutableFloatStateOf(0f) }
    var baseOffsetY by remember { mutableFloatStateOf(0f) }
    val letterboxColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .clipToBounds()
            .onSizeChanged { intSize ->
                canvasWidth = intSize.width.toFloat()
                canvasHeight = intSize.height.toFloat()
                baseScale = minOf(
                    intSize.width.toFloat() / bitmap.width,
                    intSize.height.toFloat() / bitmap.height
                )
                baseOffsetX = (intSize.width - bitmap.width * baseScale) / 2f
                baseOffsetY = (intSize.height - bitmap.height * baseScale) / 2f
            }
            .pointerInput(bitmap.width, bitmap.height, baseScale, baseOffsetX, baseOffsetY, zoom, panX, panY) {
                val currentLayout = ImageLayoutState(
                    canvasWidth, canvasHeight, baseScale, baseOffsetX, baseOffsetY
                )
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        if (applyPinchZoom(
                                event, bitmap.width, bitmap.height, currentLayout,
                                zoom, panX, panY, onZoomChange
                            ) { x, y ->
                                onPanXChange(x)
                                onPanYChange(y)
                            }
                        ) {
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size == 1) {
                            applySingleFingerPan(
                                pressed.first(), bitmap.width, bitmap.height, currentLayout,
                                zoom, panX, panY
                            ) { x, y ->
                                onPanXChange(x)
                                onPanYChange(y)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val bmpImage = bitmap.asImageBitmap()
        drawLetterboxedBitmap(
            bitmap = bmpImage,
            layout = ImageLayoutState(canvasWidth, canvasHeight, baseScale, baseOffsetX, baseOffsetY),
            zoom = zoom,
            panX = panX,
            panY = panY,
            letterboxColor = letterboxColor
        )
        overlay(
            ImageTransform(baseScale, zoom, panX, panY, baseOffsetX, baseOffsetY)
        )
    }
}
