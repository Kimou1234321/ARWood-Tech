package com.algerianwood.app.ui.results.overlay

import androidx.compose.ui.geometry.Offset
import com.algerianwood.app.ui.common.ImageTransform
import com.algerianwood.app.ui.common.TOUCH_TARGET_RADIUS_PX
import kotlin.math.max
import kotlin.math.sqrt

object ManualLogHitTest {
    const val MIN_HIT_RADIUS_CANVAS_PX = TOUCH_TARGET_RADIUS_PX

    fun dist(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Returns top-most item (highest zIndex / last in list). */
    fun findTopMostAtCanvas(
        canvasPos: Offset,
        items: List<ManualLogOverlayItem>,
        transform: ImageTransform
    ): ManualLogOverlayItem? {
        for (i in items.indices.reversed()) {
            val item = items[i]
            val center = transform.imageToCanvas(Offset(item.centerX, item.centerY))
            val hitR = max(item.radiusPx * transform.scale, MIN_HIT_RADIUS_CANVAS_PX)
            if (dist(canvasPos, center) <= hitR) return item
        }
        return null
    }
}
