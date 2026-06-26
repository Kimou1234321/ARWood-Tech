package com.algerianwood.app.ui.results

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.MaterialTheme
import com.algerianwood.app.processing.ManualLogCircle
import com.algerianwood.app.ui.common.ImageLayoutState
import com.algerianwood.app.ui.common.ImageTransform
import com.algerianwood.app.ui.common.applyPinchZoom
import com.algerianwood.app.ui.common.applySingleFingerPan
import com.algerianwood.app.ui.common.drawCircleMagnifier
import com.algerianwood.app.ui.common.drawLetterboxedBitmap
import com.algerianwood.app.ui.results.overlay.ManualLogGestureManager.handleEvent
import com.algerianwood.app.ui.results.overlay.ManualLogGestureManager
import com.algerianwood.app.ui.results.overlay.ManualLogGestureSession
import com.algerianwood.app.ui.results.overlay.ManualLogOverlayRenderer
import com.algerianwood.app.ui.results.overlay.rememberManualLogEditorState
import com.algerianwood.app.ui.theme.LocalAppPalette

enum class ResultsEditTool { NONE, ERASER, ADD_LOG }

@Composable
fun ResultsImageViewer(
    displayBitmap: Bitmap,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    panX: Float,
    onPanXChange: (Float) -> Unit,
    panY: Float,
    onPanYChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    editTool: ResultsEditTool = ResultsEditTool.NONE,
    canPlaceManualLogs: Boolean = false,
    canEditManualLogs: Boolean = false,
    canDeleteManualLogs: Boolean = false,
    manualLogs: List<ManualLogCircle> = emptyList(),
    onManualLogsChange: (List<ManualLogCircle>) -> Unit = {},
    onEraserStroke: (Float, Float) -> Unit = { _, _ -> },
    eraserBrushRadiusPx: Float = ERASER_BRUSH_DEFAULT_PX,
    onBeforeEditGesture: () -> Unit = {},
    nextManualLogId: () -> Long = { System.nanoTime() },
    showManualLogOverlay: Boolean = false,
    onLayoutChanged: (ImageLayoutState) -> Unit = {}
) {
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var baseOffsetX by remember { mutableFloatStateOf(0f) }
    var baseOffsetY by remember { mutableFloatStateOf(0f) }
    var eraserTouchImage by remember { mutableStateOf<Offset?>(null) }

    val editorState = rememberManualLogEditorState(manualLogs)
    val latestOnBeforeEditGesture by rememberUpdatedState(onBeforeEditGesture)
    val latestOnManualLogsChange by rememberUpdatedState(onManualLogsChange)
    val latestOnLayoutChanged by rememberUpdatedState(onLayoutChanged)

    val manualGestureActive =
        canPlaceManualLogs || canEditManualLogs || canDeleteManualLogs

    val letterboxColor = MaterialTheme.colorScheme.surfaceVariant
    val palette = LocalAppPalette.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(displayBitmap.width.toFloat() / displayBitmap.height.toFloat())
            .clipToBounds()
            .onSizeChanged { intSize ->
                canvasWidth = intSize.width.toFloat()
                canvasHeight = intSize.height.toFloat()
                baseScale = minOf(
                    intSize.width.toFloat() / displayBitmap.width,
                    intSize.height.toFloat() / displayBitmap.height
                )
                baseOffsetX = (intSize.width - displayBitmap.width * baseScale) / 2f
                baseOffsetY = (intSize.height - displayBitmap.height * baseScale) / 2f
                latestOnLayoutChanged(
                    ImageLayoutState(canvasWidth, canvasHeight, baseScale, baseOffsetX, baseOffsetY)
                )
            }
            .pointerInput(
                displayBitmap.width,
                displayBitmap.height,
                baseScale,
                baseOffsetX,
                baseOffsetY,
                editTool,
                canPlaceManualLogs,
                canEditManualLogs,
                canDeleteManualLogs
            ) {
                val layout = ImageLayoutState(
                    canvasWidth, canvasHeight, baseScale, baseOffsetX, baseOffsetY
                )

                var currentZoom = zoom
                var currentPanX = panX
                var currentPanY = panY

                fun transform() = ImageTransform(
                    baseScale, currentZoom, currentPanX, currentPanY, baseOffsetX, baseOffsetY
                )

                fun canvasToImage(pos: Offset) = transform().canvasToImage(pos)

                fun inImage(img: Offset) =
                    img.x in 0f..displayBitmap.width.toFloat() &&
                        img.y in 0f..displayBitmap.height.toFloat()

                val manualConfig = ManualLogGestureManager.Config(
                    bitmapWidth = displayBitmap.width,
                    bitmapHeight = displayBitmap.height,
                    layout = layout,
                    canPlace = canPlaceManualLogs,
                    canEdit = canEditManualLogs,
                    canDelete = canDeleteManualLogs,
                    getTransform = { transform() },
                    getZoom = { currentZoom },
                    getPanX = { currentPanX },
                    getPanY = { currentPanY },
                    onPanChange = { x, y ->
                        currentPanX = x
                        currentPanY = y
                        onPanXChange(x)
                        onPanYChange(y)
                    },
                    pushUndoOnce = { latestOnBeforeEditGesture() },
                    nextManualLogId = nextManualLogId
                )

                awaitEachGesture {
                    val manualSession = ManualLogGestureSession()
                    var eraserUndoPushed = false

                    if (manualGestureActive) {
                        ManualLogGestureManager.onGestureStart(editorState)
                    }

                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            val skipPinch = manualGestureActive &&
                                ManualLogGestureManager.shouldSkipImagePinch(manualSession, pressed.size)

                            if (!skipPinch &&
                                applyPinchZoom(
                                    event, displayBitmap.width, displayBitmap.height, layout,
                                    currentZoom, currentPanX, currentPanY,
                                    onZoomChange = {
                                        currentZoom = it
                                        onZoomChange(it)
                                    }
                                ) { x, y ->
                                    currentPanX = x
                                    currentPanY = y
                                    onPanXChange(x)
                                    onPanYChange(y)
                                }
                            ) {
                                manualSession.resetForPinch()
                                continue
                            }

                            when {
                                editTool == ResultsEditTool.ERASER -> {
                                    pressed.forEach { ch ->
                                        if (!ch.pressed) return@forEach
                                        val img = canvasToImage(ch.position)
                                        if (inImage(img)) {
                                            if (!eraserUndoPushed) {
                                                eraserUndoPushed = true
                                                latestOnBeforeEditGesture()
                                            }
                                            eraserTouchImage = img
                                            onEraserStroke(img.x, img.y)
                                            ch.consume()
                                        }
                                    }
                                }

                                manualGestureActive -> {
                                    handleEvent(
                                        manualSession, editorState, event, manualConfig
                                    )
                                }

                                else -> {
                                    if (pressed.size == 1) {
                                        applySingleFingerPan(
                                            pressed.first(),
                                            displayBitmap.width,
                                            displayBitmap.height,
                                            layout,
                                            currentZoom,
                                            currentPanX,
                                            currentPanY
                                        ) { x, y ->
                                            currentPanX = x
                                            currentPanY = y
                                            onPanXChange(x)
                                            onPanYChange(y)
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (editTool == ResultsEditTool.ERASER) {
                            eraserTouchImage = null
                        }
                        if (manualGestureActive) {
                            ManualLogGestureManager.onGestureEnd(manualSession, editorState) {
                                latestOnManualLogsChange(editorState.toManualLogCircles())
                            }
                        }
                    }
                }
            }
    ) {
        val layout = ImageLayoutState(canvasWidth, canvasHeight, baseScale, baseOffsetX, baseOffsetY)
        val bmpImage = displayBitmap.asImageBitmap()
        drawLetterboxedBitmap(
            bitmap = bmpImage,
            layout = layout,
            zoom = zoom,
            panX = panX,
            panY = panY,
            letterboxColor = letterboxColor
        )

        val drawOverlays = editorState.items.isNotEmpty() &&
            (showManualLogOverlay || editorState.gestureActive)
        val viewport = layout.viewportRect(displayBitmap.width, displayBitmap.height)
        if (drawOverlays) {
            clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
                val transform = ImageTransform(baseScale, zoom, panX, panY, baseOffsetX, baseOffsetY)
                with(ManualLogOverlayRenderer) {
                    drawOverlays(editorState.items, editorState.selectedId, transform)
                }
            }
        }
        val magnifierCircle = editorState.magnifierCirclePreview
        if (editorState.gestureActive && magnifierCircle != null) {
            clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
                val transform = ImageTransform(baseScale, zoom, panX, panY, baseOffsetX, baseOffsetY)
                val center = Offset(magnifierCircle.centerX, magnifierCircle.centerY)
                drawCircleMagnifier(
                    bitmap = bmpImage,
                    previewCenter = center,
                    radiusPx = magnifierCircle.radiusPx,
                    anchorCanvas = transform.imageToCanvas(center),
                    canvasSize = Size(size.width, size.height),
                    imageWidth = displayBitmap.width,
                    imageHeight = displayBitmap.height
                )
            }
        }
        val eraserTouch = eraserTouchImage
        if (editTool == ResultsEditTool.ERASER && eraserTouch != null) {
            val transform = ImageTransform(baseScale, zoom, panX, panY, baseOffsetX, baseOffsetY)
            val center = transform.imageToCanvas(eraserTouch)
            val radius = eraserScreenRadiusPx(eraserBrushRadiusPx, zoom, baseScale)
            drawCircle(
                color = palette.forestGreenLight.copy(alpha = 0.35f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = palette.goldenAmber,
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f)
            )
        }
    }
}
