/*
 * Velocity mapping adapted from android-signaturepad, Copyright 2014-2025 Gianluca Cacace.
 * Apache-2.0; see META-INF/LICENSE-android-signaturepad.
 * Modified for event timestamps, per-stroke settings and independent pressure modes.
 */
package ink.x2.signkit

import kotlin.math.hypot

internal data class StrokeSample(val x: Float, val y: Float, val time: Long, val pressure: Float)

internal fun normalizedPressure(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 1f

internal data class PenSettings(
    val width: Float,
    val mode: SignPadView.PenMode,
    val minWidthRatio: Float,
    val velocityWeight: Float,
    val velocityReference: Float,
)

internal class StrokeWidthModel(private val settings: PenSettings, private val density: Float) {
    private var previous: StrokeSample? = null
    private var velocity = 0f
    private var pressure = 1f
    private val minimumWidth = settings.width * settings.minWidthRatio
    var width: Float = (minimumWidth + settings.width) / 2f
        private set

    val usesVelocity: Boolean
        get() = settings.mode == SignPadView.PenMode.VELOCITY ||
            settings.mode == SignPadView.PenMode.PRESSURE_AND_VELOCITY

    val tailLength: Float
        get() = settings.width * 2f

    fun beginMovement() {
        if (usesVelocity) width = minimumWidth
    }

    fun commitRenderedWidth(value: Float) {
        width = value
    }

    val tapWidth: Float
        get() = when (settings.mode) {
            SignPadView.PenMode.FIXED -> settings.width
            // 无位移时没有速度依据，预览和点按必须与移动起笔同宽，避免先粗后细。
            SignPadView.PenMode.VELOCITY, SignPadView.PenMode.PRESSURE_AND_VELOCITY -> minimumWidth
            SignPadView.PenMode.PRESSURE -> pressureWidth()
        }

    fun updatePressure(value: Float) {
        pressure = normalizedPressure(value)
    }

    fun restart(sample: StrokeSample) {
        previous = null
        velocity = 0f
        width = (minimumWidth + settings.width) / 2f
        accept(sample)
    }

    fun accept(sample: StrokeSample): Float {
        val last = previous
        updatePressure(sample.pressure)
        if (last != null) {
            val elapsed = (sample.time - last.time).coerceAtLeast(1L).toFloat()
            val speed = hypot(sample.x - last.x, sample.y - last.y) / elapsed
            velocity = settings.velocityWeight * speed + (1f - settings.velocityWeight) * velocity
            width = targetWidth()
        } else if (settings.mode == SignPadView.PenMode.PRESSURE) {
            width = pressureWidth()
        }
        previous = sample
        return width
    }

    fun snapshot(): StrokeWidthModel = StrokeWidthModel(settings, density).also {
        it.previous = previous
        it.velocity = velocity
        it.pressure = pressure
        it.width = width
    }

    private fun pressureWidth(): Float = minimumWidth + (settings.width - minimumWidth) * pressure

    private fun targetWidth(): Float {
        val referencePixels = settings.velocityReference * density
        val speedWidth = maxOf(settings.width / (1f + velocity / referencePixels), minimumWidth)
        return when (settings.mode) {
            SignPadView.PenMode.FIXED -> settings.width
            SignPadView.PenMode.VELOCITY -> speedWidth
            SignPadView.PenMode.PRESSURE -> pressureWidth()
            SignPadView.PenMode.PRESSURE_AND_VELOCITY -> (speedWidth + pressureWidth()) / 2f
        }
    }
}
