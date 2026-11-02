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

    private val points = ArrayDeque<StrokeSample>().apply {
        addLast(start)
        addLast(start)
    }
    private var hasMoved = false
    private var hasCommittedCurve = false

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
            appendCurve(target, CubicCurve.through(points.toList()), model)
            points.removeFirst()
            hasCommittedCurve = true
        }
    }

    fun appendTail(target: Path) {
        if (!hasMoved) {
            val point = points.last()
            target.addCircle(point.x, point.y, model.tapWidth / 2f, Path.Direction.CW)
            return
        }
        // 补齐末段用快照，预览与导出不得推进窗口或速度滤波状态。
        val tail = points.toMutableList().apply { add(last()) }
        appendCurve(target, CubicCurve.through(tail), model.snapshot(), allowGrowth = !hasCommittedCurve)
    }

    private fun appendCurve(
        target: Path,
        curve: CubicCurve,
        widthModel: StrokeWidthModel,
        allowGrowth: Boolean = true,
    ) {
        val startWidth = widthModel.width
        val endWidth = widthModel.accept(curve.end)
        val growth = if (widthModel.usesVelocity) {
            if (allowGrowth) 0.5f else 0f
        } else {
            null
        }
        widthModel.commitRenderedWidth(renderCurve(target, curve, WidthTransition(startWidth, endWidth, growth)))
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
