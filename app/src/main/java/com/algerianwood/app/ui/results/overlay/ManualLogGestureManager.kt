package com.algerianwood.app.ui.results.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import com.algerianwood.app.ui.common.ImageLayoutState
import com.algerianwood.app.ui.common.ImageTransform
import com.algerianwood.app.ui.common.applySingleFingerPan
import kotlin.math.max

internal class ManualLogGestureSession {
    var primaryId: PointerId? = null
    var activeLogId: Long? = null
    var grabOffsetCanvas: Offset? = null
    var pendingPlace: Offset? = null
    var pendingDeleteLogId: Long? = null
    var resizeAnchorActive = false
    var initialResizeSpan = 0f
    var initialResizeRadius = 0f
    var undoPushed = false
    var mutated = false

    fun resetInteraction() {
        primaryId = null
        activeLogId = null
        grabOffsetCanvas = null
        pendingPlace = null
        pendingDeleteLogId = null
        resizeAnchorActive = false
        initialResizeSpan = 0f
        initialResizeRadius = 0f
    }

    fun resetForPinch() = resetInteraction()
}

internal object ManualLogGestureManager {
    private const val TAP_SLOP_PX = 12f
    private const val DEFAULT_LOG_RADIUS = 36f
    private const val MIN_RADIUS_PX = 8f
    private const val MAX_RADIUS_PX = 200f

    data class Config(
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val layout: ImageLayoutState,
        val canPlace: Boolean,
        val canEdit: Boolean,
        val canDelete: Boolean,
        val getTransform: () -> ImageTransform,
        val getZoom: () -> Float,
        val getPanX: () -> Float,
        val getPanY: () -> Float,
        val onPanChange: (Float, Float) -> Unit,
        val pushUndoOnce: () -> Unit,
        val nextManualLogId: () -> Long
    )

    private fun syncMagnifier(editorState: ManualLogEditorState, logId: Long) {
        val item = editorState.items.find { it.id == logId } ?: return
        editorState.magnifierCirclePreview = MagnifierCirclePreview(
            centerX = item.centerX,
            centerY = item.centerY,
            radiusPx = item.radiusPx
        )
    }

    fun onGestureStart(editorState: ManualLogEditorState) {
        editorState.gestureActive = true
        editorState.magnifierCirclePreview = null
    }

    fun onGestureEnd(
        session: ManualLogGestureSession,
        editorState: ManualLogEditorState,
        onCommit: () -> Unit
    ) {
        if (session.mutated) onCommit()
        editorState.gestureActive = false
        editorState.magnifierCirclePreview = null
        session.resetInteraction()
    }

    fun shouldSkipImagePinch(session: ManualLogGestureSession, pressedCount: Int): Boolean =
        session.activeLogId != null && pressedCount >= 2

