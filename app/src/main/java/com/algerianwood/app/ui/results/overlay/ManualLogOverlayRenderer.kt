package com.algerianwood.app.ui.results.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.algerianwood.app.ui.common.ImageTransform
import com.algerianwood.app.ui.theme.ForestGreenLight
import com.algerianwood.app.ui.theme.GoldenAmber

object ManualLogOverlayRenderer {
    fun DrawScope.drawOverlays(
        items: List<ManualLogOverlayItem>,
        selectedId: Long?,
        transform: ImageTransform
    ) {
        items.forEach { item ->
            val center = transform.imageToCanvas(Offset(item.centerX, item.centerY))
            val radiusCanvas = item.radiusPx * transform.scale
            val isSelected = item.id == selectedId

            drawCircle(
                color = ForestGreenLight.copy(alpha = 0.35f),
                radius = radiusCanvas,
                center = center
            )
            drawCircle(
                color = GoldenAmber,
                radius = radiusCanvas,
                center = center,
                style = Stroke(width = maxOf(2f, 2f * transform.scale))
            )
            drawCircle(color = Color.White, radius = 8f, center = center)

            if (isSelected) {
                drawCircle(
                    color = GoldenAmber.copy(alpha = 0.85f),
                    radius = radiusCanvas + 4f,
                    center = center,
                    style = Stroke(width = maxOf(3.5f, 3.5f * transform.scale))
                )
                val handleCenter = center + Offset(0f, radiusCanvas + 10f)
                drawCircle(
                    color = GoldenAmber,
                    radius = 12f,
                    center = handleCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = handleCenter
                )
            }
        }
    }
}
