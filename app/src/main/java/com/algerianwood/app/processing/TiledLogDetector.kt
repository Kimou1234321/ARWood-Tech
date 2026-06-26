package com.algerianwood.app.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Runs [LogDetector.detectAnalysis] on an N×N grid of overlapping crops, merges masks,
 * offsets boxes/logs to full-image coordinates, then applies cross-tile NMS on YOLO boxes.
 *
 * Overlap: adjacent segments share a band equals 30% of segment width (height); each pad is 15%
 * inward from each boundary.
 * Merge policy by coverage-count:
 * - coverCount = 1: keep that tile prediction
 * - coverCount = 2: require unanimity (both white)
 * - coverCount >= 3: strict majority (tie-to-black)
 */
class TiledLogDetector(private val context: Context) {

    fun interface ProgressCallback {
        fun onProgress(done: Int, total: Int)
    }

    fun detectAnalysisTiled(
        original: Bitmap,
        tilesCount: Int,
        confidenceThreshold: Float = 0.50f,
        progress: ProgressCallback? = null,
    ): LogDetector.DetectionResult {
        require(tilesCount in TILE_OPTIONS) { "tilesCount must be 4, 9, or 16" }
        if (original.isRecycled || original.width <= 0 || original.height <= 0) {
            val empty = Bitmap.createBitmap(max(1, original.width), max(1, original.height), Bitmap.Config.ARGB_8888)
            return LogDetector.DetectionResult(emptyList(), empty, emptyList())
        }

        val n = sqrt(tilesCount.toDouble()).toInt()
        val W = original.width
        val H = original.height

        val segW = max(1, W / n)
        val segH = max(1, H / n)
        val padX = max(1, (segW * SHARED_OVERLAP_SIDE_FRAC).roundToInt())
        val padY = max(1, (segH * SHARED_OVERLAP_SIDE_FRAC).roundToInt())

        val srcBitmap =
            if (original.config == Bitmap.Config.ARGB_8888) original else original.copy(Bitmap.Config.ARGB_8888, false)

        val detector = LogDetector(context)
        val tileSpecs = mutableListOf<TileSpec>()
        val segmentBounds = mutableListOf<LogDetector.TileBounds>()
        var maxTilePixels = 0

        for (row in 0 until n) {
            val segTop = row * H / n
            val segBottom = (row + 1) * H / n
            val tileTop = max(0, segTop - padY)
            val tileBottom = min(H, segBottom + padY)
            val th = max(1, tileBottom - tileTop)
            for (col in 0 until n) {
                val segLeft = col * W / n
                val segRight = (col + 1) * W / n
                segmentBounds.add(
                    LogDetector.TileBounds(
                        left = segLeft,
                        top = segTop,
                        right = segRight,
                        bottom = segBottom
                    )
                )
                val tileLeft = max(0, segLeft - padX)
                val tileRight = min(W, segRight + padX)
                val tw = max(1, tileRight - tileLeft)
                maxTilePixels = max(maxTilePixels, tw * th)
                tileSpecs.add(TileSpec(tileLeft, tileTop, tw, th))
            }
        }

        val coverCount = ByteArray(W * H)
        val whiteVotes = ByteArray(W * H)
        val pixelBuf = IntArray(maxTilePixels)

        val allLogs = mutableListOf<LogDetector.DetectedLog>()
        val allYolo = mutableListOf<LogDetector.YoloDetection>()

        val totalTiles = tileSpecs.size
        var done = 0

        for (spec in tileSpecs) {
            val tileBmp = Bitmap.createBitmap(srcBitmap, spec.left, spec.top, spec.width, spec.height)
            try {
                val analysis = detector.detectAnalysis(tileBmp, confidenceThreshold = confidenceThreshold)
                for (log in analysis.logs) {
                    allLogs.add(
                        LogDetector.DetectedLog(
                            centerX = log.centerX + spec.left,
                            centerY = log.centerY + spec.top,
                            radiusPixels = log.radiusPixels,
                            areaPixels = log.areaPixels,
                            semiMajor = log.semiMajor,
                            semiMinor = log.semiMinor,
                            angleDegrees = log.angleDegrees
                        )
                    )
                }
                for (det in analysis.yoloDetections) {
                    allYolo.add(
                        LogDetector.YoloDetection(
                            left = det.left + spec.left,
                            top = det.top + spec.top,
                            right = det.right + spec.left,
                            bottom = det.bottom + spec.top,
                            score = det.score
                        )
                    )
                }
                accumulateTileMaskVotes(
                    coverCount,
                    whiteVotes,
                    pixelBuf,
                    W,
                    H,
                    analysis.maskBitmap,
                    spec.left,
                    spec.top
                )
            } finally {
                if (!tileBmp.isRecycled) tileBmp.recycle()
            }
            done++
            progress?.onProgress(done, totalTiles)
        }

        if (srcBitmap !== original && !srcBitmap.isRecycled) srcBitmap.recycle()

        val mergedPixels = IntArray(W * H)
        for (i in 0 until W * H) {
            val c = coverCount[i].toInt() and 0xFF
            val w = whiteVotes[i].toInt() and 0xFF
            val isWhite = when {
                c == 1 -> w >= 1
                c == 2 -> w == 2
                c >= 3 -> w * 2 > c
                else -> false
            }
            mergedPixels[i] = if (isWhite) Color.WHITE else Color.BLACK
        }

        val mergedMask = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        mergedMask.setPixels(mergedPixels, 0, W, 0, 0, W, H)

        val (keptLogs, keptYolo) = crossTileNms(allLogs, allYolo, CROSS_TILE_IOU)

        return LogDetector.DetectionResult(
            logs = keptLogs,
            maskBitmap = mergedMask,
            yoloDetections = keptYolo,
            tileBounds = segmentBounds
        )
    }