    fun PointerInputScope.handleEvent(
        session: ManualLogGestureSession,
        editorState: ManualLogEditorState,
        event: PointerEvent,
        config: Config
    ) {
        val pressed = event.changes.filter { it.pressed }
        val transform = config.getTransform()

        fun canvasToImage(pos: Offset) = transform.canvasToImage(pos)
        fun inImage(img: Offset) =
            img.x in 0f..config.bitmapWidth.toFloat() &&
                img.y in 0f..config.bitmapHeight.toFloat()
        fun pushUndo() {
            if (!session.undoPushed) {
                session.undoPushed = true
                config.pushUndoOnce()
            }
        }
        fun markMutated() {
            session.mutated = true
        }
        fun tapSlopImage(): Float = TAP_SLOP_PX / transform.scale.coerceAtLeast(0.01f)

        if (session.activeLogId != null && session.primaryId != null && pressed.size >= 2) {
            session.resizeAnchorActive = true
            val log = editorState.items.find { it.id == session.activeLogId } ?: return
            val primary = pressed.firstOrNull { it.id == session.primaryId }
            val secondary = pressed.firstOrNull { it.id != session.primaryId }
            if (primary == null || secondary == null) return

            val currentSpan = ManualLogHitTest.dist(primary.position, secondary.position)
            if (session.initialResizeSpan <= 0f) {
                session.initialResizeSpan = currentSpan.coerceAtLeast(1f)
                session.initialResizeRadius = log.radiusPx
                pushUndo()
            }
            val newR = (session.initialResizeRadius * (currentSpan / session.initialResizeSpan))
                .coerceIn(MIN_RADIUS_PX, MAX_RADIUS_PX)
            editorState.updateItem(session.activeLogId!!) { it.copy(radiusPx = newR) }
            syncMagnifier(editorState, session.activeLogId!!)
            markMutated()
            event.changes.forEach { it.consume() }
            return
        }

        if (session.activeLogId != null && session.primaryId != null && pressed.size == 1) {
            if (session.resizeAnchorActive) {
                session.initialResizeSpan = 0f
                editorState.magnifierCirclePreview = null
                event.changes.forEach { it.consume() }
                return
            }
            val ch = pressed.firstOrNull { it.id == session.primaryId }
            if (ch != null && ch.pressed && session.grabOffsetCanvas != null) {
                val centerCanvas = ch.position - session.grabOffsetCanvas!!
                val img = transform.canvasToImage(centerCanvas)
                editorState.updateItem(session.activeLogId!!) {
                    it.copy(
                        centerX = img.x.coerceIn(0f, config.bitmapWidth.toFloat()),
                        centerY = img.y.coerceIn(0f, config.bitmapHeight.toFloat())
                    )
                }
                syncMagnifier(editorState, session.activeLogId!!)
                markMutated()
                ch.consume()
                return
            }
        }

        val newDown = event.changes.filter { it.pressed && it.changedToDown() }
        if (newDown.size == 1 && session.primaryId == null && session.activeLogId == null) {
            val ch = newDown.first()
            val canvasPos = ch.position
            val img = canvasToImage(canvasPos)
            if (!inImage(img)) return

            val hit = ManualLogHitTest.findTopMostAtCanvas(canvasPos, editorState.items, transform)
            session.primaryId = ch.id
            when {
                hit != null && config.canDelete -> {
                    pushUndo()
                    session.pendingDeleteLogId = hit.id
                    editorState.selectedId = hit.id
                }
                hit != null && config.canEdit -> {
                    pushUndo()
                    session.activeLogId = hit.id
                    editorState.selectedId = hit.id
                    val centerCanvas = transform.imageToCanvas(Offset(hit.centerX, hit.centerY))
                    session.grabOffsetCanvas = canvasPos - centerCanvas
                }
                config.canPlace -> {
                    pushUndo()
                    session.pendingPlace = img
                }
                else -> session.primaryId = null
            }
            ch.consume()
            return
        }

        if (session.pendingDeleteLogId != null && session.primaryId != null && config.canDelete) {
            val ch = event.changes.firstOrNull { it.id == session.primaryId }
            if (ch != null && !ch.pressed) {
                val canvasPos = ch.position
                val downItem = editorState.items.find { it.id == session.pendingDeleteLogId }
                if (downItem != null) {
                    val centerCanvas = transform.imageToCanvas(
                        Offset(downItem.centerX, downItem.centerY)
                    )
                    val slop = tapSlopImage()
                    val hitR = max(
                        downItem.radiusPx * transform.scale,
                        ManualLogHitTest.MIN_HIT_RADIUS_CANVAS_PX
                    ) + slop * transform.scale
                    if (ManualLogHitTest.dist(canvasPos, centerCanvas) <= hitR) {
                        val idx = editorState.indexOf(session.pendingDeleteLogId!!)
                        if (idx >= 0) {
                            editorState.items.removeAt(idx)
                            markMutated()
                        }
                        editorState.selectedId = null
                    }
                }
                session.pendingDeleteLogId = null
                session.primaryId = null
            }
            return
        }

        if (session.pendingPlace != null && session.primaryId != null && config.canPlace) {
            val ch = event.changes.firstOrNull { it.id == session.primaryId }
            if (ch != null) {
                val img = canvasToImage(ch.position)
                val slop = tapSlopImage()
                if (!ch.pressed) {
                    if (ManualLogHitTest.dist(img, session.pendingPlace!!) <= slop) {
                        val p = session.pendingPlace!!
                        val newId = config.nextManualLogId()
                        editorState.items.add(
                            ManualLogOverlayItem(
                                id = newId,
                                centerX = p.x,
                                centerY = p.y,
                                radiusPx = DEFAULT_LOG_RADIUS,
                                zIndex = editorState.items.size
                            )
                        )
                        markMutated()
                        session.activeLogId = newId
                        editorState.selectedId = newId
                        val centerCanvas = transform.imageToCanvas(Offset(p.x, p.y))
                        session.grabOffsetCanvas = ch.position - centerCanvas
                    }
                    session.pendingPlace = null
                    if (session.activeLogId == null) session.primaryId = null
                } else if (ManualLogHitTest.dist(img, session.pendingPlace!!) > slop) {
                    session.pendingPlace = null
                    session.primaryId = null
                }
            }
            return
        }

        if (session.activeLogId == null && session.primaryId == null &&
            pressed.size == 1 && config.getZoom() > 1f
        ) {
            applySingleFingerPan(
                pressed.first(),
                config.bitmapWidth,
                config.bitmapHeight,
                config.layout,
                config.getZoom(),
                config.getPanX(),
                config.getPanY(),
                config.onPanChange
            )
        }
    }
}
