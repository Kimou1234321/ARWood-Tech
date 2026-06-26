package com.algerianwood.app.ui.results.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.algerianwood.app.processing.ManualLogCircle

/** Live circle shown inside the manual-log magnifier (image pixel space). */
data class MagnifierCirclePreview(
    val centerX: Float,
    val centerY: Float,
    val radiusPx: Float
)

class ManualLogEditorState {
    val items: SnapshotStateList<ManualLogOverlayItem> = mutableStateListOf()
    var selectedId by mutableStateOf<Long?>(null)
    var gestureActive by mutableStateOf(false)
    var magnifierCirclePreview by mutableStateOf<MagnifierCirclePreview?>(null)

    fun syncFrom(logs: List<ManualLogCircle>) {
        items.clear()
        logs.forEachIndexed { index, log ->
            items.add(ManualLogOverlayItem.from(log, index))
        }
        if (selectedId != null && items.none { it.id == selectedId }) {
            selectedId = null
        }
    }

    fun toManualLogCircles(): List<ManualLogCircle> = items.map { it.toManualLogCircle() }

    fun indexOf(id: Long): Int = items.indexOfFirst { it.id == id }

    fun updateItem(id: Long, transform: (ManualLogOverlayItem) -> ManualLogOverlayItem) {
        val idx = indexOf(id)
        if (idx >= 0) {
            items[idx] = transform(items[idx])
        }
    }
}
