package com.algerianwood.app.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class LogDetector(private val context: Context) {

    data class DetectedLog(
        val centerX: Int,
        val centerY: Int,
        val radiusPixels: Float,
        val areaPixels: Int,
        val semiMajor: Float = radiusPixels,
        val semiMinor: Float = radiusPixels,
        val angleDegrees: Float = 0f
    )

    data class YoloDetection(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float
    )

    data class DetectionResult(
        val logs: List<DetectedLog>,
        val maskBitmap: Bitmap,
        val yoloDetections: List<YoloDetection>,
        val tileBounds: List<TileBounds> = emptyList()
    )

    data class TileBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class Candidate(
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val score: Float, // confidence in [0,1]
        val coeffs: FloatArray
    )

    private companion object {
        private const val MODEL_FILE_NAME = "log_detector_V2.tflite"
        private const val CANONICAL_ASSET_PATH = "models/log_detector_V2.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_CANDIDATES = 8400
        private const val NUM_FEATURES = 37
        private const val MASK_COEFF_START = 5
        private const val MASK_COEFF_COUNT = 32
        private const val MASK_W = 160
        private const val MASK_H = 160
        private const val CONFIDENCE_THRESHOLD = 0.50f
        private const val IOU_THRESHOLD = 0.45f
        private const val MASK_THRESHOLD = 0.50f
        private const val MASK_THRESHOLD_RELAXED = 0.35f
        private const val MIN_RADIUS_PX = 4f

        @Volatile
        private var sharedInterpreter: Interpreter? = null
        private val interpreterLock = Any()
    }

    fun detectLogs(original: Bitmap): List<DetectedLog> {
        if (original.isRecycled || original.width <= 0 || original.height <= 0) return emptyList()
        return try {
            detectAnalysis(original).logs
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun detectAnalysis(
        original: Bitmap,
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD
    ): DetectionResult {
        if (original.isRecycled || original.width <= 0 || original.height <= 0) {
            val emptyMask = Bitmap.createBitmap(max(1, original.width), max(1, original.height), Bitmap.Config.ARGB_8888)
            return DetectionResult(emptyList(), emptyMask, emptyList())
        }

        val interpreter = getInterpreter()
        validateTensorShapes(interpreter)

        val srcBitmap = if (original.config == Bitmap.Config.ARGB_8888) original else original.copy(Bitmap.Config.ARGB_8888, false)
        val resized = Bitmap.createScaledBitmap(srcBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = buildInputBuffer(resized)

        val output0Buffer = ByteBuffer.allocateDirect(NUM_FEATURES * NUM_CANDIDATES * 4).order(ByteOrder.nativeOrder())
        val output1Buffer = ByteBuffer.allocateDirect(MASK_H * MASK_W * MASK_COEFF_COUNT * 4).order(ByteOrder.nativeOrder())

        val outputs = hashMapOf<Int, Any>(
            0 to output0Buffer,
            1 to output1Buffer
        )
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        output0Buffer.rewind()
        output1Buffer.rewind()
        val detArray = FloatArray(NUM_FEATURES * NUM_CANDIDATES)
        output0Buffer.asFloatBuffer().get(detArray)
        val protoArray = FloatArray(MASK_H * MASK_W * MASK_COEFF_COUNT)
        output1Buffer.asFloatBuffer().get(protoArray)

        val initialCandidates = parseCandidates(detArray, protoArray, confidenceThreshold)
        val nmsCandidates = runNms(initialCandidates)

        val maskPixels = IntArray(original.width * original.height) { Color.BLACK }
        val logs = mutableListOf<DetectedLog>()
        val yoloDetections = mutableListOf<YoloDetection>()
        val protoScratch = FloatArray(MASK_W * MASK_H)

        val scaleX = original.width.toFloat() / INPUT_SIZE.toFloat()
        val scaleY = original.height.toFloat() / INPUT_SIZE.toFloat()

        for (candidate in nmsCandidates) {
            val leftModel = (candidate.cx - candidate.w / 2f).coerceIn(0f, (INPUT_SIZE - 1).toFloat())
            val topModel = (candidate.cy - candidate.h / 2f).coerceIn(0f, (INPUT_SIZE - 1).toFloat())
            val rightModel = (candidate.cx + candidate.w / 2f).coerceIn(0f, (INPUT_SIZE - 1).toFloat())
            val bottomModel = (candidate.cy + candidate.h / 2f).coerceIn(0f, (INPUT_SIZE - 1).toFloat())

            val left = floor(leftModel * scaleX).toInt().coerceIn(0, original.width - 1)
            val top = floor(topModel * scaleY).toInt().coerceIn(0, original.height - 1)
            val right = ceil(rightModel * scaleX).toInt().coerceIn(left + 1, original.width)
            val bottom = ceil(bottomModel * scaleY).toInt().coerceIn(top + 1, original.height)

            val protoLeft = floor(leftModel * MASK_W / INPUT_SIZE.toFloat()).toInt().coerceIn(0, MASK_W - 1)
            val protoTop = floor(topModel * MASK_H / INPUT_SIZE.toFloat()).toInt().coerceIn(0, MASK_H - 1)
            val protoRight = ceil(rightModel * MASK_W / INPUT_SIZE.toFloat()).toInt().coerceIn(protoLeft + 1, MASK_W)
            val protoBottom = ceil(bottomModel * MASK_H / INPUT_SIZE.toFloat()).toInt().coerceIn(protoTop + 1, MASK_H)

            fillProtoSigmoidScratch(
                scratch = protoScratch,
                candidate = candidate,
                protoArray = protoArray,
                protoLeft = protoLeft,
                protoTop = protoTop,
                protoRight = protoRight,
                protoBottom = protoBottom
            )

            var painted = compositeBilinearScratchIntoMask(
                scratch = protoScratch,
                threshold = MASK_THRESHOLD,
                outputPixels = maskPixels,
                outputWidth = original.width,
                outputHeight = original.height,
                left = left,
                top = top,
                right = right,
                bottom = bottom
            )
            if (painted == 0) {
                painted = compositeBilinearScratchIntoMask(
                    scratch = protoScratch,
                    threshold = MASK_THRESHOLD_RELAXED,
                    outputPixels = maskPixels,
                    outputWidth = original.width,
                    outputHeight = original.height,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                )
            }
            if (painted == 0) {
                // Conservative fallback: keep a visible marker inside accepted detection box.
                val cx = ((left + right) / 2f).toInt()
                val cy = ((top + bottom) / 2f).toInt()
                val rr = max(2, (min(right - left, bottom - top) * 0.18f).toInt())
                for (yy in (cy - rr).coerceAtLeast(0)..(cy + rr).coerceAtMost(original.height - 1)) {
                    val rowOffset = yy * original.width
                    for (xx in (cx - rr).coerceAtLeast(0)..(cx + rr).coerceAtMost(original.width - 1)) {
                        maskPixels[rowOffset + xx] = Color.WHITE
                    }
                }
                painted = max(1, rr * rr)
            }

            val approxMaskArea = max(1, painted)
            val radius = sqrt(approxMaskArea / PI.toFloat()).coerceAtLeast(MIN_RADIUS_PX)

            logs.add(
                DetectedLog(
                    centerX = ((left + right) / 2f).toInt(),
                    centerY = ((top + bottom) / 2f).toInt(),
                    radiusPixels = radius,
                    areaPixels = approxMaskArea
                )
            )
            yoloDetections.add(
                YoloDetection(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    score = candidate.score
                )
            )
        }

        val maskBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        maskBitmap.setPixels(maskPixels, 0, original.width, 0, 0, original.width, original.height)
        return DetectionResult(logs, maskBitmap, yoloDetections)
    }

    private fun parseCandidates(
        detArray: FloatArray,
        protoArray: FloatArray,
        confidenceThreshold: Float
    ): List<Candidate> {
        val candidates = ArrayList<Candidate>(256)
        var minRawConf = Float.POSITIVE_INFINITY
        var maxRawConf = Float.NEGATIVE_INFINITY
        var normalizedSignals = 0
        var positiveProto = 0

        for (i in protoArray.indices step 32) {
            if (protoArray[i] > 0f) positiveProto++
        }

        for (i in 0 until NUM_CANDIDATES) {
            val raw = detArray[4 * NUM_CANDIDATES + i]
            if (raw < minRawConf) minRawConf = raw
            if (raw > maxRawConf) maxRawConf = raw
        }
        val useSigmoidForConfidence = minRawConf < 0f || maxRawConf > 1f

        for (i in 0 until NUM_CANDIDATES) {
            val rawConf = detArray[4 * NUM_CANDIDATES + i]
            val conf = if (useSigmoidForConfidence) sigmoid(rawConf) else rawConf
            if (conf < confidenceThreshold) continue

            var cx = detArray[i]
            var cy = detArray[NUM_CANDIDATES + i]
            var w = detArray[2 * NUM_CANDIDATES + i]
            var h = detArray[3 * NUM_CANDIDATES + i]

            val isLikelyNormalized = (w in 0f..1.5f && h in 0f..1.5f && cx in 0f..1.5f && cy in 0f..1.5f)
            if (isLikelyNormalized) {
                normalizedSignals++
                cx *= INPUT_SIZE
                cy *= INPUT_SIZE
                w *= INPUT_SIZE
                h *= INPUT_SIZE
            }
            if (w <= 0.5f || h <= 0.5f) continue

            val coeffs = FloatArray(MASK_COEFF_COUNT)
            for (k in 0 until MASK_COEFF_COUNT) {
                coeffs[k] = detArray[(MASK_COEFF_START + k) * NUM_CANDIDATES + i]
            }

            candidates.add(
                Candidate(
                    cx = cx,
                    cy = cy,
                    w = w,
                    h = h,
                    score = conf,
                    coeffs = coeffs
                )
            )
        }
        return candidates
    }

    private fun runNms(candidates: List<Candidate>): List<Candidate> {
        val sorted = candidates.sortedByDescending { it.score }
        val selected = mutableListOf<Candidate>()
        for (candidate in sorted) {
            var keep = true
            for (picked in selected) {
                if (boxIoU(candidate, picked) > IOU_THRESHOLD) {
                    keep = false
                    break
                }
            }
            if (keep) selected.add(candidate)
        }
        return selected
    }

    private fun boxIoU(a: Candidate, b: Candidate): Float {
        val aLeft = a.cx - a.w / 2f
        val aTop = a.cy - a.h / 2f
        val aRight = a.cx + a.w / 2f
        val aBottom = a.cy + a.h / 2f

        val bLeft = b.cx - b.w / 2f
        val bTop = b.cy - b.h / 2f
        val bRight = b.cx + b.w / 2f
        val bBottom = b.cy + b.h / 2f

        val interW = (min(aRight, bRight) - max(aLeft, bLeft)).coerceAtLeast(0f)
        val interH = (min(aBottom, bBottom) - max(aTop, bTop)).coerceAtLeast(0f)
        val interArea = interW * interH
        if (interArea <= 0f) return 0f

        val areaA = a.w * a.h
        val areaB = b.w * b.h
        val union = areaA + areaB - interArea
        return if (union > 0f) interArea / union else 0f
    }

    private fun buildInputBuffer(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            inputBuffer.putFloat(r / 255f)
            inputBuffer.putFloat(g / 255f)
            inputBuffer.putFloat(b / 255f)
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun validateTensorShapes(interpreter: Interpreter) {
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        require(inputShape.contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3))) {
            "Unexpected input shape: ${inputShape.joinToString(prefix = "[", postfix = "]")}"
        }
        require(inputTensor.dataType() == DataType.FLOAT32) {
            "Unexpected input type: ${inputTensor.dataType()}"
        }

        val out0Tensor = interpreter.getOutputTensor(0)
        val out0Shape = out0Tensor.shape()
        require(out0Shape.contentEquals(intArrayOf(1, NUM_FEATURES, NUM_CANDIDATES))) {
            "Unexpected output[0] shape: ${out0Shape.joinToString(prefix = "[", postfix = "]")}"
        }
        require(out0Tensor.dataType() == DataType.FLOAT32) {
            "Unexpected output[0] type: ${out0Tensor.dataType()}"
        }

        val out1Tensor = interpreter.getOutputTensor(1)
        val out1Shape = out1Tensor.shape()
        require(out1Shape.contentEquals(intArrayOf(1, MASK_H, MASK_W, MASK_COEFF_COUNT))) {
            "Unexpected output[1] shape: ${out1Shape.joinToString(prefix = "[", postfix = "]")}"
        }
        require(out1Tensor.dataType() == DataType.FLOAT32) {
            "Unexpected output[1] type: ${out1Tensor.dataType()}"
        }
    }

    private fun getInterpreter(): Interpreter {
        val existing = sharedInterpreter
        if (existing != null) return existing

        synchronized(interpreterLock) {
            val second = sharedInterpreter
            if (second != null) return second

            val options = Interpreter.Options().apply {
                setNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2))
            }
            val modelBuffer = loadModelBuffer()
            val created = Interpreter(modelBuffer, options)
            sharedInterpreter = created
            return created
        }
    }

    private fun loadModelBuffer(): ByteBuffer {
        val rawName = MODEL_FILE_NAME.substringBeforeLast(".")

        try {
            context.assets.openFd(CANONICAL_ASSET_PATH).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (_: Throwable) {
            // Compatibility fallback paths continue below.
        }

        try {
            context.assets.openFd(MODEL_FILE_NAME).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (_: Throwable) {
            // Try nested assets/model path before resource fallback.
        }

        try {
            context.assets.openFd("model/$MODEL_FILE_NAME").use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (_: Throwable) {
            // Fallback to raw resource name without extension.
        }

        val rawId = context.resources.getIdentifier(rawName, "raw", context.packageName)
        if (rawId != 0) {
            context.resources.openRawResourceFd(rawId)?.use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        }

        val modelResId = context.resources.getIdentifier(rawName, "model", context.packageName)
        if (modelResId != 0) {
            context.resources.openRawResourceFd(modelResId)?.use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        }

        throw IllegalStateException(
            "Model file $MODEL_FILE_NAME not found. Put it at app/src/main/assets/models/$MODEL_FILE_NAME (canonical)."
        )
    }

    private fun fillProtoSigmoidScratch(
        scratch: FloatArray,
        candidate: Candidate,
        protoArray: FloatArray,
        protoLeft: Int,
        protoTop: Int,
        protoRight: Int,
        protoBottom: Int,
    ) {
        scratch.fill(0f)
        for (py in protoTop until protoBottom) {
            for (px in protoLeft until protoRight) {
                var sum = 0f
                val base = (py * MASK_W + px) * MASK_COEFF_COUNT
                for (k in 0 until MASK_COEFF_COUNT) {
                    sum += candidate.coeffs[k] * protoArray[base + k]
                }
                scratch[py * MASK_W + px] = sigmoid(sum)
            }
        }
    }

    /** Sample prototype mask probabilities with bilinear interpolation (pixel centers → 160-grid), then threshold. */
    private fun compositeBilinearScratchIntoMask(
        scratch: FloatArray,
        threshold: Float,
        outputPixels: IntArray,
        outputWidth: Int,
        outputHeight: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Int {
        var painted = 0
        val ow = outputWidth.toFloat().coerceAtLeast(1f)
        val oh = outputHeight.toFloat().coerceAtLeast(1f)
        val wm = MASK_W.toFloat().coerceAtLeast(1f)
        val hm = MASK_H.toFloat().coerceAtLeast(1f)

        for (oy in top until bottom) {
            val rowOffset = oy * outputWidth
            val gy = ((oy + 0.5f) / oh).coerceIn(0f, 1f) * hm
            for (ox in left until right) {
                val gx = ((ox + 0.5f) / ow).coerceIn(0f, 1f) * wm
                val v = bilinearSample(scratch, MASK_W, MASK_H, gx, gy)
                if (v >= threshold) {
                    outputPixels[rowOffset + ox] = Color.WHITE
                    painted++
                }
            }
        }
        return painted
    }

    private fun bilinearSample(grid: FloatArray, wMask: Int, hMask: Int, gxIn: Float, gyIn: Float): Float {
        val gx = gxIn.coerceIn(0f, wMask.toFloat())
        val gy = gyIn.coerceIn(0f, hMask.toFloat())
        val x0 = floor(gx.toDouble()).toInt().coerceIn(0, wMask - 1)
        val y0 = floor(gy.toDouble()).toInt().coerceIn(0, hMask - 1)
        val x1 = min(x0 + 1, wMask - 1)
        val y1 = min(y0 + 1, hMask - 1)
        val tx = gx - x0
        val ty = gy - y0
        val v00 = grid[y0 * wMask + x0]
        val v10 = grid[y0 * wMask + x1]
        val v01 = grid[y1 * wMask + x0]
        val v11 = grid[y1 * wMask + x1]
        val a = v00 * (1f - tx) + v10 * tx
        val b = v01 * (1f - tx) + v11 * tx
        return a * (1f - ty) + b * ty
    }

    private fun sigmoid(x: Float): Float {
        return (1f / (1f + exp(-x)))
    }
}
