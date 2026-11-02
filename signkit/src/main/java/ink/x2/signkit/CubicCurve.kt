/*
 * Adapted from android-signaturepad, Copyright 2014-2025 Gianluca Cacace.
 * Apache-2.0; see META-INF/LICENSE-android-signaturepad.
 * Modified for immutable Kotlin samples and event timestamps.
 */
package ink.x2.signkit

import kotlin.math.hypot

internal data class CurvePoint(val x: Float, val y: Float)

internal data class CubicCurve(
    val start: StrokeSample,
    val control1: CurvePoint,
    val control2: CurvePoint,
    val end: StrokeSample,
) {
    fun x(t: Float): Float = coordinate(t, start.x, control1.x, control2.x, end.x)
    fun y(t: Float): Float = coordinate(t, start.y, control1.y, control2.y, end.y)

    fun length(): Float {
        var length = 0f
        var previous = CurvePoint(start.x, start.y)
        for (step in 1..10) {
            val t = step / 10f
            val current = CurvePoint(x(t), y(t))
            length += hypot(current.x - previous.x, current.y - previous.y)
            previous = current
        }
        return length
    }

    companion object {
        fun through(points: List<StrokeSample>): CubicCurve {
            require(points.size == 4)
            val first = controls(points[0], points[1], points[2])
            val second = controls(points[1], points[2], points[3])
            return CubicCurve(points[1], first.second, second.first, points[2])
        }

        private fun controls(a: StrokeSample, b: StrokeSample, c: StrokeSample): Pair<CurvePoint, CurvePoint> {
            val m1 = CurvePoint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val m2 = CurvePoint((b.x + c.x) / 2f, (b.y + c.y) / 2f)
            val length1 = hypot(a.x - b.x, a.y - b.y)
            val length2 = hypot(b.x - c.x, b.y - c.y)
            val ratio = if (length1 + length2 > 0f) length2 / (length1 + length2) else 0f
            val center = CurvePoint(m2.x + (m1.x - m2.x) * ratio, m2.y + (m1.y - m2.y) * ratio)
            val dx = b.x - center.x
            val dy = b.y - center.y
            return CurvePoint(m1.x + dx, m1.y + dy) to CurvePoint(m2.x + dx, m2.y + dy)
        }

        private fun coordinate(t: Float, start: Float, c1: Float, c2: Float, end: Float): Float {
            val u = 1f - t
            return u * u * u * start + 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t * end
        }
    }
}

internal fun curveWidth(start: Float, end: Float, t: Float): Float = start + t * t * t * (end - start)
