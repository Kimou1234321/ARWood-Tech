package com.algerianwood.app.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.algerianwood.app.ui.theme.DarkForestCard
import com.algerianwood.app.ui.theme.ForestGreenLight
import com.algerianwood.app.ui.theme.GoldenAmber
import kotlin.math.max
import kotlin.math.roundToInt

const val TOUCH_TARGET_RADIUS_PX = 48f
const val MAGNIFIER_WINDOW_PX = 320f
const val MAGNIFIER_SAMPLE_PX = 56
private const val MAGNIFIER_CIRCLE_SAMPLE_PAD_PX = 24
private const val MAGNIFIER_BORDER_PX = 2f
private const val MAGNIFIER_IMAGE_PAD_PX = 6f
private const val MAGNIFIER_ANCHOR_GAP_PX = 28f

/**
 * Draws a floating loupe for precision dragging. [previewImage] is in bitmap pixel coords;
 * [anchorCanvas] is where the finger/circle appears on screen (window is placed beside it).
 */
fun DrawScope.drawDragMagnifier(
    bitmap: ImageBitmap,
    previewImage: Offset,
    anchorCanvas: Offset,
    canvasSize: Size,
    imageWidth: Int,
    imageHeight: Int,
    windowSizePx: Float = MAGNIFIER_WINDOW_PX,
    sampleSizePx: Int = MAGNIFIER_SAMPLE_PX,
    preferLeftOfAnchor: Boolean = true
) {
    val leftX = anchorCanvas.x - windowSizePx - MAGNIFIER_ANCHOR_GAP_PX
    val windowX = if (preferLeftOfAnchor && leftX >= 8f) {
        leftX
    } else {
        (anchorCanvas.x + MAGNIFIER_ANCHOR_GAP_PX).coerceAtMost(canvasSize.width - windowSizePx - 8f)
    }
    val windowY = (anchorCanvas.y - windowSizePx / 2f)
        .coerceIn(8f, canvasSize.height - windowSizePx - 8f)

    val sampleSize = minOf(sampleSizePx, imageWidth, imageHeight).coerceAtLeast(8)
    val halfSample = sampleSize / 2f
    val srcLeft = (previewImage.x - halfSample)
        .coerceIn(0f, (imageWidth - sampleSize).toFloat())
        .roundToInt()
    val srcTop = (previewImage.y - halfSample)
        .coerceIn(0f, (imageHeight - sampleSize).toFloat())
        .roundToInt()

    drawRoundRect(
        color = DarkForestCard.copy(alpha = 0.92f),
        topLeft = Offset(windowX, windowY),
        size = Size(windowSizePx, windowSizePx),
        cornerRadius = CornerRadius(14f, 14f)
    )

    val innerSize = (windowSizePx - MAGNIFIER_IMAGE_PAD_PX * 2f).roundToInt()
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(sampleSize, sampleSize),
        dstOffset = IntOffset(
            (windowX + MAGNIFIER_IMAGE_PAD_PX).roundToInt(),
            (windowY + MAGNIFIER_IMAGE_PAD_PX).roundToInt()
        ),
        dstSize = IntSize(innerSize, innerSize)
    )

    drawRoundRect(
        color = GoldenAmber,
        topLeft = Offset(windowX, windowY),
        size = Size(windowSizePx, windowSizePx),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = MAGNIFIER_BORDER_PX)
    )

    val center = Offset(windowX + windowSizePx / 2f, windowY + windowSizePx / 2f)
    drawLine(
        color = GoldenAmber,
        start = Offset(center.x - 12f, center.y),
        end = Offset(center.x + 12f, center.y),
        strokeWidth = 1.8f
    )
    drawLine(
        color = GoldenAmber,
        start = Offset(center.x, center.y - 12f),
        end = Offset(center.x, center.y + 12f),
        strokeWidth = 1.8f
    )
}

/**
 * Loupe for manual-log circles: samples around [previewCenter], scales sample to fit [radiusPx],
 * and draws the live circle overlay inside the window.
 */
fun DrawScope.drawCircleMagnifier(
    bitmap: ImageBitmap,
    previewCenter: Offset,
    radiusPx: Float,
    anchorCanvas: Offset,
    canvasSize: Size,
    imageWidth: Int,
    imageHeight: Int,
    windowSizePx: Float = MAGNIFIER_WINDOW_PX,
    preferLeftOfAnchor: Boolean = true
) {
    val sampleSizePx = minOf(
        imageWidth,
        imageHeight,
        max(MAGNIFIER_SAMPLE_PX, (2f * radiusPx + MAGNIFIER_CIRCLE_SAMPLE_PAD_PX).roundToInt())
    ).coerceAtLeast(8)

    val leftX = anchorCanvas.x - windowSizePx - MAGNIFIER_ANCHOR_GAP_PX
    val windowX = if (preferLeftOfAnchor && leftX >= 8f) {
        leftX
    } else {
        (anchorCanvas.x + MAGNIFIER_ANCHOR_GAP_PX).coerceAtMost(canvasSize.width - windowSizePx - 8f)
    }
    val windowY = (anchorCanvas.y - windowSizePx / 2f)
        .coerceIn(8f, canvasSize.height - windowSizePx - 8f)

    val sampleSize = sampleSizePx
    val halfSample = sampleSize / 2f
    val srcLeft = (previewCenter.x - halfSample)
        .coerceIn(0f, (imageWidth - sampleSize).toFloat())
        .roundToInt()
    val srcTop = (previewCenter.y - halfSample)
        .coerceIn(0f, (imageHeight - sampleSize).toFloat())
        .roundToInt()

    drawRoundRect(
        color = DarkForestCard.copy(alpha = 0.92f),
        topLeft = Offset(windowX, windowY),
        size = Size(windowSizePx, windowSizePx),
        cornerRadius = CornerRadius(14f, 14f)
    )

    val innerSize = (windowSizePx - MAGNIFIER_IMAGE_PAD_PX * 2f).roundToInt()
    val imageDstX = (windowX + MAGNIFIER_IMAGE_PAD_PX).roundToInt()
    val imageDstY = (windowY + MAGNIFIER_IMAGE_PAD_PX).roundToInt()
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(sampleSize, sampleSize),
        dstOffset = IntOffset(imageDstX, imageDstY),
        dstSize = IntSize(innerSize, innerSize)
    )

    val loupeCenter = Offset(
        imageDstX + innerSize / 2f,
        imageDstY + innerSize / 2f
    )
    val radiusInLoupe = radiusPx * (innerSize / sampleSize.toFloat())
    drawCircle(
        color = ForestGreenLight.copy(alpha = 0.35f),
        radius = radiusInLoupe,
        center = loupeCenter
    )
    drawCircle(
        color = GoldenAmber,
        radius = radiusInLoupe,
        center = loupeCenter,
        style = Stroke(width = max(2f, 2f * radiusInLoupe / radiusPx.coerceAtLeast(1f)))
    )
    drawCircle(color = Color.White, radius = 5f, center = loupeCenter)

    drawRoundRect(
        color = GoldenAmber,
        topLeft = Offset(windowX, windowY),
        size = Size(windowSizePx, windowSizePx),
        cornerRadius = CornerRadius(14f, 14f),
        style = Stroke(width = MAGNIFIER_BORDER_PX)
    )
}
