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
class SignPadContactWidthTest {
    @Test
    fun contactDotNeverExceedsFastHorizontalBody() = withPad { pad ->
        for (mode in velocityModes) {
            for (width in listOf(24f, 64f)) {
                for (ratio in listOf(3f / 7f, 0.25f, 1f)) {
                    for (pressure in listOf(0.05f, 1f)) {
                        pad.clear()
                        pad.penMode = mode
                        pad.strokeWidth = width
                        pad.minStrokeWidthRatio = ratio
                        touch(pad, MotionEvent.ACTION_DOWN, 60f, 1000L, pressure)
                        val contact = pad.exportBitmap()
                        touch(pad, MotionEvent.ACTION_MOVE, 60f, 1200L, pressure)
                        assertTrue("Stationary contact must not resize", contact.sameAs(pad.exportBitmap()))
                        touch(pad, MotionEvent.ACTION_UP, 60f, 1201L, 0f)
                        assertTrue("Finishing tap must not resize", contact.sameAs(pad.exportBitmap()))
                        pad.clear()
                        drawFastLine(pad, pressure)
                        val body = thickness(pad.exportBitmap(), 200)
                        val dot = thickness(contact, 60)
                        assertTrue(
                            "$mode width=$width ratio=$ratio pressure=$pressure dot=$dot body=$body",
                            dot <= body + 2,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun firstMovementDoesNotShrinkContactAfterStationaryPressureChanges() = withPad { pad ->
        for (mode in velocityModes) {
            pad.clear()
            pad.penMode = mode
            touch(pad, MotionEvent.ACTION_DOWN, 60f, 1000L, 0.05f)
            val down = pad.exportBitmap()
            touch(pad, MotionEvent.ACTION_MOVE, 60f, 1200L, 1f)
            val held = pad.exportBitmap()
            touch(pad, MotionEvent.ACTION_MOVE, 160f, 1201L, 1f)
            val moving = pad.exportBitmap()
            assertTrue("$mode stationary pressure must not create a large speed-mode dot", down.sameAs(held))
            assertTrue("$mode contact must match moving start", thickness(held, 60) <= thickness(moving, 60) + 2)
            touch(pad, MotionEvent.ACTION_CANCEL, 160f, 1220L, 0f)
            assertTrue(moving.sameAs(pad.exportBitmap()))
        }
    }

    @Test
    fun tapWidthUsesLockedSettingsAndUndoRestoresBlank() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        val blank = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_DOWN, 60f, 1000L, 1f)
        val down = pad.exportBitmap()
        pad.strokeWidth = 64f
        pad.minStrokeWidthRatio = 0.8f
        touch(pad, MotionEvent.ACTION_UP, 60f, 1020L, 0f)
        assertTrue(down.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
        touch(pad, MotionEvent.ACTION_DOWN, 60f, 2000L, 1f)
        assertTrue(thickness(pad.exportBitmap(), 60) > thickness(down, 60) * 2)
    }

    private fun drawFastLine(pad: SignPadView, pressure: Float) {
        touch(pad, MotionEvent.ACTION_DOWN, 60f, 2000L, pressure)
        for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 60f + index * 20f, 2000L + index, pressure)
        touch(pad, MotionEvent.ACTION_UP, 260f, 2011L, 0f)
    }

    private fun thickness(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }

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
            pad.layout(0, 0, 320, 200)
            pad.strokeWidth = 24f
            block(pad)
        }
    }

    private val velocityModes = listOf(SignPadView.PenMode.VELOCITY, SignPadView.PenMode.PRESSURE_AND_VELOCITY)
}
