package com.algerianwood.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO-style letterbox preprocessing: scale uniformly to fit inside [target], pad with black.
 *
 * Mapping:
 * - model space: [0..target) with padding at (padX, padY)
 * - original pixel (x,y):
 *     x = (xModel - padX) / scale
 *     y = (yModel - padY) / scale
 */
internal data class LetterboxTransform(
    val origW: Int,
    val origH: Int,
    val target: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

internal fun buildLetterboxBitmap(src: Bitmap, target: Int): Pair<Bitmap, LetterboxTransform> {
    require(target > 0)
    require(src.width > 0 && src.height > 0)

    val scale = min(target / src.width.toFloat(), target / src.height.toFloat())
    val newW = max(1, floor(src.width * scale + 1e-3f).toInt())
    val newH = max(1, floor(src.height * scale + 1e-3f).toInt())
    val padX = (target - newW) / 2f
    val padY = (target - newH) / 2f

    val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawARGB(255, 0, 0, 0)

    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
    c.drawBitmap(resized, padX, padY, paint)
    // `resized` is a throwaway intermediate; recycle to avoid repeated allocation pressure.
    if (!resized.isRecycled && resized !== src) resized.recycle()

    val tx = LetterboxTransform(
        origW = src.width,
        origH = src.height,
        target = target,
        scale = scale,
        padX = padX,
        padY = padY
    )
    return out to tx
}

internal fun LetterboxTransform.modelToOriginalX(xModel: Float): Float = (xModel - padX) / scale
internal fun LetterboxTransform.modelToOriginalY(yModel: Float): Float = (yModel - padY) / scale

internal fun LetterboxTransform.originalToModelX(xOrig: Float): Float = xOrig * scale + padX
internal fun LetterboxTransform.originalToModelY(yOrig: Float): Float = yOrig * scale + padY
