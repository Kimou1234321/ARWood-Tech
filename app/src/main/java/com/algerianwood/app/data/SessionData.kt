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

    fun clear() {
        imageBitmap?.let { if (!it.isRecycled) it.recycle() }
        imageBitmap = null
        pixelPerCm = 0f
        logLengthMeters = 0f
    }
}
