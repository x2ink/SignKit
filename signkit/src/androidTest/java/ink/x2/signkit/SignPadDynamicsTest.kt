package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadDynamicsTest {
    private data class Sample(val x: Float, val y: Float, val time: Long, val pressure: Float)

    @Test
    fun greaterPressureProducesThickerInk() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val light = stroke(pad, pressure = 0.1f)
        pad.clear()
        val firm = stroke(pad, pressure = 0.9f)
        assertTrue(thickness(firm) > thickness(light) + 4)
    }

    @Test
    fun pressureChangesWidthInsideOneContinuousStroke() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val samples = samples(0.1f, 20).mapIndexed { index, sample ->
            sample.copy(pressure = if (index < 5) 0.1f else 0.9f)
        }
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        samples.drop(1).dropLast(1).forEach { touch(pad, MotionEvent.ACTION_MOVE, it) }
        touch(pad, MotionEvent.ACTION_UP, samples.last())
        val bitmap = pad.exportBitmap()
        val earlyWidth = (60..140).count { Color.alpha(bitmap.getPixel(70, it)) > 0 }
        val lateWidth = (60..140).count { Color.alpha(bitmap.getPixel(190, it)) > 0 }
        assertTrue(lateWidth > earlyWidth + 4)
        assertTrue((20..220).all { Color.alpha(bitmap.getPixel(it, 100)) > 0 })
    }

    @Test
    fun fasterWritingProducesThinnerInk() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        val slow = stroke(pad, interval = 200)
        pad.clear()
        val fast = stroke(pad, interval = 1)
        assertTrue(thickness(slow) > thickness(fast) + 4)
        assertTrue(thickness(fast) >= pad.strokeWidth * pad.minStrokeWidthRatio - 2f)
    }

    @Test
    fun combinedModeRespondsToBothPressureAndSpeed() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        val firmSlow = stroke(pad, pressure = 0.9f, interval = 200)
        pad.clear()
        val lightSlow = stroke(pad, pressure = 0.1f, interval = 200)
        pad.clear()
        val firmFast = stroke(pad, pressure = 0.9f, interval = 1)
        assertTrue(thickness(firmSlow) > thickness(lightSlow))
        assertTrue(thickness(firmSlow) > thickness(firmFast))
    }

    @Test
    fun fixedModeIgnoresPressureAndSpeed() = withPad { pad ->
        assertEquals(SignPadView.PenMode.FIXED, pad.penMode)
        val first = stroke(pad, pressure = 0.1f, interval = 200)
        pad.clear()
        assertTrue(first.sameAs(stroke(pad, pressure = 0.9f, interval = 1)))
    }

    @Test
    fun batchedHistoricalSamplesMatchIndividualMoves() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        val samples = samples(0.8f, 25).mapIndexed { index, sample ->
            sample.copy(pressure = 0.1f + index * 0.08f, time = 1000L + index * index * 10L)
        }
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        samples.drop(1).dropLast(1).forEach { touch(pad, MotionEvent.ACTION_MOVE, it) }
        touch(pad, MotionEvent.ACTION_UP, samples.last())
        val individual = pad.exportBitmap()
        pad.clear()
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        val moves = samples.drop(1).dropLast(1)
        val event = event(MotionEvent.ACTION_MOVE, moves.first())
        try {
            moves.drop(1).forEach {
                event.addBatch(it.time, it.x, it.y, it.pressure, 1f, 0)
            }
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
        touch(pad, MotionEvent.ACTION_UP, samples.last())
        assertTrue(individual.sameAs(pad.exportBitmap()))
    }

    @Test
    fun settingsRemainLockedUntilNextStroke() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val expected = stroke(pad, pressure = 0.4f)
        pad.clear()
        val samples = samples(0.4f, 20)
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        pad.penMode = SignPadView.PenMode.VELOCITY
        pad.strokeWidth = 40f
        pad.minStrokeWidthRatio = 0.8f
        pad.velocityFilterWeight = 0.2f
        samples.drop(1).dropLast(1).forEach { touch(pad, MotionEvent.ACTION_MOVE, it) }
        touch(pad, MotionEvent.ACTION_UP, samples.last())
        assertTrue(expected.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertFalse(expected.sameAs(stroke(pad, pressure = 0.4f)))
    }

    @Test
    fun dynamicHistorySurvivesSettingsChangesAndUndo() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val first = stroke(pad, pressure = 0.3f)
        pad.strokeWidth = 40f
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        assertTrue(first.sameAs(pad.exportBitmap()))
        stroke(pad, pressure = 0.9f)
        assertFalse(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(first.sameAs(pad.exportBitmap()))
        pad.clear()
        assertFalse(pad.canUndo)
        assertEquals(SignPadView.PenMode.PRESSURE_AND_VELOCITY, pad.penMode)
        assertEquals(40f, pad.strokeWidth, 0f)
    }

    @Test
    fun pressureIsClampedAndNonFinitePressureFallsBack() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val maximum = stroke(pad, pressure = 1f)
        for (pressure in listOf(2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            pad.clear()
            assertTrue(maximum.sameAs(stroke(pad, pressure)))
        }
        pad.clear()
        val minimum = stroke(pad, pressure = 0f)
        pad.clear()
        assertTrue(minimum.sameAs(stroke(pad, pressure = -1f)))
    }

    @Test
    fun liftingWithZeroPressureKeepsFinalEndpoint() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        val samples = samples(0.8f, 20)
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        samples.drop(1).dropLast(1).forEach { touch(pad, MotionEvent.ACTION_MOVE, it) }
        touch(pad, MotionEvent.ACTION_UP, samples.last().copy(pressure = 0f))
        val zeroRelease = pad.exportBitmap()
        assertTrue(Color.alpha(zeroRelease.getPixel(220, 100)) > 0)
        pad.clear()
        assertTrue(zeroRelease.sameAs(stroke(pad, pressure = 0.8f)))
    }

    @Test
    fun pressureCanRecoverFromInvalidStartWithoutPositionChange() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        var updates = 0
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriting(view: SignPadView) {
                updates++
            }
        })
        touch(pad, MotionEvent.ACTION_DOWN, Sample(40f, 100f, 1000L, Float.NaN))
        touch(pad, MotionEvent.ACTION_MOVE, Sample(40f, 100f, 1020L, 0.2f))
        assertEquals(1, updates)
        touch(pad, MotionEvent.ACTION_UP, Sample(40f, 100f, 1040L, 0f))
        assertTrue(pad.canUndo)
    }

    @Test
    fun pressingWithoutMovingAddsInkAndNotifiesWriting() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        touch(pad, MotionEvent.ACTION_DOWN, Sample(40f, 100f, 1000L, 0.1f))
        val light = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_MOVE, Sample(40f, 100f, 1020L, 0.9f))
        val firm = pad.exportBitmap()
        assertTrue((60..140).count { Color.alpha(firm.getPixel(40, it)) > 0 } >
            (60..140).count { Color.alpha(light.getPixel(40, it)) > 0 })
    }

    @Test
    fun invalidDynamicsSettingsAreRejected() = withPad { pad ->
        val invalid = listOf(0f, -1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)
        for (value in invalid) {
            rejects { pad.minStrokeWidthRatio = value }
            rejects { pad.velocityFilterWeight = value }
        }
        assertEquals(3f / 7f, pad.minStrokeWidthRatio, 0f)
        assertEquals(0.9f, pad.velocityFilterWeight, 0f)
    }

    private fun stroke(pad: SignPadView, pressure: Float = 1f, interval: Long = 20): Bitmap {
        val samples = samples(pressure, interval)
        touch(pad, MotionEvent.ACTION_DOWN, samples.first())
        samples.drop(1).dropLast(1).forEach { touch(pad, MotionEvent.ACTION_MOVE, it) }
        touch(pad, MotionEvent.ACTION_UP, samples.last())
        return pad.exportBitmap()
    }

    private fun samples(pressure: Float, interval: Long): List<Sample> =
        (0..10).map { index -> Sample(20f + index * 20f, 100f, 1000L + index * interval, pressure) }

    private fun thickness(bitmap: Bitmap): Int =
        (60..140).count { y -> Color.alpha(bitmap.getPixel(170, y)) > 0 }

    private fun touch(pad: SignPadView, action: Int, sample: Sample) {
        val event = event(action, sample)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun event(action: Int, sample: Sample): MotionEvent = MotionEvent.obtain(
        1000L, sample.time, action, sample.x, sample.y, sample.pressure, 1f, 0, 1f, 1f, 0, 0,
    )

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotBlank() == true)
        }
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 260, 200)
            pad.strokeWidth = 24f
            block(pad)
        }
    }
}
