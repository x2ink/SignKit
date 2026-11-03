package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SignPadTailTest {
    @Test
    fun severalSlowMovesCannotCommitTailBulb() = withPad { pad ->
        for (mode in velocityModes) {
            for (count in listOf(2, 6, 12)) {
                pad.clear()
                pad.penMode = mode
                slowingStroke(pad, count)
                val preview = pad.exportBitmap()
                repeat(3) { assertTrue(preview.sameAs(pad.exportBitmap())) }
                touch(pad, MotionEvent.ACTION_UP, 240f + count * 4f, 3000L)
                val bitmap = pad.exportBitmap()
                assertTrue("Preview and committed tail must match", preview.sameAs(bitmap))
                val body = thickness(bitmap, 200)
                val tail = (240..240 + count * 4).maxOf { thickness(bitmap, it) }
                assertTrue("$mode samples=$count body=$body tail=$tail", tail <= body + 3)
                capture(pad, "tail-${mode.name}-$count.png")
                assertTrue(pad.undo())
                assertTrue(!pad.canUndo)
            }
        }
    }

    @Test
    fun shortSlowBodyCanGrowAcrossSeveralSmallSamples() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        pad.strokeWidth = 64f
        for (step in listOf(4, 20)) {
            pad.clear()
            touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
            for (x in step..40 step step) {
                touch(pad, MotionEvent.ACTION_MOVE, 40f + x, 1000L + x * 10L)
            }
            touch(pad, MotionEvent.ACTION_UP, 80f, 1500L)
            val body = thickness(pad.exportBitmap(), 74)
            assertTrue("step=$step slow body=$body must develop beyond minimum", body >= 40)
        }
    }

    @Test
    fun shortFastStrokeDoesNotGrowBulbDuringSlowLift() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        pad.strokeWidth = 64f
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        for (i in 1..3) touch(pad, MotionEvent.ACTION_MOVE, 40f + i * 20f, 1000L + i * 4L)
        for (i in 1..6) touch(pad, MotionEvent.ACTION_MOVE, 100f + i * 4f, 1012L + i * 100L)
        touch(pad, MotionEvent.ACTION_UP, 124f, 1800L)
        val bitmap = pad.exportBitmap()
        assertTrue(thickness(bitmap, 120) <= thickness(bitmap, 80) + 3)
    }

    @Test
    fun slowMiddleStillGrowsAndFastEndBecomesThin() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        for (i in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + i * 20f, 1000L + i * 4L)
        for (i in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 240f + i * 20f, 1040L + i * 100L)
        for (i in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 440f + i * 20f, 2040L + i * 4L)
        touch(pad, MotionEvent.ACTION_UP, 640f, 2100L)
        val bitmap = pad.exportBitmap()
        assertTrue(thickness(bitmap, 400) > thickness(bitmap, 200) + 4)
        assertTrue(thickness(bitmap, 600) < thickness(bitmap, 400))
    }

    @Test
    fun pressureOnlyCanStillGrowInLastFewMoves() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.PRESSURE
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L, 0.1f)
        for (i in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + i * 20f, 1000L + i * 10L, 0.1f)
        for (i in 1..6) touch(pad, MotionEvent.ACTION_MOVE, 240f + i * 4f, 1100L + i * 100L, 1f)
        touch(pad, MotionEvent.ACTION_UP, 264f, 1800L)
        val bitmap = pad.exportBitmap()
        assertTrue(thickness(bitmap, 260) > thickness(bitmap, 200) + 4)
    }

    private fun slowingStroke(pad: SignPadView, count: Int) {
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        for (i in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + i * 20f, 1000L + i * 4L)
        for (i in 1..count) touch(pad, MotionEvent.ACTION_MOVE, 240f + i * 4f, 1040L + i * 100L)
    }

    private fun thickness(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).count { Color.alpha(bitmap.getPixel(x, it)) > 0 }

    private fun touch(pad: SignPadView, action: Int, x: Float, time: Long, pressure: Float = 1f) {
        val event = MotionEvent.obtain(1000L, time, action, x, 100f, pressure, 1f, 0, 1f, 1f, 0, 0)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun capture(pad: SignPadView, name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureReviewFixes") != "true") return
        File(pad.context.filesDir, name).outputStream().use {
            assertTrue(pad.exportBitmap(Color.WHITE).compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pad = SignPadView(InstrumentationRegistry.getInstrumentation().targetContext)
            pad.layout(0, 0, 720, 200)
            pad.strokeWidth = 24f
            block(pad)
        }
    }

    private val velocityModes = listOf(SignPadView.PenMode.VELOCITY, SignPadView.PenMode.PRESSURE_AND_VELOCITY)
}
