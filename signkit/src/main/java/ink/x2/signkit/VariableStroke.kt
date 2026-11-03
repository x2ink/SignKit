/*
 * Curve rendering adapted from android-signaturepad, Copyright 2014-2025 Gianluca Cacace.
 * Apache-2.0; see META-INF/LICENSE-android-signaturepad.
 * Modified for per-stroke vector storage, history, preview and complete endpoints.
 */
package ink.x2.signkit

import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.hypot

internal class VariableStroke(start: StrokeSample, private val model: StrokeWidthModel) {
    private data class WidthTransition(val start: Float, val end: Float, val maxGrowth: Float?)
    private data class PendingCurve(val curve: CubicCurve, val length: Float)

    private val points = ArrayDeque<StrokeSample>().apply {
        addLast(start)
        addLast(start)
    }
    private var hasMoved = false
    private var hasCommittedCurve = false
    private val pendingCurves = ArrayDeque<PendingCurve>()
    private var pendingLength = 0f

    fun append(target: Path, sample: StrokeSample) {
        val last = points.last()
        if (sample.x == last.x && sample.y == last.y) {
            if (!hasMoved) {
                points.clear()
                points.addLast(sample)
                points.addLast(sample)
                model.restart(sample)
                return
            }
            points[points.lastIndex] = sample
            model.updatePressure(sample.pressure)
            return
        }
        if (!hasMoved) model.beginMovement()
        hasMoved = true
        points.addLast(sample)
        if (points.size == 4) {
            queueCurve(target, CubicCurve.through(points.toList()))
            points.removeFirst()
        }
    }

    private fun queueCurve(target: Path, curve: CubicCurve) {
        if (!model.usesVelocity) {
            appendCurve(target, curve, model)
            hasCommittedCurve = true
            return
        }
        val pending = PendingCurve(curve, curve.length())
        pendingCurves.addLast(pending)
        pendingLength += pending.length
        // 收笔减速可能跨越多个MOVE；保留按路程界定的末端，避免粗尾提前永久写入路径。
        while (pendingCurves.isNotEmpty() && pendingLength - pendingCurves.first().length >= model.tailLength) {
            val first = pendingCurves.removeFirst()
            pendingLength -= first.length
            appendCurve(target, first.curve, model)
            hasCommittedCurve = true
        }
    }

    fun appendTail(target: Path) {
        if (!hasMoved) {
            val point = points.last()
            target.addCircle(point.x, point.y, model.tapWidth / 2f, Path.Direction.CW)
            return
        }
        // 预览和完成共用快照，重建整个末端，不推进队列或速度滤波状态。
        val snapshot = model.snapshot()
        var widthLimit = if (hasCommittedCurve) snapshot.width else Float.POSITIVE_INFINITY
        for (pending in pendingCurves) {
            widthLimit = appendCurve(target, pending.curve, snapshot, widthLimit)
        }
        val tail = points.toMutableList().apply { add(last()) }
        appendCurve(target, CubicCurve.through(tail), snapshot, widthLimit)
    }

    private fun appendCurve(
        target: Path,
        curve: CubicCurve,
        widthModel: StrokeWidthModel,
        widthLimit: Float = Float.POSITIVE_INFINITY,
    ): Float {
        val startWidth = widthModel.width
        val requestedWidth = widthModel.accept(curve.end)
        // 首段按速度目标建立笔身上限；末端目标变细后不能因减速再次抬高上限。
        val endWidth = if (widthModel.usesVelocity) minOf(requestedWidth, widthLimit) else requestedWidth
        val growth = if (widthModel.usesVelocity) 0.5f else null
        widthModel.commitRenderedWidth(renderCurve(target, curve, WidthTransition(startWidth, endWidth, growth)))
        return endWidth
    }

    private fun renderCurve(target: Path, curve: CubicCurve, transition: WidthTransition): Float {
        val steps = ceil(curve.length()).toInt().coerceAtLeast(1)
        var width = transition.start
        var previousX = curve.start.x
        var previousY = curve.start.y
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val x = curve.x(t)
            val y = curve.y(t)
            val distance = hypot(x - previousX, y - previousY)
            val requestedWidth = curveWidth(transition.start, transition.end, t)
            // 限制半径相对真实行进的增长，避免短段/停笔把粗圆堆在端点。
            width = transition.maxGrowth?.let { minOf(requestedWidth, width + distance * it) } ?: requestedWidth
            target.addCircle(x, y, width / 2f, Path.Direction.CW)
            previousX = x
            previousY = y
        }
        return width
    }
}
