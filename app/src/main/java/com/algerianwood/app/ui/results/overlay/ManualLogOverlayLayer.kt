package com.algerianwood.app.ui.results.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.algerianwood.app.processing.ManualLogCircle

@Composable
fun rememberManualLogEditorState(manualLogs: List<ManualLogCircle>): ManualLogEditorState {
    val editorState = remember { ManualLogEditorState() }
    val latestLogs by rememberUpdatedState(manualLogs)
    LaunchedEffect(manualLogs, editorState.gestureActive) {
        if (!editorState.gestureActive) {
            editorState.syncFrom(latestLogs)
        }
    }
    return editorState
}
