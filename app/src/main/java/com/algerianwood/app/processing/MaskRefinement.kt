package com.algerianwood.app.processing

import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal object MaskRefinement {

    data class RefinedMaskStats(
        val ok: Boolean,
        val area: Int,
        val cx: Int,
        val cy: Int,
        val bbLeft: Int,
        val bbTop: Int,
        val bbRight: Int,
        val bbBottom: Int,
    )

    /**
     * @param mask Binary mask sized [cropW*cropH] (nonzero = foreground).
     *
     * Mutates `mask` in-place so only the chosen component remains foreground.
     */
    fun refineLogEndMask(
        cropW: Int,
        cropH: Int,
        mask: ByteArray,
        anchorX: Float,
        anchorY: Float,
        openCloseIters: Int = 0
    ): RefinedMaskStats {
        require(cropW > 2 && cropH > 2)
        require(mask.size == cropW * cropH)

        morphOpenThenClose(mask, cropW, cropH, iterations = max(0, openCloseIters))

        val ax = round(anchorX).toInt().coerceIn(0, cropW - 1)
        val ay = round(anchorY).toInt().coerceIn(0, cropH - 1)

        data class Component(
            val pix: IntArray,
            val area: Int,
            val cx: Float,
            val cy: Float,
            val minX: Int,
            val minY: Int,
            val maxX: Int,
            val maxY: Int,
            val overlapsAnchor: Boolean,
            val dist2: Float,
        )

        fun bfs(seed: Int, visited: BooleanArray): Component? {
            if (mask[seed] == 0.toByte()) return null

            val q = ArrayDeque<Int>(512)
            q.addLast(seed)
            visited[seed] = true

            val buf = ArrayList<Int>(512)

            var area = 0
            var sx = 0L
            var sy = 0L
            var minX = cropW - 1
            var minY = cropH - 1
            var maxX = 0
            var maxY = 0

            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

            while (q.isNotEmpty()) {
                val cur = q.removeFirst()
                val x = cur % cropW
                val y = cur / cropW

                buf.add(cur)
                area++
                sx += x
                sy += y

                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)

                for (d in dirs) {
                    val nx = x + d[0]
                    val ny = y + d[1]
                    if (nx < 0 || ny < 0 || nx >= cropW || ny >= cropH) continue
                    val nxt = ny * cropW + nx
                    if (visited[nxt]) continue
                    if (mask[nxt] == 0.toByte()) continue
                    visited[nxt] = true
                    q.addLast(nxt)
                }
            }

            val cx = sx.toFloat() / area.toFloat()
            val cy = sy.toFloat() / area.toFloat()
            val overlaps = ax in minX..maxX && ay in minY..maxY
            val dx = cx - anchorX
            val dy = cy - anchorY
            val dist2 = dx * dx + dy * dy

            return Component(
                pix = buf.toIntArray(),
                area = area,
                cx = cx,
                cy = cy,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                overlapsAnchor = overlaps,
                dist2 = dist2
            )
        }

        val visited = BooleanArray(mask.size)
        var best: Component? = null

        for (y in 0 until cropH) {
            val row = y * cropW
            for (x in 0 until cropW) {
                val idx = row + x
                if (visited[idx]) continue
                if (mask[idx] == 0.toByte()) continue
                val comp = bfs(idx, visited) ?: continue

                val cur = best
                if (cur == null) {
                    best = comp
                    continue
                }

                fun better(a: Component, b: Component): Boolean {
                    val ao = if (a.overlapsAnchor) 1 else 0
                    val bo = if (b.overlapsAnchor) 1 else 0
                    if (ao != bo) return ao > bo

                    if (a.area != b.area) return a.area > b.area

                    // Tie-break toward anchor when areas are extremely close (merged blobs / overlaps)
                    if (abs(a.area - b.area) <= max(50, min(a.area, b.area) / 20)) {
                        return a.dist2 < b.dist2
                    }

                    return false
                }

                if (better(comp, cur)) {
                    best = comp
                }
            }
        }

        val pick = best ?: run {
            // Clear mask entirely on failure.
            mask.fill(0)
            return RefinedMaskStats(false, 0, ax, ay, 0, 0, cropW - 1, cropH - 1)
        }

        // Keep only picked component pixels.
        val keep = BooleanArray(mask.size)
        var area = pick.pix.size
        for (idx in pick.pix) keep[idx] = true

        for (i in mask.indices) {
            mask[i] = if (keep[i]) 1 else 0
        }

        val cxInt = round(pick.cx).toInt().coerceIn(pick.minX, pick.maxX)
        val cyInt = round(pick.cy).toInt().coerceIn(pick.minY, pick.maxY)

        return RefinedMaskStats(
            ok = true,
            area = area,
            cx = cxInt,
            cy = cyInt,
            bbLeft = pick.minX,
            bbTop = pick.minY,
            bbRight = pick.maxX,
            bbBottom = pick.maxY,
        )
    }

    private fun morphOpenThenClose(mask: ByteArray, w: Int, h: Int, iterations: Int) {
        repeat(iterations) {
            erode3(mask, w, h)
            dilate3(mask, w, h)
        }
        repeat(iterations) {
            dilate3(mask, w, h)
            erode3(mask, w, h)
        }
    }

    private fun dilate3(mask: ByteArray, w: Int, h: Int) {
        val src = mask.copyOf()
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                var on = src[i] != 0.toByte()
                if (!on) {
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val j = i + dy * w + dx
                            if (src[j] != 0.toByte()) {
                                on = true
                                break
                            }
                        }
                        if (on) break
                    }
                }
                mask[i] = if (on) 1 else 0
            }
        }
    }

    private fun erode3(mask: ByteArray, w: Int, h: Int) {
        val src = mask.copyOf()
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                var all = src[i] != 0.toByte()
                if (all) {
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val j = i + dy * w + dx
                            if (src[j] == 0.toByte()) {
                                all = false
                                break
                            }
                        }
                        if (!all) break
                    }
                }
                mask[i] = if (all) 1 else 0
            }
        }
    }
}
