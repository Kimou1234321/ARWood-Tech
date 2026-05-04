package com.algerianwood.app.ui.results

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.processing.LogDetector
import com.algerianwood.app.processing.VolumeCalculator
import com.algerianwood.app.ui.theme.DarkForest
import com.algerianwood.app.ui.theme.DarkForestCard
import com.algerianwood.app.ui.theme.ErrorRed
import com.algerianwood.app.ui.theme.ForestGreen
import com.algerianwood.app.ui.theme.ForestGreenLight
import com.algerianwood.app.ui.theme.GoldenAmber
import com.algerianwood.app.ui.theme.TextPrimary
import com.algerianwood.app.ui.theme.TextSecondary
import com.algerianwood.app.ui.theme.WoodBrownDark
import com.algerianwood.app.ui.theme.WoodBrownLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ResultImageMode { ORIGINAL, MASK, YOLO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onNewAnalysis: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = SessionData.imageBitmap
    val pixelPerCm = SessionData.pixelPerCm
    val logLengthMeters = SessionData.logLengthMeters

    var isProcessing by remember { mutableStateOf(true) }
    var volumeResult by remember { mutableStateOf<VolumeCalculator.VolumeResult?>(null) }
    var originalImage by remember { mutableStateOf<Bitmap?>(null) }
    var maskImage by remember { mutableStateOf<Bitmap?>(null) }
    var yoloImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentMode by remember { mutableStateOf(ResultImageMode.ORIGINAL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            originalImage?.let { if (!it.isRecycled) it.recycle() }
            maskImage?.let { if (!it.isRecycled) it.recycle() }
            yoloImage?.let { if (!it.isRecycled) it.recycle() }
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

            // All heavy work on background thread, using safeBitmap only
            val processingResult = withContext(Dispatchers.Default) {
                try {
                    // Detect logs using YOLOv8 TFLite and build display variants.
                    val detector = LogDetector(context)
                    val analysis = detector.detectAnalysis(safeBitmap)
                    val detectedLogs = analysis.logs

                    // Calculate volumes
                    val calculator = VolumeCalculator()
                    val result = calculator.calculateVolume(
                        detectedLogs, pixelPerCm, logLengthMeters
                    )

                    val rawOriginal = safeBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val yoloAnnotated = rawOriginal.copy(Bitmap.Config.ARGB_8888, true)
                    val yoloCanvas = android.graphics.Canvas(yoloAnnotated)
                    val maskBmp = analysis.maskBitmap.copy(Bitmap.Config.ARGB_8888, true)

                    val boxStrokeWidth = maxOf(2.5f, yoloAnnotated.width / 260f)

                    val maskFillPaint = android.graphics.Paint().apply {
                        color = ForestGreenLight.copy(alpha = 0.32f).toArgb()
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val boxPaint = android.graphics.Paint().apply {
                        color = GoldenAmber.toArgb()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = boxStrokeWidth
                        isAntiAlias = true
                    }
                    val labelBgPaint = android.graphics.Paint().apply {
                        color = WoodBrownDark.toArgb()
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

                    Pair(Triple(rawOriginal, maskBmp, yoloAnnotated), result)
                } catch (e: Throwable) {
                    throw e // re-throw to be caught by outer try
                }
            }

            // Update state back on Main thread
            originalImage = processingResult.first.first
            maskImage = processingResult.first.second
            yoloImage = processingResult.first.third
            volumeResult = processingResult.second
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkForest,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkForest
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
                            color = ForestGreenLight,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Detecting logs...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Text(
                            text = "AI is analyzing the image",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.6f),
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
                        Text(text = errorMessage!!, color = ErrorRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // ── Results ──
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    val currentBmp = when (currentMode) {
                        ResultImageMode.ORIGINAL -> originalImage
                        ResultImageMode.MASK -> maskImage
                        ResultImageMode.YOLO -> yoloImage
                    }
                    currentBmp?.let { bmp ->
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Toggle Button
                    Button(
                        onClick = {
                            currentMode = when (currentMode) {
                                ResultImageMode.ORIGINAL -> ResultImageMode.MASK
                                ResultImageMode.MASK -> ResultImageMode.YOLO
                                ResultImageMode.YOLO -> ResultImageMode.ORIGINAL
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)
                    ) {
                        Text(
                            text = when (currentMode) {
                                ResultImageMode.ORIGINAL -> "Switch To Mask"
                                ResultImageMode.MASK -> "Switch To YOLO View"
                                ResultImageMode.YOLO -> "Switch To Original"
                            },
                            fontWeight = FontWeight.Bold,
                            color = DarkForest
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                                valueColor = ForestGreenLight
                            )
                            StatCard(
                                title = "Avg Diameter",
                                value = String.format("%.1f cm", result.averageDiameterCm),
                                modifier = Modifier.weight(1f),
                                valueColor = GoldenAmber
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
                                valueColor = WoodBrownLight
                            )
                            StatCard(
                                title = "Total Volume",
                                value = String.format("%.3f m³", result.totalVolumeM3),
                                modifier = Modifier.weight(1f),
                                valueColor = ForestGreenLight
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Individual logs list
                        if (result.logs.isNotEmpty()) {
                            Text(
                                text = "Individual Logs",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
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
                                        containerColor = DarkForestCard
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
                                            color = ForestGreenLight,
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
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "Vol: ${
                                                    String.format(
                                                        "%.4f",
                                                        logVolume.volumeM3
                                                    )
                                                } m³",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary
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
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("New Analysis", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ForestGreenLight
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkForestCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
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
