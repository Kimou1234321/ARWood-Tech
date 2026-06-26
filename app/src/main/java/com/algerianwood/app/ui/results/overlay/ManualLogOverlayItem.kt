package com.algerianwood.app.ui.results.overlay

import com.algerianwood.app.processing.ManualLogCircle

data class ManualLogOverlayItem(
    val id: Long,
    val centerX: Float,
    val centerY: Float,
    val radiusPx: Float,
    val zIndex: Int
) {
    fun toManualLogCircle(): ManualLogCircle = ManualLogCircle(
        id = id,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx
    )

    companion object {
        fun from(circle: ManualLogCircle, zIndex: Int): ManualLogOverlayItem = ManualLogOverlayItem(
            id = circle.id,
            centerX = circle.centerX,
            centerY = circle.centerY,
            radiusPx = circle.radiusPx,
            zIndex = zIndex
        )
    }
}
