package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadStartTest {
    @Test
    fun movingStartDoesNotRetainIndependentDownDot() = withPad { pad ->
        for (mode in listOf(SignPadView.PenMode.VELOCITY, SignPadView.PenMode.PRESSURE_AND_VELOCITY)) {
            pad.clear()
            pad.penMode = mode
            touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
            val downDot = pad.exportBitmap()
            pad.clear()
            fastStroke(pad)
            val bitmap = pad.exportBitmap()
            assertTrue(
                "Moving stroke must replace the DOWN preview: $mode",
                thickness(bitmap, 40) < thickness(downDot, 40),
            )
        }
    }

    @Test
    fun combinedStartMatchesFastStrokeBodyAfterPressureOnlySamples() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 0.1f)
        touch(pad, MotionEvent.ACTION_MOVE, 40f, 1020L, 1f)
        val pressed = pad.exportBitmap()
        for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + index * 20f, 1020L + index, 1f)
        touch(pad, MotionEvent.ACTION_UP, 240f, 1031L, 0f)
        val bitmap = pad.exportBitmap()
        assertTrue(thickness(pressed, 40) > thickness(bitmap, 40))
        assertTrue(thickness(bitmap, 40) <= thickness(bitmap, 180) + 2)
    }

    @Test
    fun firstMovePreviewAndReleaseOnlyStrokeHaveNoLargeStartDot() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
        touch(pad, MotionEvent.ACTION_MOVE, 140f, 1001L, 1f)
        val preview = pad.exportBitmap()
        assertTrue(thickness(preview, 40) <= thickness(preview, 100) + 2)
        pad.clear()
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
        touch(pad, MotionEvent.ACTION_UP, 140f, 1001L, 0f)
        val released = pad.exportBitmap()
        assertTrue(preview.sameAs(released))
        assertTrue(Color.alpha(released.getPixel(140, 100)) > 0)
    }

    @Test
    fun cancelBeforeMovementKeepsDotAndCanUndo() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
        val preview = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_CANCEL, 40f, 1010L, 0f)
        assertTrue(preview.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
    }

    @Test
    fun dynamicTapStillLeavesPressureSizedDotAndCanUndo() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
        val blank = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
        val preview = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_UP, 40f, 1020L, 0f)
        assertTrue(thickness(pad.exportBitmap(), 40) >= 23)
        assertTrue(preview.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
    }

    @Test
    fun previewMatchesCompletedFastStrokeAndEveryStrokeResetsStart() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        fastStroke(pad)
        val first = pad.exportBitmap()
        pad.clear()
        fastStroke(pad, release = false)
        val preview = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_UP, 240f, 1011L, 0f)
        assertTrue(preview.sameAs(pad.exportBitmap()))
        assertTrue(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
    }

    private fun fastStroke(pad: SignPadView, release: Boolean = true) {
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 1f)
        for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + index * 20f, 1000L + index, 1f)
        if (release) touch(pad, MotionEvent.ACTION_UP, 240f, 1011L, 0f)
    }

    private fun thickness(bitmap: Bitmap, x: Int): Int =
        (60..140).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }

    private fun touch(pad: SignPadView, action: Int, x: Float, time: Long, pressure: Float) {
        val event = MotionEvent.obtain(1000L, time, action, x, 100f, pressure, 1f, 0, 1f, 1f, 0, 0)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 300, 200)
            pad.strokeWidth = 24f
            block(pad)
        }
    }
}
