package com.algerianwood.app.eval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.algerianwood.app.processing.LogDetector
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.charset.StandardCharsets

/**
 * Offline baseline recorder for repeatable evaluation on a device/emulator.
 *
 * Setup:
 * - Place PNG/JPG fixtures under androidTest/assets/evaluation_images/
 * - Optionally commit a small subset; larger sets can be copied locally.
 *
 * Outputs:
 * - Writes CSV summary to instrumentation external cache:
 *     <externalCache>/arwood_eval/baseline_run_<timestamp>.csv
 */
@RunWith(AndroidJUnit4::class)
class LogDetectorBaselineTest {

    private data class Fixture(val name: String, val bitmap: Bitmap)

    @Test
    fun recordBaseline_optionalFixturesPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val fixtures = loadFixtures(context)
        if (fixtures.isEmpty()) {
            // Intentionally a no-op in CI repos without bundled fixtures.
            // See docs/android_evaluation.md for how to populate assets.
            return
        }

        val outDir = context.externalCacheDir?.resolve("arwood_eval") ?: context.cacheDir.resolve("arwood_eval")
        outDir.mkdirs()
        val artDir = outDir.resolve("artifacts_${System.currentTimeMillis()}")
        artDir.mkdirs()
        val outFile = outDir.resolve("baseline_run_${System.currentTimeMillis()}.csv")

        FileWriter(outFile, StandardCharsets.UTF_8).use { fw ->
            BufferedWriter(fw).use { w ->
                w.appendLine(
                    listOf(
                        "imageName",
                        "width",
                        "height",
                        "inferenceMs",
                        "detectedLogs",
                        "avgScoreApprox",
                        "note"
                    ).joinToString(",")
                )

                for (fixture in fixtures) {
                    val t0 = System.nanoTime()

                    val result = LogDetector(context).detectAnalysis(fixture.bitmap)
                    val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(0L)

                    val avgScore = if (result.yoloDetections.isEmpty()) 0f
                    else result.yoloDetections.sumOf { it.score.toDouble() }.toFloat() / result.yoloDetections.size

                    val note = ""

                    // Save debug artifacts (PNG) for visual diffing between baselines.
                    try {
                        val baseName = fixture.name.substringBeforeLast('.')
                        fixture.bitmap.compress(Bitmap.CompressFormat.PNG, 100, artDir.resolve("${baseName}_input.png").outputStream())

                        val overlay = fixture.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val c = Canvas(overlay)
                        val p = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeWidth = maxOf(2f, overlay.width / 350f)
                            // Brand accent (matches app GoldenAmber approx)
                            color = Color.rgb(224, 162, 79)
                            isAntiAlias = true
                        }
                        for (d in result.yoloDetections) {
                            c.drawRect(d.left.toFloat(), d.top.toFloat(), d.right.toFloat(), d.bottom.toFloat(), p)
                        }
                        overlay.compress(Bitmap.CompressFormat.PNG, 100, artDir.resolve("${baseName}_boxes.png").outputStream())

                        result.maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, artDir.resolve("${baseName}_mask.png").outputStream())
                    } catch (_: Throwable) {
                        // Best-effort artifact export only.
                    }

                    fun esc(s: String): String =
                        "\"" + s.replace("\"", "\"\"") + "\""

                    w.appendLine(
                        listOf(
                            esc(fixture.name),
                            fixture.bitmap.width.toString(),
                            fixture.bitmap.height.toString(),
                            ms.toString(),
                            result.logs.size.toString(),
                            String.format("%.4f", avgScore),
                            esc(note)
                        ).joinToString(",")
                    )
                }
            }
        }
    }

    private fun loadFixtures(context: Context): List<Fixture> {
        val am = context.assets
        val base = "evaluation_images"

        val manifest = readManifestNames(context).mapNotNull { line ->
            // manifest entries are filenames only by default
            line.substringAfterLast('/').trim().takeIf { it.isNotBlank() }
        }

        if (manifest.isNotEmpty()) {
            val out = ArrayList<Fixture>(manifest.size)
            for (name in manifest.distinct().sorted()) {
                val lower = name.lowercase()
                if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) continue
                val bmp = try {
                    am.open("$base/$name").use { ins -> BitmapFactory.decodeStream(ins) }
                } catch (_: Throwable) {
                    null
                } ?: continue
                if (bmp.width > 0 && bmp.height > 0) out.add(Fixture(name, bmp))
            }
            return out
        }

        val names = try {
            am.list(base)?.toList().orEmpty().filter {
                val lower = it.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val out = ArrayList<Fixture>(names.size)
        for (name in names.sorted()) {
            val bmp = try {
                am.open("$base/$name").use { ins -> BitmapFactory.decodeStream(ins) }
            } catch (_: Throwable) {
                null
            } ?: continue
            if (bmp.width > 0 && bmp.height > 0) out.add(Fixture(name, bmp))
        }
        return out
    }

    /**
     * Optional manifest list for large local collections (newline separated file names).
     * If assets/evaluation_manifest.txt exists, it overrides directory listing.
     */
    private fun readManifestNames(context: Context): List<String> {
        return try {
            context.assets.open("evaluation_manifest.txt").use { ins ->
                ins.bufferedReader(StandardCharsets.UTF_8).readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
