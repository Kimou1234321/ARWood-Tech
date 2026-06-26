package com.algerianwood.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.algerianwood.app.processing.LogDetector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class SavedAnalysisSummary(
    val id: Long,
    val username: String,
    val name: String,
    val description: String,
    val savedAtMs: Long
)

data class SavedAnalysisSnapshot(
    val summary: SavedAnalysisSummary,
    val totalLogs: Int,
    val totalAreaCm2: Float,
    val totalVolumeM3: Float,
    val averageDiameterCm: Float,
    val logs: List<SavedLogMetric>,
    val tileBounds: List<LogDetector.TileBounds>,
    val originalBitmap: Bitmap,
    val maskBitmap: Bitmap,
    val yoloBitmap: Bitmap
)

data class SavedLogMetric(
    val index: Int,
    val diameterCm: Float,
    val volumeM3: Float
)

class LocalAnalysisRepository(private val context: Context) {
    private val baseDir: File
        get() = File(context.filesDir, "saved_analyses").apply { mkdirs() }

    fun save(snapshot: SavedAnalysisSnapshot): Result<Unit> {
        return runCatching {
            val userDir = File(baseDir, snapshot.summary.username).apply { mkdirs() }
            val itemDir = File(userDir, snapshot.summary.id.toString()).apply { mkdirs() }

            writeBitmap(File(itemDir, "original.png"), snapshot.originalBitmap)
            writeBitmap(File(itemDir, "mask.png"), snapshot.maskBitmap)
            writeBitmap(File(itemDir, "yolo.png"), snapshot.yoloBitmap)

            val json = JSONObject()
                .put("id", snapshot.summary.id)
                .put("username", snapshot.summary.username)
                .put("name", snapshot.summary.name)
                .put("description", snapshot.summary.description)
                .put("savedAtMs", snapshot.summary.savedAtMs)
                .put("totalLogs", snapshot.totalLogs)
                .put("totalAreaCm2", snapshot.totalAreaCm2.toDouble())
                .put("totalVolumeM3", snapshot.totalVolumeM3.toDouble())
                .put("averageDiameterCm", snapshot.averageDiameterCm.toDouble())
                .put("logs", JSONArray().apply {
                    snapshot.logs.forEach {
                        put(
                            JSONObject()
                                .put("index", it.index)
                                .put("diameterCm", it.diameterCm.toDouble())
                                .put("volumeM3", it.volumeM3.toDouble())
                        )
                    }
                })
                .put("tileBounds", JSONArray().apply {
                    snapshot.tileBounds.forEach {
                        put(
                            JSONObject()
                                .put("left", it.left)
                                .put("top", it.top)
                                .put("right", it.right)
                                .put("bottom", it.bottom)
                        )
                    }
                })
            File(itemDir, "meta.json").writeText(json.toString())
        }
    }

    fun listForUser(username: String): List<SavedAnalysisSummary> {
        val userDir = File(baseDir, username)
        if (!userDir.exists()) return emptyList()
        return userDir.listFiles()
            ?.mapNotNull { dir ->
                val meta = File(dir, "meta.json")
                if (!meta.exists()) return@mapNotNull null
                val obj = JSONObject(meta.readText())
                SavedAnalysisSummary(
                    id = obj.optLong("id"),
                    username = obj.optString("username"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    savedAtMs = obj.optLong("savedAtMs")
                )
            }
            ?.sortedByDescending { it.savedAtMs }
            ?: emptyList()
    }

    fun load(username: String, id: Long): SavedAnalysisSnapshot? {
        val itemDir = File(File(baseDir, username), id.toString())
        val meta = File(itemDir, "meta.json")
        if (!meta.exists()) return null
        val obj = JSONObject(meta.readText())
        val original = BitmapFactory.decodeFile(File(itemDir, "original.png").absolutePath) ?: return null
        val mask = BitmapFactory.decodeFile(File(itemDir, "mask.png").absolutePath) ?: return null
        val yolo = BitmapFactory.decodeFile(File(itemDir, "yolo.png").absolutePath) ?: return null
        val logs = mutableListOf<SavedLogMetric>()
        val logsArr = obj.optJSONArray("logs") ?: JSONArray()
        for (i in 0 until logsArr.length()) {
            val log = logsArr.optJSONObject(i) ?: continue
            logs.add(
                SavedLogMetric(
                    index = log.optInt("index"),
                    diameterCm = log.optDouble("diameterCm").toFloat(),
                    volumeM3 = log.optDouble("volumeM3").toFloat()
                )
            )
        }
        val tileBounds = mutableListOf<LogDetector.TileBounds>()
        val boundsArr = obj.optJSONArray("tileBounds") ?: JSONArray()
        for (i in 0 until boundsArr.length()) {
            val b = boundsArr.optJSONObject(i) ?: continue
            tileBounds.add(
                LogDetector.TileBounds(
                    left = b.optInt("left"),
                    top = b.optInt("top"),
                    right = b.optInt("right"),
                    bottom = b.optInt("bottom")
                )
            )
        }
        return SavedAnalysisSnapshot(
            summary = SavedAnalysisSummary(
                id = obj.optLong("id"),
                username = obj.optString("username"),
                name = obj.optString("name"),
                description = obj.optString("description"),
                savedAtMs = obj.optLong("savedAtMs")
            ),
            totalLogs = obj.optInt("totalLogs"),
            totalAreaCm2 = obj.optDouble("totalAreaCm2").toFloat(),
            totalVolumeM3 = obj.optDouble("totalVolumeM3").toFloat(),
            averageDiameterCm = obj.optDouble("averageDiameterCm").toFloat(),
            logs = logs,
            tileBounds = tileBounds,
            originalBitmap = original,
            maskBitmap = mask,
            yoloBitmap = yolo
        )
    }

    fun delete(username: String, id: Long): Result<Unit> {
        return runCatching {
            val itemDir = File(File(baseDir, username), id.toString())
            if (itemDir.exists()) {
                itemDir.deleteRecursively()
            }
        }
    }

    private fun writeBitmap(file: File, bitmap: Bitmap) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }
}
