package com.algerianwood.app.data

import android.graphics.Bitmap

/**
 * Simple shared state holder for passing data between screens.
 * For this prototype, a singleton is sufficient. In production,
 * this would be replaced with a proper repository + database pattern.
 */
object SessionData {
    var imageBitmap: Bitmap? = null
    var pixelPerCm: Float = 0f
    var logLengthMeters: Float = 0f
    var currentUsername: String = ""
    var selectedSavedAnalysisId: Long? = null

    /** 1 = whole image once (no tiling); 4 / 9 / 16 = N×N tiled inference */
    @Volatile
    var tilesCount: Int = 4
    @Volatile
    var modelThresholdPercent: Int = 50

    fun clear() {
        imageBitmap?.let { if (!it.isRecycled) it.recycle() }
        imageBitmap = null
        pixelPerCm = 0f
        logLengthMeters = 0f
        tilesCount = 4
        modelThresholdPercent = 50
        selectedSavedAnalysisId = null
    }
}
