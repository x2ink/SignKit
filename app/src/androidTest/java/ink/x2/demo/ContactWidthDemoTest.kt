package ink.x2.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ink.x2.signkit.SignPadView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContactWidthDemoTest {
    @Test
    fun defaultSpeedModeDotMatchesFastLineAtBothSliderWidths() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                val slider = activity.findViewById<SeekBar>(R.id.sbStrokeWidth)
                assertEquals(SignPadView.PenMode.VELOCITY, pad.penMode)
                for (width in listOf(24, 64)) {
                    pad.clear()
                    slider.progress = width
                    val dotY = pad.height / 3f
                    val lineY = pad.height * 2f / 3f
                    touch(pad, MotionEvent.ACTION_DOWN, 100f, dotY, 1000L)
                    touch(pad, MotionEvent.ACTION_UP, 100f, dotY, 1020L)
                    touch(pad, MotionEvent.ACTION_DOWN, 100f, lineY, 2000L)
                    for (index in 1..10) {
                        touch(pad, MotionEvent.ACTION_MOVE, 100f + index * 20f, lineY, 2000L + index)
                    }
                    touch(pad, MotionEvent.ACTION_UP, 300f, lineY, 2011L)
                    val bitmap = pad.exportBitmap()
                    val dot = thickness(bitmap, 100, dotY.toInt())
                    val body = thickness(bitmap, 220, lineY.toInt())
                    assertTrue("Slider $width dp: dot=$dot body=$body", dot <= body + 2)
                    if (captureEnabled()) {
                        val file = File(activity.filesDir, "contact-width-$width.png")
                        file.outputStream().use {
                            assertTrue(pad.exportBitmap(Color.WHITE).compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    }
                }
            }
            if (captureEnabled()) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync()
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(instrumentation.targetContext.filesDir, "contact-width-screen.png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
    }

    private fun captureEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("captureContactWidths") == "true"

    private fun thickness(bitmap: Bitmap, x: Int, y: Int): Int =
        (maxOf(0, y - 100)..minOf(bitmap.height - 1, y + 100)).count {
            Color.alpha(bitmap.getPixel(x, it)) > 0
        }

    private fun touch(pad: SignPadView, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(1000L, time, action, x, y, 0.1f, 1f, 0, 1f, 1f, 0, 0)
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }
}