    private data class TileSpec(val left: Int, val top: Int, val width: Int, val height: Int)

    /** Bumps coverage and agreement counts; white iff all covering voters agree (built in final composite). */
    private fun accumulateTileMaskVotes(
        coverCount: ByteArray,
        whiteVotes: ByteArray,
        pixelBuf: IntArray,
        W: Int,
        H: Int,
        tileMask: Bitmap,
        offsetX: Int,
        offsetY: Int,
    ) {
        val tw = tileMask.width
        val th = tileMask.height
        val need = tw * th
        require(pixelBuf.size >= need) { "pixelBuf too small ($need)" }
        tileMask.getPixels(pixelBuf, 0, tw, 0, 0, tw, th)
        for (ty in 0 until th) {
            val oy = offsetY + ty
            if (oy !in 0 until H) continue
            val rowOff = oy * W
            val srcRow = ty * tw
            for (tx in 0 until tw) {
                val ox = offsetX + tx
                if (ox !in 0 until W) continue
                val idx = rowOff + ox
                val newCover = ((coverCount[idx].toInt() and 0xFF) + 1).coerceAtMost(255).toByte()
                coverCount[idx] = newCover
                val p = pixelBuf[srcRow + tx]
                if ((p and 0xFFFFFF) != 0 && Color.alpha(p) > 0) {
                    whiteVotes[idx] = ((whiteVotes[idx].toInt() and 0xFF) + 1).coerceAtMost(255).toByte()
                }
            }
        }
    }

    private fun crossTileNms(
        logs: List<LogDetector.DetectedLog>,
        yolo: List<LogDetector.YoloDetection>,
        iouThreshold: Float,
    ): Pair<List<LogDetector.DetectedLog>, List<LogDetector.YoloDetection>> {
        if (logs.size != yolo.size || logs.isEmpty()) return logs to yolo
        val order = yolo.indices.sortedByDescending { yolo[it].score }
        val selected = mutableListOf<Int>()
        for (idx in order) {
            var keep = true
            for (kept in selected) {
                if (boxIouYolo(yolo[idx], yolo[kept]) > iouThreshold) {
                    keep = false
                    break
                }
            }
            if (keep) selected.add(idx)
        }
        val keptLogs = selected.map { logs[it] }
        val keptYolo = selected.map { yolo[it] }
        return keptLogs to keptYolo
    }

    private fun boxIouYolo(a: LogDetector.YoloDetection, b: LogDetector.YoloDetection): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val iw = (interRight - interLeft).coerceAtLeast(0)
        val ih = (interBottom - interTop).coerceAtLeast(0)
        val inter = iw.toLong() * ih.toLong()
        if (inter <= 0L) return 0f
        val areaA = (a.right - a.left).toLong() * (a.bottom - a.top).toLong()
        val areaB = (b.right - b.left).toLong() * (b.bottom - b.top).toLong()
        val union = areaA + areaB - inter
        return if (union > 0L) inter.toFloat() / union.toFloat() else 0f
    }

    companion object {
        private val TILE_OPTIONS = setOf(4, 9, 16)
        private const val CROSS_TILE_IOU = 0.45f

        /** 15% inward per adjacent segment side => shared band equals 30% of segment dimension. */
        private const val SHARED_OVERLAP_SIDE_FRAC = 0.15f
    }
}
