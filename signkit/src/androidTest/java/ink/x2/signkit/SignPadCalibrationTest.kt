package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadCalibrationTest {
    @Test
    fun typicalWritingSpeedsProduceVisibleContrast() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        val slow = stroke(pad, interval = 24L)
        pad.clear()
        val fast = stroke(pad, interval = 4L)
        assertTrue(thickness(slow, pad) > thickness(fast, pad))
    }

    @Test
    fun responseReferenceIsLockedPerStrokeAndAffectsNextStroke() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        pad.velocityReference = 0.75f
        val first = stroke(pad, interval = 12L)
        pad.clear()
        val locked = stroke(pad, interval = 12L) { pad.velocityReference = 3f }
        assertTrue(first.sameAs(locked))
        pad.clear()
        val next = stroke(pad, interval = 12L)
        assertTrue(thickness(next, pad) > thickness(first, pad) + 4)
    }

    @Test
    fun invalidResponseReferencesAreRejected() = withPad { pad ->
        val original = pad.velocityReference
        for (value in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            try {
                pad.velocityReference = value
                fail("Invalid reference must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertEquals(original, pad.velocityReference, 0f)
            }
        }
    }

    @Test
    fun lowPressureRetainsVisibleSpeedVariation() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        val slow = stroke(pad, interval = 60L)
        pad.clear()
        val fast = stroke(pad, interval = 2L)
        val difference = thickness(slow, pad) - thickness(fast, pad)
        assertTrue("Low-pressure speed contrast was only $difference px", difference >= 5)
    }

    @Test
    fun maximumWidthChangesVisibleBodyThickness() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        pad.strokeWidth = 12f
        val small = stroke(pad, interval = 12L)
        pad.clear()
        pad.strokeWidth = 48f
        val large = stroke(pad, interval = 12L)
        assertTrue(thickness(large, pad) >= thickness(small, pad) * 3 - 2)
        assertTrue(thickness(large, pad) >= 20)
    }

    private fun stroke(pad: SignPadView, interval: Long, afterDown: (() -> Unit)? = null): Bitmap {
        val density = pad.resources.displayMetrics.density
        for (index in 0..12) {
            val action = when (index) {
                0 -> MotionEvent.ACTION_DOWN
                12 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(
                1000L, 1000L + interval * index, action,
                40f + index * 12f * density, 80f, 0.05f, 1f, 0, 1f, 1f, 0, 0,
            )
            try {
                assertTrue(pad.onTouchEvent(event))
                if (index == 0) afterDown?.invoke()
            } finally {
                event.recycle()
            }
        }
        return pad.exportBitmap()
    }

    private fun thickness(bitmap: Bitmap, pad: SignPadView): Int {
        val x = (40f + 60f * pad.resources.displayMetrics.density).toInt()
        return (30..130).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 1200, 160)
            pad.strokeWidth = 32f
            // 使用固定dp/ms尺度，像素对比断言不能依赖测试设备的屏幕密度。
            pad.velocityReference = 1f
            block(pad)
        }
    }
}
