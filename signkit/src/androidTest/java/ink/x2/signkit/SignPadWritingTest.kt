package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
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
class SignPadWritingTest {
    @Test
    fun writingNotifiesStartMoveAndEndInOrder() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 40f)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 200f, 40f)
        assertEquals(listOf("start", "writing", "end"), events)
        assertTrue(pad.canUndo)
    }

    @Test
    fun historicalMoveReturningToStartStillNotifiesWritingOnce() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        val dot = pad.exportBitmap()
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, 100f, 40f, 0)
        try {
            event.addBatch(time + 10, 20f, 40f, 1f, 1f, 0)
            assertTrue(pad.onTouchEvent(event))
            assertEquals(listOf("start", "writing"), events)
            assertFalse(dot.sameAs(pad.exportBitmap()))
        } finally {
            event.recycle()
        }
    }

    @Test
    fun tapOnlyNotifiesStartAndEnd() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 40f, 40f)
        assertEquals(listOf("start", "end"), events)
    }

    @Test
    fun cancelEndsOnceAndKeepsUndoableStroke() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 40f)
        touch(pad, MotionEvent.ACTION_CANCEL, 100f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 100f, 40f)
        assertEquals(listOf("start", "writing", "end"), events)
        assertTrue(pad.canUndo)
        assertTrue(pad.undo())
        assertEquals(3, events.size)
    }

    @Test
    fun clearEndsCurrentStrokeOnceBeforeTailEvents() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        pad.clear()
        pad.clear()
        pad.undo()
        touch(pad, MotionEvent.ACTION_UP, 100f, 40f)
        assertEquals(listOf("start", "end"), events)
        assertFalse(pad.canUndo)
    }

    @Test
    fun undoEndsCurrentStrokeOnceBeforeTailEvents() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        assertTrue(pad.undo())
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 200f, 40f)
        assertEquals(listOf("start", "end"), events)
        assertFalse(pad.canUndo)
    }

    @Test
    fun endCallbackCanClearWithoutReceivingAnotherEnd() = withPad { pad ->
        var endings = 0
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteEnd(view: SignPadView) {
                endings++
                view.clear()
            }
        })
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 40f, 40f)
        assertEquals(1, endings)
        assertFalse(pad.canUndo)
    }

    @Test
    fun removingListenerStopsCallbacks() = withPad { pad ->
        val events = recordEvents(pad)
        pad.setOnWriteListener(null)
        stroke(pad, 40f)
        assertTrue(events.isEmpty())
    }

    @Test
    fun widthIsLockedPerStrokeAndPreservedByUndo() = withPad { pad ->
        pad.strokeWidth = 2f
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 40f)
        pad.strokeWidth = 16f
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 200f, 40f)
        val first = pad.exportBitmap()
        assertTrue(columnInk(first, 100, 20..60) <= 4)
        pad.strokeWidth = 20f
        assertTrue(first.sameAs(pad.exportBitmap()))
        stroke(pad, 140f)
        val combined = pad.exportBitmap()
        assertTrue(columnInk(combined, 100, 120..160) >= 18)
        assertEquals(columnInk(first, 100, 20..60), columnInk(combined, 100, 20..60))
        assertTrue(pad.undo())
        assertTrue(first.sameAs(pad.exportBitmap()))
    }

    @Test
    fun invalidWidthIsRejectedWithoutChangingCurrentWidth() = withPad { pad ->
        val original = pad.strokeWidth
        for (width in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            try {
                pad.strokeWidth = width
                fail("Expected invalid width to be rejected: $width")
            } catch (expected: IllegalArgumentException) {
                assertEquals(original, pad.strokeWidth, 0f)
            }
        }
    }

    @Test
    fun midpointCurveRoundsCornerAndPreservesEndpoints() = withPad { pad ->
        pad.strokeWidth = 4f
        touch(pad, MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 20f)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, 100f)
        touch(pad, MotionEvent.ACTION_UP, 180f, 100f)
        val bitmap = pad.exportBitmap()
        assertEquals(0, Color.alpha(bitmap.getPixel(100, 20)))
        assertTrue(Color.alpha(bitmap.getPixel(90, 30)) > 0)
        assertTrue(Color.alpha(bitmap.getPixel(20, 20)) > 0)
        assertTrue(Color.alpha(bitmap.getPixel(180, 100)) > 0)
    }

    @Test
    fun subThresholdJitterDoesNotCreateSegmentsOrWritingCallbacks() = withPad { pad ->
        val events = recordEvents(pad)
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        val dot = pad.exportBitmap()
        val jitter = pad.resources.displayMetrics.density * 0.25f
        touch(pad, MotionEvent.ACTION_MOVE, 40f + jitter, 40f + jitter)
        touch(pad, MotionEvent.ACTION_MOVE, 40f - jitter, 40f - jitter)
        assertEquals(listOf("start"), events)
        assertTrue(dot.sameAs(pad.exportBitmap()))
        touch(pad, MotionEvent.ACTION_UP, 40f, 40f)
        assertEquals(listOf("start", "end"), events)
    }

    @Test
    fun releaseKeepsShortMovementEndpointBelowSamplingThreshold() = withPad { pad ->
        pad.strokeWidth = 1f
        val distance = pad.resources.displayMetrics.density
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 40f + distance, 40f)
        assertTrue(Color.alpha(pad.exportBitmap().getPixel((40f + distance).toInt(), 40)) > 0)
    }

    private fun recordEvents(pad: SignPadView): MutableList<String> {
        val events = mutableListOf<String>()
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteStart(view: SignPadView) {
                assertTrue(view.canUndo)
                events.add("start")
            }

            override fun onWriting(view: SignPadView) {
                events.add("writing")
            }

            override fun onWriteEnd(view: SignPadView) {
                events.add("end")
            }
        })
        return events
    }

    private fun columnInk(bitmap: Bitmap, x: Int, rows: IntRange): Int =
        rows.count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 240, 240)
            block(pad)
        }
    }

    private fun stroke(pad: SignPadView, y: Float) {
        touch(pad, MotionEvent.ACTION_DOWN, 20f, y)
        touch(pad, MotionEvent.ACTION_MOVE, 100f, y)
        touch(pad, MotionEvent.ACTION_UP, 200f, y)
    }

    private fun touch(pad: SignPadView, action: Int, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, action, x, y, 0)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }
}
