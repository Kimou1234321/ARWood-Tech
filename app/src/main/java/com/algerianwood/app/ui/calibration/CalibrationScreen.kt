package com.algerianwood.app.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.LocalAppPalette
import com.algerianwood.app.ui.common.TOUCH_TARGET_RADIUS_PX
import com.algerianwood.app.ui.common.drawDragMagnifier
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onAnalyze: () -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    fun defaultThresholdForTiles(tiles: Int): Float = when (tiles) {
        1 -> 50f
        4 -> 40f
        9 -> 30f
        16 -> 20f
        else -> 40f
    }

    val bitmap = SessionData.imageBitmap

    if (bitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("No image loaded", color = palette.errorRed, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                ) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    // Points stored in ORIGINAL image pixel coordinates
    var point1 by remember { mutableStateOf<Offset?>(null) }
    var point2 by remember { mutableStateOf<Offset?>(null) }

    // Drag state: null means no drag, 1 or 2 means dragging that point
    var draggingPoint by remember { mutableStateOf<Int?>(null) }
    var dragPreviewPoint by remember { mutableStateOf<Offset?>(null) }

    // Input values
    var distanceInput by remember { mutableStateOf("") }
    var logLengthInput by remember { mutableStateOf("") }
    var tilesChoice by remember {
        mutableIntStateOf(
            when (SessionData.tilesCount) {
                1 -> 1
                4, 9, 16 -> SessionData.tilesCount
                else -> 4
            }
        )
    }
    var modelThreshold by remember {
        mutableFloatStateOf(SessionData.modelThresholdPercent.toFloat().coerceIn(0f, 100f))
    }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Base fit transform + user precision transform
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var baseOffsetX by remember { mutableFloatStateOf(0f) }
    var baseOffsetY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scale Calibration", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground
                )
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Instructions banner ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when {
                        point1 == null -> "📍 Tap to place the FIRST reference point"
                        point2 == null -> "📍 Tap to place the SECOND reference point"
                        else -> "✅ Both points placed! Drag to adjust, or enter distance below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (point1 != null && point2 != null) palette.mintGreen else palette.goldenAmber,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Image canvas with point picking + dragging ──
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .onSizeChanged { intSize ->
                        bitmap?.let { bmp ->
                            canvasWidth = intSize.width.toFloat()
                            canvasHeight = intSize.height.toFloat()
                            baseScale = minOf(
                                intSize.width.toFloat() / bmp.width,
                                intSize.height.toFloat() / bmp.height
                            )
                            baseOffsetX = (intSize.width - bmp.width * baseScale) / 2f
                            baseOffsetY = (intSize.height - bmp.height * baseScale) / 2f
                        }
                    }
                    .pointerInput(bitmap?.width, bitmap?.height, baseScale, baseOffsetX, baseOffsetY, zoom, panX, panY) {
                        fun clampPan(nextX: Float, nextY: Float): Offset {
                            val bmp = bitmap ?: return Offset.Zero
                            val shownW = bmp.width * baseScale * zoom
                            val shownH = bmp.height * baseScale * zoom
                            val maxPanX = max(0f, (shownW - canvasWidth) / 2f)
                            val maxPanY = max(0f, (shownH - canvasHeight) / 2f)
                            return Offset(
                                nextX.coerceIn(-maxPanX, maxPanX),
                                nextY.coerceIn(-maxPanY, maxPanY)
                            )
                        }

                        fun canvasToImage(pos: Offset): Offset {
                            val scale = baseScale * zoom
                            val x = ((pos.x - (baseOffsetX + panX)) / scale)
                            val y = ((pos.y - (baseOffsetY + panY)) / scale)
                            return Offset(x, y)
                        }

                        fun imageToCanvas(img: Offset): Offset {
                            val scale = baseScale * zoom
                            return Offset(
                                img.x * scale + baseOffsetX + panX,
                                img.y * scale + baseOffsetY + panY
                            )
                        }

                        awaitEachGesture {
                            val downEvent = awaitPointerEvent()
                            val down = downEvent.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                            val startImg = canvasToImage(down.position)
                            var tapCandidate = true
                            var localDraggingPoint: Int? = null

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }

                                if (pressedChanges.isEmpty()) break

                                if (pressedChanges.size >= 2) {
                                    tapCandidate = false
                                    localDraggingPoint = null
                                    draggingPoint = null
                                    dragPreviewPoint = null

                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)

                                    val oldZoom = zoom
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)

                                    if (baseScale > 0f) {
                                        val focusX =
                                            (centroid.x - (baseOffsetX + panX)) / (baseScale * oldZoom)
                                        val focusY =
                                            (centroid.y - (baseOffsetY + panY)) / (baseScale * oldZoom)

                                        zoom = newZoom
                                        val rawPanX = centroid.x - baseOffsetX - (focusX * baseScale * newZoom) + panChange.x
                                        val rawPanY = centroid.y - baseOffsetY - (focusY * baseScale * newZoom) + panChange.y
                                        val clamped = clampPan(rawPanX, rawPanY)
                                        panX = clamped.x
                                        panY = clamped.y
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                val change = pressedChanges.first()
                                val imgPos = canvasToImage(change.position)

                                if (localDraggingPoint == null) {
                                    val canvasPos = change.position
                                    val distToP1 = point1?.let {
                                        val dp = imageToCanvas(it)
                                        sqrt(
                                            (canvasPos.x - dp.x) * (canvasPos.x - dp.x) +
                                                (canvasPos.y - dp.y) * (canvasPos.y - dp.y)
                                        )
                                    } ?: Float.MAX_VALUE
                                    val distToP2 = point2?.let {
                                        val dp = imageToCanvas(it)
                                        sqrt(
                                            (canvasPos.x - dp.x) * (canvasPos.x - dp.x) +
                                                (canvasPos.y - dp.y) * (canvasPos.y - dp.y)
                                        )
                                    } ?: Float.MAX_VALUE

                                    localDraggingPoint = when {
                                        distToP1 < TOUCH_TARGET_RADIUS_PX && distToP1 <= distToP2 -> 1
                                        distToP2 < TOUCH_TARGET_RADIUS_PX -> 2
                                        else -> null
                                    }
                                    draggingPoint = localDraggingPoint
                                }

                                if (localDraggingPoint != null) {
                                    tapCandidate = false
                                    val clampedImg = Offset(
                                        imgPos.x.coerceIn(0f, (bitmap?.width?.toFloat() ?: 0f)),
                                        imgPos.y.coerceIn(0f, (bitmap?.height?.toFloat() ?: 0f))
                                    )
                                    dragPreviewPoint = clampedImg
                                    if (localDraggingPoint == 1) {
                                        point1 = clampedImg
                                    } else {
                                        point2 = clampedImg
                                    }
                                    change.consume()
                                } else if (change.positionChange().getDistance() > 8f) {
                                    tapCandidate = false
                                }
                            }

                            draggingPoint = null
                            dragPreviewPoint = null
                            if (tapCandidate) {
                                bitmap?.let { bmp ->
                                    if (startImg.x >= 0 && startImg.x < bmp.width &&
                                        startImg.y >= 0 && startImg.y < bmp.height
                                    ) {
                                        if (point1 == null) {
                                            point1 = startImg
                                        } else if (point2 == null) {
                                            point2 = startImg
                                        } else {
                                            point1 = startImg
                                            point2 = null
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                bitmap?.let { bmp ->
                    val bmpImage = bmp.asImageBitmap()
                    val scale = baseScale * zoom
                    val offX = baseOffsetX + panX
                    val offY = baseOffsetY + panY

                    // Draw the image
                    drawImage(
                        image = bmpImage,
                        dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                        dstSize = IntSize(
                            (bmp.width * scale).toInt(),
                            (bmp.height * scale).toInt()
                        )
                    )

                    // Helper: convert image pixel coords → canvas pixel coords
                    fun imgToCanvas(pt: Offset) = Offset(
                        pt.x * scale + offX,
                        pt.y * scale + offY
                    )

                    // Draw Point 1 (orange)
                    point1?.let { p ->
                        val dp = imgToCanvas(p)
                        val isDragging = draggingPoint == 1
                        val outerRadius = if (isDragging) 30f else 22f

                        drawCircle(palette.forestGreenLight, outerRadius, dp)
                        drawCircle(Color.White, 12f, dp)
                        drawCircle(palette.forestGreenLight, 4f, dp)

                        // Crosshair lines for precision when dragging
                        if (isDragging) {
                            val crossLen = 40f
                            drawLine(
                                palette.forestGreenLight.copy(alpha = 0.7f),
                                Offset(dp.x - crossLen, dp.y),
                                Offset(dp.x + crossLen, dp.y),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                palette.forestGreenLight.copy(alpha = 0.7f),
                                Offset(dp.x, dp.y - crossLen),
                                Offset(dp.x, dp.y + crossLen),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Draw Point 2 (blue)
                    point2?.let { p ->
                        val dp = imgToCanvas(p)
                        val isDragging = draggingPoint == 2
                        val outerRadius = if (isDragging) 30f else 22f

                        drawCircle(palette.woodBrownLight, outerRadius, dp)
                        drawCircle(Color.White, 12f, dp)
                        drawCircle(palette.woodBrownLight, 4f, dp)

                        // Crosshair lines for precision when dragging
                        if (isDragging) {
                            val crossLen = 40f
                            drawLine(
                                palette.woodBrownLight.copy(alpha = 0.7f),
                                Offset(dp.x - crossLen, dp.y),
                                Offset(dp.x + crossLen, dp.y),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                palette.woodBrownLight.copy(alpha = 0.7f),
                                Offset(dp.x, dp.y - crossLen),
                                Offset(dp.x, dp.y + crossLen),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Draw connecting line + midpoint marker
                    if (point1 != null && point2 != null) {
                        val dp1 = imgToCanvas(point1!!)
                        val dp2 = imgToCanvas(point2!!)
                        drawLine(
                            color = palette.goldenAmber,
                            start = dp1,
                            end = dp2,
                            strokeWidth = 3f
                        )
                        val mid = Offset((dp1.x + dp2.x) / 2f, (dp1.y + dp2.y) / 2f)
                        drawCircle(Color.Black.copy(alpha = 0.5f), 18f, mid)
                        drawCircle(palette.goldenAmber, 6f, mid)
                    }

                    if (draggingPoint != null && dragPreviewPoint != null) {
                        val preview = dragPreviewPoint!!
                        drawDragMagnifier(
                            bitmap = bmpImage,
                            previewImage = preview,
                            anchorCanvas = imgToCanvas(preview),
                            canvasSize = androidx.compose.ui.geometry.Size(size.width, size.height),
                            imageWidth = bmp.width,
                            imageHeight = bmp.height
                        )
                    }
                }
            }

            // ── Input controls ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    if (point1 != null && point2 != null) {
                        // Pixel distance in original image coordinates
                        val p1 = point1!!
                        val p2 = point2!!
                        val pixelDist = sqrt(
                            (p2.x - p1.x) * (p2.x - p1.x) +
                                    (p2.y - p1.y) * (p2.y - p1.y)
                        )

                        Text(
                            text = "Pixel distance: ${pixelDist.toInt()} px",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = distanceInput,
                            onValueChange = { distanceInput = it; showError = false },
                            label = { Text("Distance between points (cm)") },
                            leadingIcon = { Icon(Icons.Filled.Straighten, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.forestGreenLight,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                focusedLabelColor = palette.forestGreenLight,
                                cursorColor = palette.forestGreenLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = logLengthInput,
                            onValueChange = { logLengthInput = it; showError = false },
                            label = { Text("Log length (meters)") },
                            leadingIcon = { Icon(Icons.Filled.Height, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.forestGreenLight,
                                unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                                focusedLabelColor = palette.forestGreenLight,
                                cursorColor = palette.forestGreenLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (showError) {
                            Text(
                                text = errorMessage,
                                color = palette.errorRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Detection tiles — 1 = full image",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 4, 9, 16).forEach { n ->
                                FilterChip(
                                    selected = tilesChoice == n,
                                    onClick = {
                                        tilesChoice = n
                                        modelThreshold = defaultThresholdForTiles(n)
                                    },
                                    label = { Text("$n") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.forestGreen.copy(alpha = 0.35f),
                                        selectedLabelColor = colors.onBackground,
                                        containerColor = colors.surfaceVariant,
                                        labelColor = colors.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Model threshold: ${modelThreshold.roundToInt()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                        Slider(
                            value = modelThreshold,
                            onValueChange = { modelThreshold = it.coerceIn(0f, 100f) },
                            valueRange = 0f..100f
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    point1 = null
                                    point2 = null
                                    draggingPoint = null
                                    dragPreviewPoint = null
                                    distanceInput = ""
                                    logLengthInput = ""
                                    tilesChoice = 4
                                    modelThreshold = defaultThresholdForTiles(4)
                                    showError = false
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.onSurfaceVariant
                                )
                            ) {
                                Text("Clear Points")
                            }

                            Button(
                                onClick = {
                                    val distance = distanceInput.toFloatOrNull()
                                    val logLength = logLengthInput.toFloatOrNull()

                                    when {
                                        distance == null || distance <= 0f -> {
                                            showError = true
                                            errorMessage = "Enter a valid distance in cm"
                                        }

                                        logLength == null || logLength <= 0f -> {
                                            showError = true
                                            errorMessage = "Enter a valid log length in meters"
                                        }

                                        else -> {
                                            val pxPerCm = pixelDist / distance
                                            if (pxPerCm <= 0f || pxPerCm.isNaN() || pxPerCm.isInfinite()) {
                                                showError = true
                                                errorMessage = "Invalid calibration — move points further apart"
                                            } else {
                                                SessionData.pixelPerCm = pxPerCm
                                                SessionData.logLengthMeters = logLength
                                                SessionData.tilesCount = tilesChoice
                                                SessionData.modelThresholdPercent = modelThreshold.roundToInt().coerceIn(0, 100)
                                                try {
                                                    onAnalyze()
                                                } catch (_: Throwable) {
                                                    showError = true
                                                    errorMessage = "Navigation error — try again"
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.forestGreen
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Analyze Logs", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "Tap on the image above to place two reference points for scale calibration.\n\n" +
                                    "Choose two points whose real-world distance you know (e.g. a ruler, a known log diameter).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
