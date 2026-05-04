package com.algerianwood.app.processing

/**
 * Calculates the volume of detected logs based on:
 *   - Detected circle radius (in original image pixels)
 *   - Scale factor (pixels per cm, from calibration)
 *   - Log length (in meters, user-entered)
 *
 * Formula per log:
 *   radiusCm  = radiusPixels / pixelPerCm
 *   areaCm²   = π × radiusCm²
 *   volumeCm³ = areaCm² × logLengthCm
 *   volumeM³  = volumeCm³ / 1,000,000
 */
import kotlin.math.PI

class VolumeCalculator {

    data class LogVolume(
        val log: LogDetector.DetectedLog,
        val diameterCm: Float,
        val areaCm2: Float,
        val volumeCm3: Float,
        val volumeM3: Float
    )

    data class VolumeResult(
        val logs: List<LogVolume>,
        val totalLogs: Int,
        val totalAreaCm2: Float,
        val totalVolumeCm3: Float,
        val totalVolumeM3: Float,
        val averageDiameterCm: Float
    )

    fun calculateVolume(
        detectedLogs: List<LogDetector.DetectedLog>,
        pixelPerCm: Float,
        logLengthMeters: Float
    ): VolumeResult {
        val logLengthCm = logLengthMeters * 100f

        val logVolumes = detectedLogs.map { log ->
            val radiusCm = log.radiusPixels / pixelPerCm
            val diameterCm = radiusCm * 2f
            val areaCm2 = PI.toFloat() * radiusCm * radiusCm
            val volumeCm3 = areaCm2 * logLengthCm
            val volumeM3 = volumeCm3 / 1_000_000f

            LogVolume(
                log = log,
                diameterCm = diameterCm,
                areaCm2 = areaCm2,
                volumeCm3 = volumeCm3,
                volumeM3 = volumeM3
            )
        }

        val totalArea = logVolumes.sumOf { it.areaCm2.toDouble() }.toFloat()
        val totalVolCm3 = logVolumes.sumOf { it.volumeCm3.toDouble() }.toFloat()
        val totalVolM3 = logVolumes.sumOf { it.volumeM3.toDouble() }.toFloat()
        val avgDiameter = if (logVolumes.isNotEmpty()) {
            logVolumes.sumOf { it.diameterCm.toDouble() }.toFloat() / logVolumes.size
        } else 0f

        return VolumeResult(
            logs = logVolumes,
            totalLogs = logVolumes.size,
            totalAreaCm2 = totalArea,
            totalVolumeCm3 = totalVolCm3,
            totalVolumeM3 = totalVolM3,
            averageDiameterCm = avgDiameter
        )
    }
}
