package ink.x2.signkit

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadInputTest {
    @Test
    fun reportsUnfilteredFingerPressureAndSeparatesRelease() = withPad { pad ->
        val inputs = mutableListOf<SignPadView.InputInfo>()
        pad.setOnInputListener { inputs.add(it) }
        touch(pad, MotionEvent.ACTION_DOWN, 1f)
        touch(pad, MotionEvent.ACTION_MOVE, 0.999f)
        touch(pad, MotionEvent.ACTION_UP, 0f)
        assertEquals(listOf(1f, 0.999f, 0f), inputs.map { it.pressure })
        assertEquals(listOf(true, true, false), inputs.map { it.isContact })
        assertTrue(inputs.all { it.toolType == MotionEvent.TOOL_TYPE_FINGER })
    }

    @Test
    fun reportsAllHistoricalPressuresAndCanRemoveListener() = withPad { pad ->
        val pressures = mutableListOf<Float>()
        pad.setOnInputListener { pressures.add(it.pressure) }
        touch(pad, MotionEvent.ACTION_DOWN, 0.2f)
        val event = event(MotionEvent.ACTION_MOVE, 0.3f)
        try {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 40f; y = 40f; pressure = 0.8f })
            event.addBatch(1020L, coords, 0)
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
        assertEquals(listOf(0.2f, 0.3f, 0.8f), pressures)
        pad.setOnInputListener(null)
        touch(pad, MotionEvent.ACTION_UP, 0f)
        assertEquals(3, pressures.size)
        pad.isEnabled = false
        val disabled = event(MotionEvent.ACTION_DOWN, 0.4f)
        try {
            assertFalse(pad.onTouchEvent(disabled))
        } finally {
            disabled.recycle()
        }
    }

    private fun touch(pad: SignPadView, action: Int, pressure: Float) {
        val event = event(action, pressure)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun event(action: Int, pressure: Float): MotionEvent = MotionEvent.obtain(
        1000L, 1010L, action, 1,
        arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
        arrayOf(MotionEvent.PointerCoords().apply { x = 40f; y = 40f; this.pressure = pressure }),
        0, 0, 1f, 1f, 42, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
    )

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 240, 240)
            block(pad)
        }
    }
}
