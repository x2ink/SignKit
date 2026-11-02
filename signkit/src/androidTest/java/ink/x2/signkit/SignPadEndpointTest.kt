package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SignPadEndpointTest {
    @Test
    fun oneMoveWideStrokeCanDevelopBodyWithoutLargeStart() = withPad { pad ->
        val density = pad.resources.displayMetrics.density
        pad.penMode = SignPadView.PenMode.VELOCITY
        pad.strokeWidth = 64f * density
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        touch(pad, MotionEvent.ACTION_MOVE, 40f + 100f * density, 1320L)
        val preview = pad.exportBitmap()
        val body = thickness(preview, (40f + 90f * density).toInt())
        assertTrue("Single MOVE must grow a visible body: $body", body >= 35f * density)
        assertTrue(thickness(preview, 40) < body)
        touch(pad, MotionEvent.ACTION_UP, 40f + 100f * density, 1700L)
        assertTrue(preview.sameAs(pad.exportBitmap()))
    }

    @Test
    fun slowLastMoveAndLiftDoNotEnlargeTail() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + index * 20f, 1000L + index * 4L)
        touch(pad, MotionEvent.ACTION_MOVE, 241f, 1250L)
        touch(pad, MotionEvent.ACTION_UP, 242f, 1450L)
        val bitmap = pad.exportBitmap()
        val tail = (238..242).maxOf { thickness(bitmap, it) }
        assertTrue("Slow final sample must not make a bulb", tail <= thickness(bitmap, 200) + 3)
    }

    @Test
    fun wideShortStrokeRetainsVisibleThickness() = withPad { pad ->
        val density = pad.resources.displayMetrics.density
        pad.penMode = SignPadView.PenMode.VELOCITY
        pad.strokeWidth = 64f * density
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 40f + index * 4f * density, 1000L + index * 40)
        touch(pad, MotionEvent.ACTION_UP, 40f + 40f * density, 1500L)
        assertTrue(thickness(pad.exportBitmap(), (40f + 30f * density).toInt()) >= 28f * density)
    }

    @Test
    fun pausedStartAndSlowReleaseDoNotCreateEndBulbs() = withPad { pad ->
        for (mode in listOf(SignPadView.PenMode.VELOCITY, SignPadView.PenMode.PRESSURE_AND_VELOCITY)) {
            pad.clear()
            pad.penMode = mode
            touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
            touch(pad, MotionEvent.ACTION_MOVE, 43f, 1200L)
            for (index in 1..10) touch(pad, MotionEvent.ACTION_MOVE, 43f + index * 20f, 1200L + index * 4L)
            touch(pad, MotionEvent.ACTION_UP, 244f, 1500L)
            val bitmap = pad.exportBitmap()
            val body = thickness(bitmap, 200)
            val head = (40..44).maxOf { thickness(bitmap, it) }
            val tail = (241..244).maxOf { thickness(bitmap, it) }
            assertTrue("$mode head=$head body=$body", head <= body + 3)
            assertTrue("$mode tail=$tail body=$body", tail <= body + 3)
            assertTrue(Color.alpha(bitmap.getPixel(244, 80)) > 0)
            if (InstrumentationRegistry.getArguments().getString("captureEndpoints") == "true") {
                val output = Bitmap.createBitmap(600, 380, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                canvas.drawColor(Color.WHITE)
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
                canvas.drawText("$mode: paused start / fast body / slow lift", 16f, 24f, label)
                canvas.save()
                canvas.translate(0f, 30f)
                canvas.scale(2f, 2f)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
                val file = File(pad.context.filesDir, "endpoints-${mode.name}.png")
                file.outputStream().use { assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
    }

    @Test
    fun movementPreviewDoesNotCommitDownDotOrAdvanceWidth() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 1000L)
        touch(pad, MotionEvent.ACTION_MOVE, 140f, 1020L)
        val preview = pad.exportBitmap()
        repeat(5) { assertTrue(preview.sameAs(pad.exportBitmap())) }
        touch(pad, MotionEvent.ACTION_UP, 140f, 1050L)
        assertTrue(preview.sameAs(pad.exportBitmap()))
        assertTrue(thickness(preview, 40) <= thickness(preview, 100) + 2)
    }

    private fun thickness(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).count { Color.alpha(bitmap.getPixel(x, it)) > 0 }

    private fun touch(pad: SignPadView, action: Int, x: Float, time: Long) {
        val event = MotionEvent.obtain(1000L, time, action, x, 80f, 1f, 1f, 0, 1f, 1f, 0, 0)
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
            pad.layout(0, 0, 300, 160)
            pad.strokeWidth = 24f
            block(pad)
        }
    }
}
