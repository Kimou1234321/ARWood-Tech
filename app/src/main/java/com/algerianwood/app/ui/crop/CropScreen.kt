package com.algerianwood.app.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.algerianwood.app.data.SessionData
import com.algerianwood.app.ui.theme.LocalAppPalette
private enum class CropHandle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private const val CROP_HANDLE_HIT_RADIUS_PX = 44f
private const val CROP_HANDLE_DRAW_RADIUS_PX = 16f
private const val CROP_HANDLE_DRAW_INNER_PX = 8f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalAppPalette.current
    val colors = MaterialTheme.colorScheme
    val bitmap = SessionData.imageBitmap

    if (bitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No image to crop", color = colors.onSurfaceVariant)
        }
        return
    }

    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var baseOffsetX by remember { mutableFloatStateOf(0f) }
    var baseOffsetY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    var cropLeft by remember(bitmap) { mutableFloatStateOf(0f) }
    var cropTop by remember(bitmap) { mutableFloatStateOf(0f) }
    var cropRight by remember(bitmap) { mutableFloatStateOf(bitmap.width.toFloat()) }
    var cropBottom by remember(bitmap) { mutableFloatStateOf(bitmap.height.toFloat()) }

    fun imageToCanvas(x: Float, y: Float): Offset {
        val s = baseScale * zoom
        return Offset(x * s + baseOffsetX + panX, y * s + baseOffsetY + panY)
    }

    fun canvasToImage(pos: Offset): Offset {
        val s = baseScale * zoom
        return Offset(
            (pos.x - (baseOffsetX + panX)) / s,
            (pos.y - (baseOffsetY + panY)) / s
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop Image", fontWeight = FontWeight.SemiBold) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Drag corners or move the crop area. Pinch to zoom.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.goldenAmber,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
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
                    .pointerInput(bitmap.width, bitmap.height, zoom, panX, panY, cropLeft, cropTop, cropRight, cropBottom) {
                        fun hitHandle(canvasPos: Offset): CropHandle {
                            val tl = imageToCanvas(cropLeft, cropTop)
                            val tr = imageToCanvas(cropRight, cropTop)
                            val bl = imageToCanvas(cropLeft, cropBottom)
                            val br = imageToCanvas(cropRight, cropBottom)
                            fun near(a: Offset) =
                                (a - canvasPos).getDistance() < CROP_HANDLE_HIT_RADIUS_PX
                            return when {
                                near(tl) -> CropHandle.TOP_LEFT
                                near(tr) -> CropHandle.TOP_RIGHT
                                near(bl) -> CropHandle.BOTTOM_LEFT
                                near(br) -> CropHandle.BOTTOM_RIGHT
                                else -> {
                                    val img = canvasToImage(canvasPos)
                                    if (img.x in cropLeft..cropRight && img.y in cropTop..cropBottom) {
                                        CropHandle.MOVE
                                    } else {
                                        CropHandle.NONE
                                    }
                                }
                            }
                        }

                        fun clampCrop() {
                            val minSize = 40f
                            cropLeft = cropLeft.coerceIn(0f, bitmap.width - minSize)
                            cropTop = cropTop.coerceIn(0f, bitmap.height - minSize)
                            cropRight = cropRight.coerceIn(cropLeft + minSize, bitmap.width.toFloat())
                            cropBottom = cropBottom.coerceIn(cropTop + minSize, bitmap.height.toFloat())
                        }

                        awaitEachGesture {
                            val down = awaitPointerEvent()
                            if (down.changes.count { it.pressed } >= 2) {
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val oldZoom = zoom
                                        val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                        if (baseScale > 0f) {
                                            val focusX = (centroid.x - (baseOffsetX + panX)) / (baseScale * oldZoom)
                                            val focusY = (centroid.y - (baseOffsetY + panY)) / (baseScale * oldZoom)
                                            zoom = newZoom
                                            val rawPanX = centroid.x - baseOffsetX - (focusX * baseScale * newZoom) + panChange.x
                                            val rawPanY = centroid.y - baseOffsetY - (focusY * baseScale * newZoom) + panChange.y
                                            val shownW = bitmap.width * baseScale * newZoom
                                            val shownH = bitmap.height * baseScale * newZoom
                                            val maxPanX = maxOf(0f, (shownW - canvasWidth) / 2f)
                                            val maxPanY = maxOf(0f, (shownH - canvasHeight) / 2f)
                                            panX = rawPanX.coerceIn(-maxPanX, maxPanX)
                                            panY = rawPanY.coerceIn(-maxPanY, maxPanY)
                                        }
                                        pressed.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                return@awaitEachGesture
                            }

                            val ptr = down.changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                            val startCanvas = ptr.position
                            val startImg = canvasToImage(startCanvas)
                            val handle = hitHandle(startCanvas)
                            if (handle == CropHandle.NONE) return@awaitEachGesture

                            val startLeft = cropLeft
                            val startTop = cropTop
                            val startRight = cropRight
                            val startBottom = cropBottom

                            do {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == ptr.id } ?: break
                                if (!ch.pressed) break
                                val img = canvasToImage(ch.position)
                                val dx = img.x - startImg.x
                                val dy = img.y - startImg.y
                                when (handle) {
                                    CropHandle.MOVE -> {
                                        val w = startRight - startLeft
                                        val h = startBottom - startTop
                                        cropLeft = (startLeft + dx).coerceIn(0f, bitmap.width - w)
                                        cropTop = (startTop + dy).coerceIn(0f, bitmap.height - h)
                                        cropRight = cropLeft + w
                                        cropBottom = cropTop + h
                                    }
                                    CropHandle.TOP_LEFT -> {
                                        cropLeft = minOf(img.x, startRight - 40f)
                                        cropTop = minOf(img.y, startBottom - 40f)
                                    }
                                    CropHandle.TOP_RIGHT -> {
                                        cropRight = maxOf(img.x, startLeft + 40f)
                                        cropTop = minOf(img.y, startBottom - 40f)
                                    }
                                    CropHandle.BOTTOM_LEFT -> {
                                        cropLeft = minOf(img.x, startRight - 40f)
                                        cropBottom = maxOf(img.y, startTop + 40f)
                                    }
                                    CropHandle.BOTTOM_RIGHT -> {
                                        cropRight = maxOf(img.x, startLeft + 40f)
                                        cropBottom = maxOf(img.y, startTop + 40f)
                                    }
                                    else -> Unit
                                }
                                clampCrop()
                                ch.consume()
                            } while (true)
                        }
                    }
            ) {
                val bmpImage = bitmap.asImageBitmap()
                val scale = baseScale * zoom
                val offX = baseOffsetX + panX
                val offY = baseOffsetY + panY
                drawImage(
                    image = bmpImage,
                    dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                    dstSize = IntSize(
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt()
                    )
                )

                val tl = imageToCanvas(cropLeft, cropTop)
                val tr = imageToCanvas(cropRight, cropTop)
                val bl = imageToCanvas(cropLeft, cropBottom)
                val br = imageToCanvas(cropRight, cropBottom)

                val dimPath = Path().apply {
                    addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                    addRect(Rect(tl.x, tl.y, br.x, br.y))
                }
                clipPath(dimPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.55f))
                }

                drawRect(
                    color = palette.goldenAmber,
                    topLeft = Offset(tl.x, tl.y),
                    size = Size(br.x - tl.x, br.y - tl.y),
                    style = Stroke(width = 2.5f)
                )
                listOf(tl, tr, bl, br).forEach { corner ->
                    drawCircle(palette.goldenAmber, CROP_HANDLE_DRAW_RADIUS_PX, corner)
                    drawCircle(Color.White, CROP_HANDLE_DRAW_INNER_PX, corner)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val left = cropLeft.toInt().coerceIn(0, bitmap.width - 1)
                        val top = cropTop.toInt().coerceIn(0, bitmap.height - 1)
                        val right = cropRight.toInt().coerceIn(left + 1, bitmap.width)
                        val bottom = cropBottom.toInt().coerceIn(top + 1, bitmap.height)
                        val w = right - left
                        val h = bottom - top
                        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
                        SessionData.imageBitmap = cropped
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forestGreen)
                ) { Text("Confirm") }
            }
        }
    }
}
