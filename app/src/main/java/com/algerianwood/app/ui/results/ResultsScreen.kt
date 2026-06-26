package com.algerianwood.app.ui.results

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algerianwood.app.processing.AnalysisRefresh
import com.algerianwood.app.processing.ManualLogCircle
import kotlinx.coroutines.launch
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.data.LocalAnalysisRepository
import com.algerianwood.app.data.SavedAnalysisSnapshot
import com.algerianwood.app.data.SavedAnalysisSummary
import com.algerianwood.app.data.SavedLogMetric
import com.algerianwood.app.processing.LogDetector
import com.algerianwood.app.processing.TiledLogDetector
import com.algerianwood.app.processing.VolumeCalculator
import com.algerianwood.app.ui.theme.LocalAppPalette
import com.algerianwood.app.ui.common.ImageLayoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ResultImageMode { ORIGINAL, MASK, YOLO }

private enum class ManualLogAction { CREATE, DELETE }

private const val MAX_UNDO_STEPS = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onNewAnalysis: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val analysisRepository = remember(context) { LocalAnalysisRepository(context) }
    val bitmap = SessionData.imageBitmap
    val pixelPerCm = SessionData.pixelPerCm
    val logLengthMeters = SessionData.logLengthMeters
    val confidenceThreshold = (SessionData.modelThresholdPercent.coerceIn(0, 100) / 100f)

    var isProcessing by remember { mutableStateOf(true) }
    var volumeResult by remember { mutableStateOf<VolumeCalculator.VolumeResult?>(null) }
    var originalImage by remember { mutableStateOf<Bitmap?>(null) }
    var maskImage by remember { mutableStateOf<Bitmap?>(null) }
    var yoloImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentMode by remember { mutableStateOf(ResultImageMode.ORIGINAL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processedTiles by remember { mutableIntStateOf(0) }
    var totalTiles by remember { mutableIntStateOf(SessionData.tilesCount) }
    var tileBounds by remember { mutableStateOf<List<LogDetector.TileBounds>>(emptyList()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveDescription by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()

    var resultZoom by remember { mutableFloatStateOf(1f) }
    var resultPanX by remember { mutableFloatStateOf(0f) }
    var resultPanY by remember { mutableFloatStateOf(0f) }

    var editState by remember { mutableStateOf<AnalysisEditState?>(null) }
    var editTool by remember { mutableStateOf(ResultsEditTool.NONE) }
    var manualLogToolActive by remember { mutableStateOf(false) }
    var manualLogAction by remember { mutableStateOf(ManualLogAction.CREATE) }
    var manualLogMenuExpanded by remember { mutableStateOf(false) }
    var manualLogs by remember { mutableStateOf<List<ManualLogCircle>>(emptyList()) }
    val undoStack = remember { mutableStateListOf<ResultsEditSnapshot>() }
    var nextManualId by remember { mutableLongStateOf(1L) }
    var maskPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasEdits by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastMaskPreviewMs by remember { mutableLongStateOf(0L) }
    var eraserBrushRadiusPx by remember { mutableFloatStateOf(ERASER_BRUSH_DEFAULT_PX) }
    var eraserBrushSliderVisible by remember { mutableStateOf(false) }
    var imageLayout by remember { mutableStateOf<ImageLayoutState?>(null) }

    BackHandler(onBack = onBack)

    fun clearUndoStack() {
        undoStack.forEach { snap ->
            if (!snap.erasureMaskCopy.isRecycled) snap.erasureMaskCopy.recycle()
        }
        undoStack.clear()
    }

    fun captureEditSnapshot(): ResultsEditSnapshot? {
        val es = editState ?: return null
        val maskCopy = es.erasureMaskBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return ResultsEditSnapshot(manualLogs.toList(), maskCopy)
    }

    fun pushUndoSnapshot() {
        val snap = captureEditSnapshot() ?: return
        if (undoStack.size >= MAX_UNDO_STEPS) {
            val removed = undoStack.removeAt(0)
            if (!removed.erasureMaskCopy.isRecycled) removed.erasureMaskCopy.recycle()
        }
        undoStack.add(snap)
    }

    fun restoreMaskPreview() {
        val es = editState ?: return
        maskPreviewBitmap?.let { if (!it.isRecycled) it.recycle() }
        maskPreviewBitmap = AnalysisRefresh.buildPreviewMask(
            es.baseMaskBitmap,
            es.erasureMaskBitmap,
            manualLogs
        )
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        val snap = undoStack.removeAt(undoStack.lastIndex)
        manualLogs = snap.manualLogs
        editState?.let { es ->
            android.graphics.Canvas(es.erasureMaskBitmap).drawBitmap(snap.erasureMaskCopy, 0f, 0f, null)
            restoreMaskPreview()
        }
        if (!snap.erasureMaskCopy.isRecycled) snap.erasureMaskCopy.recycle()
        hasEdits = undoStack.isNotEmpty() || manualLogs.isNotEmpty()
    }

    DisposableEffect(Unit) {
        onDispose {
            originalImage?.let { if (!it.isRecycled) it.recycle() }
            maskImage?.let { if (!it.isRecycled) it.recycle() }
            yoloImage?.let { if (!it.isRecycled) it.recycle() }
            maskPreviewBitmap?.let { if (!it.isRecycled) it.recycle() }
            editState?.let { state ->
                if (!state.baseMaskBitmap.isRecycled) state.baseMaskBitmap.recycle()
                if (!state.erasureMaskBitmap.isRecycled) state.erasureMaskBitmap.recycle()
            }
        }
    }

    // Run detection on a background thread
    LaunchedEffect(Unit) {
        if (bitmap == null) {
            errorMessage = "No image available"
            isProcessing = false
            return@LaunchedEffect
        }

        try {
            // Create a safe working copy on Main thread FIRST.
            // bitmap.copy() can return null and can crash natively if called
            // concurrently with Compose's Skia renderer on some devices.
            val safeBitmap: Bitmap? = try {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: Throwable) {
                null
            }

            if (safeBitmap == null) {
                errorMessage = "Failed to prepare image for analysis"
                isProcessing = false
                return@LaunchedEffect
            }

            val tilesOption = when (SessionData.tilesCount) {
                1 -> 1
                4, 9, 16 -> SessionData.tilesCount
                else -> 4
            }
            totalTiles = if (tilesOption == 1) 1 else tilesOption
            processedTiles = 0

            // All heavy work on background thread, using safeBitmap only
            val processingResult = withContext(Dispatchers.Default) {
                try {
                    val analysis = if (tilesOption == 1) {
                        LogDetector(context).detectAnalysis(
                            safeBitmap,
                            confidenceThreshold = confidenceThreshold
                        ).also {
                            mainHandler.post {
                                processedTiles = 1
                                totalTiles = 1
                            }
                        }
                    } else {
                        TiledLogDetector(context).detectAnalysisTiled(
                            safeBitmap,
                            tilesOption,
                            confidenceThreshold = confidenceThreshold
                        ) { done, total ->
                            mainHandler.post {
                                processedTiles = done
                                totalTiles = total
                            }
                        }
                    }
                    val detectedLogs = analysis.logs
                    val tileRects = analysis.tileBounds

                    // Calculate volumes
                    val calculator = VolumeCalculator()
                    val result = calculator.calculateVolume(
                        detectedLogs, pixelPerCm, logLengthMeters
                    )

                    val rawOriginal = safeBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    if (tilesOption in setOf(4, 9, 16) && tileRects.isNotEmpty()) {
                        val tileCanvas = android.graphics.Canvas(rawOriginal)
                        val segmentPaint = android.graphics.Paint().apply {
                            color = palette.woodBrownLight.toArgb()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = maxOf(2f, rawOriginal.width / 360f)
                            isAntiAlias = true
                        }
                        tileRects.forEach { b ->
                            tileCanvas.drawRect(
                                b.left.toFloat(),
                                b.top.toFloat(),
                                b.right.toFloat(),
                                b.bottom.toFloat(),
                                segmentPaint
                            )
                        }
                    }
                    val yoloAnnotated = rawOriginal.copy(Bitmap.Config.ARGB_8888, true)
                    val yoloCanvas = android.graphics.Canvas(yoloAnnotated)
                    val maskBmp = analysis.maskBitmap.copy(Bitmap.Config.ARGB_8888, true)

                    val boxStrokeWidth = maxOf(2.5f, yoloAnnotated.width / 260f)

                    val maskFillPaint = android.graphics.Paint().apply {
                        color = palette.forestGreenLight.copy(alpha = 0.32f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val boxPaint = android.graphics.Paint().apply {
                        color = palette.goldenAmber.toArgb()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = boxStrokeWidth
                        isAntiAlias = true
                    }
                    val labelBgPaint = android.graphics.Paint().apply {
                        color = palette.woodBrownDark.toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val labelTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = maxOf(16f, yoloAnnotated.width / 34f)
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    val monoTextPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = maxOf(15f, yoloAnnotated.width / 38f)
                        isAntiAlias = true
                    }

                    analysis.yoloDetections.forEach { det ->
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
                        val textY = bgBottom - pad * 0.6f
                        yoloCanvas.drawText(label, bgLeft + pad, textY, monoTextPaint)
                    }

                    val baseMaskCopy = maskBmp.copy(Bitmap.Config.ARGB_8888, true)
                    val erasureMask = AnalysisRefresh.createEmptyErasureMask(
                        baseMaskCopy.width,
                        baseMaskCopy.height
                    )

                    ProcessingPayload(
                        rawOriginal = rawOriginal,
                        maskBmp = maskBmp,
                        yoloAnnotated = yoloAnnotated,
                        volumeResult = result,
                        tileBounds = tileRects,
                        baseMaskBitmap = baseMaskCopy,
                        baseLogs = detectedLogs,
                        baseYoloDetections = analysis.yoloDetections,
                        erasureMaskBitmap = erasureMask
                    )
                } catch (e: Throwable) {
                    throw e // re-throw to be caught by outer try
                }
            }

            // Update state back on Main thread
            originalImage = processingResult.rawOriginal
            maskImage = processingResult.maskBmp
            yoloImage = processingResult.yoloAnnotated
            volumeResult = processingResult.volumeResult
            tileBounds = processingResult.tileBounds
            editState = AnalysisEditState(
                baseMaskBitmap = processingResult.baseMaskBitmap,
                baseLogs = processingResult.baseLogs,
                baseYoloDetections = processingResult.baseYoloDetections,
                erasureMaskBitmap = processingResult.erasureMaskBitmap,
                originalBitmap = processingResult.rawOriginal
            )
            manualLogs = emptyList()
            hasEdits = false
            editTool = ResultsEditTool.NONE
            manualLogToolActive = false
            clearUndoStack()
        } catch (e: Throwable) {
            errorMessage = "Detection failed: ${e.message ?: "Unknown error"}"
        } finally {
            isProcessing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis Results", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (!isProcessing && errorMessage == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { performUndo() },
                                enabled = undoStack.isNotEmpty(),
                                modifier = Modifier
                                    .alpha(if (undoStack.isNotEmpty()) 1f else 0.4f)
                                    .background(
                                        colors.surfaceVariant.copy(alpha = 0.9f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = colors.onBackground
                                )
                            }

                            val eraserEnabled = currentMode == ResultImageMode.MASK
                            IconButton(
                                onClick = {
                                    if (!eraserEnabled) return@IconButton
                                    val turningOn = editTool != ResultsEditTool.ERASER
                                    editTool = if (turningOn) ResultsEditTool.ERASER else ResultsEditTool.NONE
                                    if (turningOn) {
                                        manualLogToolActive = false
                                        lastMaskPreviewMs = 0L
                                    } else {
                                        editState?.let { es ->
                                            maskPreviewBitmap?.let { if (!it.isRecycled) it.recycle() }
                                            maskPreviewBitmap = AnalysisRefresh.buildPreviewMask(
                                                es.baseMaskBitmap,
                                                es.erasureMaskBitmap,
                                                manualLogs
                                            )
                                        }
                                    }
                                },
                                enabled = eraserEnabled,
                                modifier = Modifier
                                    .alpha(if (eraserEnabled) 1f else 0.4f)
                                    .background(
                                        if (editTool == ResultsEditTool.ERASER && eraserEnabled) {
                                            palette.goldenAmber
                                        } else {
                                            colors.surfaceVariant.copy(alpha = 0.9f)
                                        },
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Brush,
                                    contentDescription = "Eraser",
                                    tint = if (editTool == ResultsEditTool.ERASER && eraserEnabled) {
                                        colors.background
                                    } else {
                                        colors.onBackground
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (!eraserEnabled) return@IconButton
                                    eraserBrushSliderVisible = !eraserBrushSliderVisible
                                    if (eraserBrushSliderVisible && editTool != ResultsEditTool.ERASER) {
                                        editTool = ResultsEditTool.ERASER
                                        manualLogToolActive = false
                                        lastMaskPreviewMs = 0L
                                    }
                                },
                                enabled = eraserEnabled,
                                modifier = Modifier
                                    .alpha(if (eraserEnabled) 1f else 0.4f)
                                    .background(
                                        if (eraserBrushSliderVisible && eraserEnabled) {
                                            palette.goldenAmber.copy(alpha = 0.85f)
                                        } else {
                                            colors.surfaceVariant.copy(alpha = 0.9f)
                                        },
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Eraser brush size",
                                    tint = if (eraserBrushSliderVisible && eraserEnabled) {
                                        colors.background
                                    } else {
                                        colors.onBackground
                                    }
                                )
                            }

                            val manualLogEnabled = currentMode == ResultImageMode.YOLO
                            Box {
                                IconButton(
                                    onClick = {
                                        if (!manualLogEnabled) return@IconButton
                                        manualLogMenuExpanded = true
                                    },
                                    enabled = manualLogEnabled,
                                    modifier = Modifier
                                        .alpha(if (manualLogEnabled) 1f else 0.4f)
                                        .background(
                                            if (manualLogToolActive && manualLogEnabled) {
                                                palette.goldenAmber
                                            } else {
                                                colors.surfaceVariant.copy(alpha = 0.9f)
                                            },
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Filled.AddCircle,
                                        contentDescription = "Manual logs",
                                        tint = if (manualLogToolActive && manualLogEnabled) {
                                            colors.background
                                        } else {
                                            colors.onBackground
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = manualLogMenuExpanded && manualLogEnabled,
                                    onDismissRequest = { manualLogMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Create circle") },
                                        onClick = {
                                            manualLogMenuExpanded = false
                                            manualLogToolActive = true
                                            manualLogAction = ManualLogAction.CREATE
                                            editTool = ResultsEditTool.NONE
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete circle") },
                                        onClick = {
                                            manualLogMenuExpanded = false
                                            manualLogToolActive = true
                                            manualLogAction = ManualLogAction.DELETE
                                            editTool = ResultsEditTool.NONE
                                        }
                                    )
                                    if (manualLogToolActive) {
                                        DropdownMenuItem(
                                            text = { Text("Turn off") },
                                            onClick = {
                                                manualLogMenuExpanded = false
                                                manualLogToolActive = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isProcessing && errorMessage == null && volumeResult != null) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save analysis")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                    actionIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { paddingValues ->

        when {
            // ── Loading state ──
            isProcessing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = palette.forestGreenLight,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (totalTiles <= 0) 0f
                                else processedTiles.coerceIn(0, totalTiles).toFloat() / totalTiles.toFloat()
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(8.dp),
                            color = palette.forestGreenLight,
                            trackColor = colors.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing tile $processedTiles of $totalTiles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.goldenAmber
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Detecting logs...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = "AI is analyzing the image",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ── Error state ──
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = errorMessage!!, color = palette.errorRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // ── Results ──
            else -> {
                val state = editState
                val displayBmp = when (currentMode) {
                    ResultImageMode.ORIGINAL -> originalImage
                    ResultImageMode.MASK -> {
                        if (state != null && (editTool == ResultsEditTool.ERASER || hasEdits)) {
                            maskPreviewBitmap ?: maskImage
                        } else {
                            maskImage
                        }
                    }
                    ResultImageMode.YOLO -> {
                        if (manualLogs.isNotEmpty() || manualLogToolActive) {
                            originalImage
                        } else {
                            yoloImage
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    displayBmp?.let { bmp ->
                        ResultsImageViewer(
                            displayBitmap = bmp,
                            zoom = resultZoom,
                            onZoomChange = { resultZoom = it },
                            panX = resultPanX,
                            onPanXChange = { resultPanX = it },
                            panY = resultPanY,
                            onPanYChange = { resultPanY = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            editTool = when (currentMode) {
                                ResultImageMode.MASK ->
                                    if (editTool == ResultsEditTool.ERASER) ResultsEditTool.ERASER else ResultsEditTool.NONE
                                else -> ResultsEditTool.NONE
                            },
                            canPlaceManualLogs = currentMode == ResultImageMode.YOLO &&
                                manualLogToolActive &&
                                manualLogAction == ManualLogAction.CREATE,
                            canEditManualLogs = currentMode == ResultImageMode.YOLO &&
                                manualLogs.isNotEmpty(),
                            canDeleteManualLogs = currentMode == ResultImageMode.YOLO &&
                                manualLogToolActive &&
                                manualLogAction == ManualLogAction.DELETE,
                            manualLogs = manualLogs,
                            onManualLogsChange = { updated ->
                                manualLogs = updated
                                hasEdits = true
                            },
                            onBeforeEditGesture = { pushUndoSnapshot() },
                            onEraserStroke = { x, y ->
                                state?.let { es ->
                                    val brush = eraserStampRadiusPx(eraserBrushRadiusPx, resultZoom)
                                    AnalysisRefresh.stampErasure(es.erasureMaskBitmap, x, y, brush)
                                    hasEdits = true
                                    val now = System.currentTimeMillis()
                                    if (now - lastMaskPreviewMs >= 50L) {
                                        lastMaskPreviewMs = now
                                        maskPreviewBitmap?.let { if (!it.isRecycled) it.recycle() }
                                        maskPreviewBitmap = AnalysisRefresh.buildPreviewMask(
                                            es.baseMaskBitmap,
                                            es.erasureMaskBitmap,
                                            manualLogs
                                        )
                                    }
                                }
                            },
                            eraserBrushRadiusPx = eraserBrushRadiusPx,
                            onLayoutChanged = { imageLayout = it },
                            nextManualLogId = {
                                val id = nextManualId
                                nextManualId = id + 1
                                id
                            },
                            showManualLogOverlay = currentMode == ResultImageMode.YOLO && manualLogs.isNotEmpty()
                        )
                    }

                        EraserBrushSizeSliderOverlay(
                            visible = eraserBrushSliderVisible && currentMode == ResultImageMode.MASK,
                            brushRadiusPx = eraserBrushRadiusPx,
                            zoom = resultZoom,
                            imageBaseScale = imageLayout?.baseScale ?: 0f,
                            onBrushRadiusChange = { eraserBrushRadiusPx = it },
                            onDismiss = { eraserBrushSliderVisible = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    DirectModeChooser(
                        currentMode = currentMode,
                        onModeSelected = { mode ->
                            currentMode = mode
                            editTool = ResultsEditTool.NONE
                            manualLogToolActive = false
                        }
                    )

                    if (hasEdits) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val es = editState ?: return@Button
                                val orig = originalImage ?: return@Button
                                isRefreshing = true
                                scope.launch {
                                    try {
                                        val refreshResult = withContext(Dispatchers.Default) {
                                            AnalysisRefresh.refresh(
                                                baseMaskBitmap = es.baseMaskBitmap,
                                                erasureMaskBitmap = es.erasureMaskBitmap,
                                                manualLogs = manualLogs,
                                                baseLogs = es.baseLogs,
                                                baseYoloDetections = es.baseYoloDetections,
                                                originalBitmap = orig,
                                                pixelPerCm = pixelPerCm,
                                                logLengthMeters = logLengthMeters
                                            )
                                        }
                                        maskImage?.let { if (!it.isRecycled) it.recycle() }
                                        yoloImage?.let { if (!it.isRecycled) it.recycle() }
                                        maskPreviewBitmap?.let { if (!it.isRecycled) it.recycle() }

                                        maskImage = refreshResult.finalMaskBitmap
                                        yoloImage = refreshResult.yoloAnnotatedBitmap
                                        volumeResult = refreshResult.volumeResult
                                        maskPreviewBitmap = null

                                        es.erasureMaskBitmap.eraseColor(android.graphics.Color.BLACK)
                                        val newBaseMask = refreshResult.finalMaskBitmap.copy(
                                            Bitmap.Config.ARGB_8888,
                                            true
                                        )
                                        if (!es.baseMaskBitmap.isRecycled) es.baseMaskBitmap.recycle()
                                        editState = AnalysisEditState(
                                            baseMaskBitmap = newBaseMask,
                                            baseLogs = refreshResult.updatedLogs,
                                            baseYoloDetections = refreshResult.keptYoloDetections,
                                            erasureMaskBitmap = es.erasureMaskBitmap,
                                            originalBitmap = es.originalBitmap
                                        )
                                        manualLogs = emptyList()
                                        hasEdits = false
                                        editTool = ResultsEditTool.NONE
                                        manualLogToolActive = false
                                        clearUndoStack()
                                    } finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            enabled = !isRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Refresh analysis")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {

                    // Stats grid
                    volumeResult?.let { result ->
                        // Row 1: Logs + Avg Diameter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Logs Detected",
                                value = "${result.totalLogs}",
                                modifier = Modifier.weight(1f),
                                valueColor = palette.forestGreenLight
                            )
                            StatCard(
                                title = "Avg Diameter",
                                value = String.format("%.1f cm", result.averageDiameterCm),
                                modifier = Modifier.weight(1f),
                                valueColor = palette.goldenAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2: Total Area + Total Volume
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Total Area",
                                value = String.format("%.2f m²", result.totalAreaCm2 / 10000f),
                                modifier = Modifier.weight(1f),
                                valueColor = palette.woodBrownLight
                            )
                            StatCard(
                                title = "Total Volume",
                                value = String.format("%.3f m³", result.totalVolumeM3),
                                modifier = Modifier.weight(1f),
                                valueColor = palette.forestGreenLight
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Individual logs list
                        if (result.logs.isNotEmpty()) {
                            Text(
                                text = "Individual Logs",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            result.logs.forEachIndexed { index, logVolume ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colors.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Log #${index + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.forestGreenLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "⌀ ${
                                                    String.format(
                                                        "%.1f",
                                                        logVolume.diameterCm
                                                    )
                                                } cm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Vol: ${
                                                    String.format(
                                                        "%.4f",
                                                        logVolume.volumeM3
                                                    )
                                                } m³",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onBackground
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action button
                    Button(
                        onClick = onNewAnalysis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("New Analysis", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save analysis") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = saveName,
                            onValueChange = { saveName = it; saveError = null },
                            label = { Text("Analysis name") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = saveDescription,
                            onValueChange = { saveDescription = it; saveError = null },
                            label = { Text("Small description") },
                            singleLine = true
                        )
                        saveError?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = palette.errorRed)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = saveName.trim()
                            val desc = saveDescription.trim()
                            val user = SessionData.currentUsername.trim()
                            val result = volumeResult
                            val original = originalImage
                            val mask = maskImage
                            val yolo = yoloImage
                            if (name.isBlank() || desc.isBlank()) {
                                saveError = "Name and description are required"
                                return@Button
                            }
                            if (user.isBlank()) {
                                saveError = "No active user session"
                                return@Button
                            }
                            if (result == null || original == null || mask == null || yolo == null) {
                                saveError = "Analysis is not ready yet"
                                return@Button
                            }
                            val now = System.currentTimeMillis()
                            val snapshot = SavedAnalysisSnapshot(
                                summary = SavedAnalysisSummary(
                                    id = now,
                                    username = user,
                                    name = name,
                                    description = desc,
                                    savedAtMs = now
                                ),
                                totalLogs = result.totalLogs,
                                totalAreaCm2 = result.totalAreaCm2,
                                totalVolumeM3 = result.totalVolumeM3,
                                averageDiameterCm = result.averageDiameterCm,
                                logs = result.logs.mapIndexed { index, log ->
                                    SavedLogMetric(index, log.diameterCm, log.volumeM3)
                                },
                                tileBounds = tileBounds,
                                originalBitmap = original,
                                maskBitmap = mask,
                                yoloBitmap = yolo
                            )
                            val saveResult = analysisRepository.save(snapshot)
                            if (saveResult.isSuccess) {
                                Toast.makeText(context, "Analyse saved", Toast.LENGTH_SHORT).show()
                                showSaveDialog = false
                                onNewAnalysis()
                            } else {
                                saveError = saveResult.exceptionOrNull()?.message ?: "Failed to save analysis"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                    ) { Text("Save") }
                },
                dismissButton = {
                    Button(
                        onClick = { showSaveDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                    ) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAppPalette.current.forestGreenLight
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class ResultsEditSnapshot(
    val manualLogs: List<ManualLogCircle>,
    val erasureMaskCopy: Bitmap
)

private data class AnalysisEditState(
    val baseMaskBitmap: Bitmap,
    val baseLogs: List<LogDetector.DetectedLog>,
    val baseYoloDetections: List<LogDetector.YoloDetection>,
    val erasureMaskBitmap: Bitmap,
    val originalBitmap: Bitmap
)

private data class ProcessingPayload(
    val rawOriginal: Bitmap,
    val maskBmp: Bitmap,
    val yoloAnnotated: Bitmap,
    val volumeResult: VolumeCalculator.VolumeResult,
    val tileBounds: List<LogDetector.TileBounds>,
    val baseMaskBitmap: Bitmap,
    val baseLogs: List<LogDetector.DetectedLog>,
    val baseYoloDetections: List<LogDetector.YoloDetection>,
    val erasureMaskBitmap: Bitmap
)

@Composable
internal fun DirectModeChooser(
    currentMode: ResultImageMode,
    onModeSelected: (ResultImageMode) -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            ResultImageMode.ORIGINAL to "Original",
            ResultImageMode.MASK to "Mask",
            ResultImageMode.YOLO to "YOLO"
        ).forEach { (mode, label) ->
            val selected = currentMode == mode
            Button(
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) palette.goldenAmber else colors.surfaceVariant,
                    contentColor = if (selected) colors.background else colors.onBackground
                )
            ) { Text(label) }
        }
    }
}
