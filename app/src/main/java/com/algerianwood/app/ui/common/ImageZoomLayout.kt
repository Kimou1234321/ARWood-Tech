package com.algerianwood.app.ui.common

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEvent
data class ImageLayoutState(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val baseScale: Float,
    val baseOffsetX: Float,
    val baseOffsetY: Float
) {
    fun viewportRect(bitmapWidth: Int, bitmapHeight: Int): Rect = Rect(
        left = baseOffsetX,
        top = baseOffsetY,
        right = baseOffsetX + bitmapWidth * baseScale,
        bottom = baseOffsetY + bitmapHeight * baseScale
    )

    fun clampPan(zoom: Float, panX: Float, panY: Float, bitmapWidth: Int, bitmapHeight: Int): Offset {
        val viewportW = bitmapWidth * baseScale
        val viewportH = bitmapHeight * baseScale
        val shownW = viewportW * zoom
        val shownH = viewportH * zoom
        val minPanX = minOf(0f, viewportW - shownW)
        val minPanY = minOf(0f, viewportH - shownH)
        return Offset(
            panX.coerceIn(minPanX, 0f),
            panY.coerceIn(minPanY, 0f)
        )
    }

    fun imageDrawRect(zoom: Float, panX: Float, panY: Float, bitmapWidth: Int, bitmapHeight: Int): androidx.compose.ui.geometry.Rect {
        val scale = baseScale * zoom
        val offX = baseOffsetX + panX
        val offY = baseOffsetY + panY
        return androidx.compose.ui.geometry.Rect(
            offX,
            offY,
            offX + bitmapWidth * scale,
            offY + bitmapHeight * scale
        )
    }
}

fun DrawScope.drawLetterboxedBitmap(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    layout: ImageLayoutState,
    zoom: Float,
    panX: Float,
    panY: Float,
    letterboxColor: Color
) {
    drawRect(color = letterboxColor, size = Size(size.width, size.height))
    val viewport = layout.viewportRect(bitmap.width, bitmap.height)
    val scale = layout.baseScale * zoom
    val offX = layout.baseOffsetX + panX
    val offY = layout.baseOffsetY + panY
    clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
        translate(offX, offY) {
            scale(scale, scale, pivot = Offset.Zero) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(bitmap.width, bitmap.height)
                )
            }
        }
    }
}

fun PointerInputScope.applyPinchZoom(
    event: PointerEvent,
    bitmapWidth: Int,
    bitmapHeight: Int,
    layout: ImageLayoutState,
    zoom: Float,
    panX: Float,
    panY: Float,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit
): Boolean {
    val pressed = event.changes.filter { it.pressed }
    if (pressed.size < 2 || layout.baseScale <= 0f) return false

    val zoomChange = event.calculateZoom()
    val panChange = event.calculatePan()
    val centroid = event.calculateCentroid(useCurrent = true)
    val oldZoom = zoom
    val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
    val focusX = (centroid.x - (layout.baseOffsetX + panX)) / (layout.baseScale * oldZoom)
    val focusY = (centroid.y - (layout.baseOffsetY + panY)) / (layout.baseScale * oldZoom)
    onZoomChange(newZoom)
    val rawPanX = centroid.x - layout.baseOffsetX - (focusX * layout.baseScale * newZoom) + panChange.x
    val rawPanY = centroid.y - layout.baseOffsetY - (focusY * layout.baseScale * newZoom) + panChange.y
    val clamped = layout.clampPan(newZoom, rawPanX, rawPanY, bitmapWidth, bitmapHeight)
    onPanChange(clamped.x, clamped.y)
    pressed.forEach { it.consume() }
    return true
}

fun PointerInputScope.applySingleFingerPan(
    change: PointerInputChange,
    bitmapWidth: Int,
    bitmapHeight: Int,
    layout: ImageLayoutState,
    zoom: Float,
    panX: Float,
    panY: Float,
    onPanChange: (Float, Float) -> Unit
): Boolean {
    if (zoom <= 1f) return false
    val delta = change.position - change.previousPosition
    if (delta == Offset.Zero) return false
    val clamped = layout.clampPan(zoom, panX + delta.x, panY + delta.y, bitmapWidth, bitmapHeight)
    onPanChange(clamped.x, clamped.y)
    change.consume()
    return true
}
