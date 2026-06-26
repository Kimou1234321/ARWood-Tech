package com.algerianwood.app.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.algerianwood.app.ui.theme.ForestGreenLight
import com.algerianwood.app.ui.theme.GoldenAmber
import com.algerianwood.app.ui.theme.WoodBrownDark
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AnalysisRefresh {

    private const val KEEP_LOG_WHITE_FRACTION = 0.10f

    data class RefreshResult(
        val finalMaskBitmap: Bitmap,
        val updatedLogs: List<LogDetector.DetectedLog>,
        val keptYoloDetections: List<LogDetector.YoloDetection>,
        val yoloAnnotatedBitmap: Bitmap,
        val volumeResult: VolumeCalculator.VolumeResult
    )

    fun refresh(
        baseMaskBitmap: Bitmap,
        erasureMaskBitmap: Bitmap,
        manualLogs: List<ManualLogCircle>,
        baseLogs: List<LogDetector.DetectedLog>,
        baseYoloDetections: List<LogDetector.YoloDetection>,
        originalBitmap: Bitmap,
        pixelPerCm: Float,
        logLengthMeters: Float
    ): RefreshResult {
        val width = baseMaskBitmap.width
        val height = baseMaskBitmap.height
        val basePixels = IntArray(width * height)
        val erasurePixels = IntArray(width * height)
        baseMaskBitmap.getPixels(basePixels, 0, width, 0, 0, width, height)
        erasureMaskBitmap.getPixels(erasurePixels, 0, width, 0, 0, width, height)

        val finalPixels = IntArray(width * height)
        for (i in finalPixels.indices) {
            val baseWhite = isWhite(basePixels[i])
            val erased = isWhite(erasurePixels[i])
            finalPixels[i] = when {
                baseWhite && !erased -> Color.WHITE
                else -> Color.BLACK
            }
        }

        for (manual in manualLogs) {
            stampCircle(finalPixels, width, height, manual.centerX, manual.centerY, manual.radiusPx, Color.WHITE)
        }

        val keptLogs = mutableListOf<LogDetector.DetectedLog>()
        val keptYolo = mutableListOf<LogDetector.YoloDetection>()

        baseLogs.forEachIndexed { index, log ->
            val whiteCount = countWhiteInDisk(
                finalPixels, width, height,
                log.centerX.toFloat(), log.centerY.toFloat(), log.radiusPixels
            )
            val expected = (PI * log.radiusPixels * log.radiusPixels).roundToInt().coerceAtLeast(1)
            if (whiteCount >= expected * KEEP_LOG_WHITE_FRACTION) {
                val area = whiteCount.coerceAtLeast(1)
                val radius = sqrt(area / PI.toFloat()).coerceAtLeast(4f)
                keptLogs.add(
                    log.copy(
                        radiusPixels = radius,
                        areaPixels = area,
                        semiMajor = radius,
                        semiMinor = radius
                    )
                )
                if (index < baseYoloDetections.size) {
                    keptYolo.add(baseYoloDetections[index])
                }
            }
        }

        for (manual in manualLogs) {
            val area = (PI * manual.radiusPx * manual.radiusPx).roundToInt().coerceAtLeast(1)
            val radius = manual.radiusPx.coerceAtLeast(4f)
            keptLogs.add(
                LogDetector.DetectedLog(
                    centerX = manual.centerX.roundToInt(),
                    centerY = manual.centerY.roundToInt(),
                    radiusPixels = radius,
                    areaPixels = area,
                    semiMajor = radius,
                    semiMinor = radius
                )
            )
        }

        val finalMaskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        finalMaskBitmap.setPixels(finalPixels, 0, width, 0, 0, width, height)

        val volumeResult = VolumeCalculator().calculateVolume(
            keptLogs, pixelPerCm, logLengthMeters
        )

        val yoloAnnotated = buildYoloBitmap(
            originalBitmap = originalBitmap,
            keptYoloDetections = keptYolo,
            manualLogs = manualLogs
        )

        return RefreshResult(
            finalMaskBitmap = finalMaskBitmap,
            updatedLogs = keptLogs,
            keptYoloDetections = keptYolo,
            yoloAnnotatedBitmap = yoloAnnotated,
            volumeResult = volumeResult
        )
    }

    fun createEmptyErasureMask(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
    }

    fun stampErasure(
        erasureMask: Bitmap,
        centerX: Float,
        centerY: Float,
        brushRadiusPx: Float
    ) {
        val pixels = IntArray(erasureMask.width * erasureMask.height)
        erasureMask.getPixels(pixels, 0, erasureMask.width, 0, 0, erasureMask.width, erasureMask.height)
        stampCircle(pixels, erasureMask.width, erasureMask.height, centerX, centerY, brushRadiusPx, Color.WHITE)
        erasureMask.setPixels(pixels, 0, erasureMask.width, 0, 0, erasureMask.width, erasureMask.height)
    }

    fun hasErasureStrokes(erasureMask: Bitmap): Boolean {
        val pixels = IntArray(erasureMask.width * erasureMask.height)
        erasureMask.getPixels(pixels, 0, erasureMask.width, 0, 0, erasureMask.width, erasureMask.height)
        return pixels.any { isWhite(it) }
    }

    fun buildPreviewMask(baseMask: Bitmap, erasureMask: Bitmap, manualLogs: List<ManualLogCircle>): Bitmap {
        val width = baseMask.width
        val height = baseMask.height
        val basePixels = IntArray(width * height)
        val erasurePixels = IntArray(width * height)
        baseMask.getPixels(basePixels, 0, width, 0, 0, width, height)
        erasureMask.getPixels(erasurePixels, 0, width, 0, 0, width, height)
        val out = IntArray(width * height)
        for (i in out.indices) {
            val baseWhite = isWhite(basePixels[i])
            val erased = isWhite(erasurePixels[i])
            out[i] = if (baseWhite && !erased) Color.WHITE else Color.BLACK
        }
        for (manual in manualLogs) {
            stampCircle(out, width, height, manual.centerX, manual.centerY, manual.radiusPx, Color.WHITE)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, width, 0, 0, width, height)
        }
    }

    private fun buildYoloBitmap(
        originalBitmap: Bitmap,
        keptYoloDetections: List<LogDetector.YoloDetection>,
        manualLogs: List<ManualLogCircle>
    ): Bitmap {
        val yoloAnnotated = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val yoloCanvas = Canvas(yoloAnnotated)
        val boxStrokeWidth = maxOf(2.5f, yoloAnnotated.width / 260f)

        val maskFillPaint = Paint().apply {
            color = ForestGreenLight.copy(alpha = 0.32f).toArgb()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val boxPaint = Paint().apply {
            color = GoldenAmber.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = boxStrokeWidth
            isAntiAlias = true
        }
        val labelBgPaint = Paint().apply {
            color = WoodBrownDark.toArgb()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val labelTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = maxOf(16f, yoloAnnotated.width / 34f)
            isAntiAlias = true
            isFakeBoldText = true
        }

        keptYoloDetections.forEach { det ->
            val l = det.left.toFloat()
            val t = det.top.toFloat()
            val r = det.right.toFloat()
            val b = det.bottom.toFloat()
            yoloCanvas.drawRect(l, t, r, b, maskFillPaint)
            yoloCanvas.drawRect(l, t, r, b, boxPaint)
            val label = "log ${String.format("%.2f", det.score)}"
            val textWidth = labelTextPaint.measureText(label)
            val textHeight = labelTextPaint.textSize
            val pad = maxOf(6f, textHeight * 0.25f)
            val bgLeft = l
            val bgTop = (t - textHeight - pad * 1.6f).coerceAtLeast(0f)
            val bgRight = (l + textWidth + pad * 1.6f).coerceAtMost(yoloAnnotated.width.toFloat())
            val bgBottom = (bgTop + textHeight + pad * 1.4f).coerceAtMost(yoloAnnotated.height.toFloat())
            yoloCanvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, labelBgPaint)
            yoloCanvas.drawText(label, bgLeft + pad, bgBottom - pad * 0.6f, labelTextPaint)
        }

        val manualFillPaint = Paint().apply {
            color = ForestGreenLight.copy(alpha = 0.35f).toArgb()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val manualStrokePaint = Paint().apply {
            color = GoldenAmber.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, yoloAnnotated.width / 300f)
            isAntiAlias = true
        }
        val manualLabelPaint = Paint().apply {
            color = Color.WHITE
            textSize = maxOf(14f, yoloAnnotated.width / 40f)
            isAntiAlias = true
            isFakeBoldText = true
        }

        manualLogs.forEach { manual ->
            yoloCanvas.drawCircle(manual.centerX, manual.centerY, manual.radiusPx, manualFillPaint)
            yoloCanvas.drawCircle(manual.centerX, manual.centerY, manual.radiusPx, manualStrokePaint)
            val label = "manual"
            yoloCanvas.drawText(
                label,
                manual.centerX - manualLabelPaint.measureText(label) / 2f,
                manual.centerY - manual.radiusPx - 8f,
                manualLabelPaint
            )
        }

        return yoloAnnotated
    }

    private fun isWhite(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 128 && g > 128 && b > 128
    }

    private fun stampCircle(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int
    ) {
        val r = radius.coerceAtLeast(0.5f)
        val rSq = r * r
        val xMin = kotlin.math.floor(cx - r).toInt().coerceAtLeast(0)
        val xMax = kotlin.math.ceil(cx + r).toInt().coerceAtMost(width - 1)
        val yMin = kotlin.math.floor(cy - r).toInt().coerceAtLeast(0)
        val yMax = kotlin.math.ceil(cy + r).toInt().coerceAtMost(height - 1)
        for (yy in yMin..yMax) {
            val row = yy * width
            for (xx in xMin..xMax) {
                val dx = (xx + 0.5f) - cx
                val dy = (yy + 0.5f) - cy
                if (dx * dx + dy * dy <= rSq) {
                    pixels[row + xx] = color
                }
            }
        }
    }

    private fun countWhiteInDisk(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        radius: Float
    ): Int {
        val r = radius.coerceAtLeast(0.5f)
        val rSq = r * r
        val xMin = kotlin.math.floor(cx - r).toInt().coerceAtLeast(0)
        val xMax = kotlin.math.ceil(cx + r).toInt().coerceAtMost(width - 1)
        val yMin = kotlin.math.floor(cy - r).toInt().coerceAtLeast(0)
        val yMax = kotlin.math.ceil(cy + r).toInt().coerceAtMost(height - 1)
        var count = 0
        for (yy in yMin..yMax) {
            val row = yy * width
            for (xx in xMin..xMax) {
                val dx = (xx + 0.5f) - cx
                val dy = (yy + 0.5f) - cy
                if (dx * dx + dy * dy <= rSq && isWhite(pixels[row + xx])) {
                    count++
                }
            }
        }
        return count
    }
}
