package ink.x2.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.x2.signkit.SignPadView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoControlsTest {
    @Test
    fun pressureReadoutDoesNotCountReleaseZeroAsPressureVariation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertEquals(SignPadView.PenMode.VELOCITY, pad.penMode)
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
                    val pressure = if (action == MotionEvent.ACTION_UP) 0f else 0.6f
                    val event = MotionEvent.obtain(1000L, 1020L, action, 40f, 40f, pressure, 1f, 0, 1f, 1f, 0, 0)
                    try {
                        assertTrue(pad.onTouchEvent(event))
                    } finally {
                        event.recycle()
                    }
                }
                val text = activity.findViewById<TextView>(R.id.tvPressure).text.toString()
                assertTrue(text.contains("0.000"))
                assertTrue(text.contains("0.600–0.600"))
            }
        }
    }

    @Test
    fun widthSliderChangesActualInkThickness() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val slider = activity.findViewById<SeekBar>(R.id.sbStrokeWidth)
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertEquals(24, slider.progress)
                assertEquals(4, slider.min)
                assertEquals(64, slider.max)
                val y = pad.height / 2
                val normal = drawBody(pad, y)
                pad.clear()
                slider.progress = 64
                val thick = drawBody(pad, y)
                val normalWidth = inkThickness(normal, y)
                val thickWidth = inkThickness(thick, y)
                assertTrue(thickWidth >= normalWidth * 2)
                assertTrue(thickWidth >= 20f * pad.resources.displayMetrics.density)
            }
        }
    }

    private fun drawBody(pad: SignPadView, y: Int): Bitmap {
        for (index in 0..10) {
            val action = when (index) {
                0 -> MotionEvent.ACTION_DOWN
                10 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(
                1000L, 1000L + index * 20L, action,
                40f + index * 20f, y.toFloat(), 0.05f, 1f, 0, 1f, 1f, 0, 0,
            )
            try {
                assertTrue(pad.onTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
        return pad.exportBitmap()
    }

    private fun inkThickness(bitmap: Bitmap, y: Int): Int =
        (maxOf(0, y - 100)..minOf(bitmap.height - 1, y + 100)).count {
            Color.alpha(bitmap.getPixel(160, it)) > 0
        }

    @Test
    fun modeSelectorUpdatesPenMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var labels: Array<String>
            scenario.onActivity { labels = it.resources.getStringArray(R.array.pen_modes) }
            SignPadView.PenMode.entries.forEachIndexed { index, mode ->
                onView(withId(R.id.spPenMode)).perform(click())
                onData(equalTo(labels[index])).perform(click())
                scenario.onActivity { activity ->
                    assertEquals(mode, activity.findViewById<SignPadView>(R.id.spvTest).penMode)
                }
            }
        }
    }

    @Test
    fun widthWritingExportUndoAndClearWorkTogether() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var blank: Bitmap
            scenario.onActivity { activity ->
                blank = activity.findViewById<SignPadView>(R.id.spvTest).exportBitmap(Color.WHITE)
            }
            onView(withId(R.id.sbStrokeWidth)).perform(swipeRight())
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertTrue(pad.strokeWidth > 5f * pad.resources.displayMetrics.density)
            }
            onView(withId(R.id.spvTest)).perform(swipeRight())
            onView(withId(R.id.tvWritingState)).check(matches(withText(R.string.writing_ended)))
            onView(withId(R.id.btnExport)).perform(click())
            scenario.onActivity { activity ->
                val image = activity.findViewById<ImageView>(R.id.ivExport)
                val exported = (image.drawable as BitmapDrawable).bitmap
                assertFalse(blank.sameAs(exported))
            }
            onView(withId(R.id.btnUndo)).perform(click())
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertFalse(pad.canUndo)
                assertTrue(blank.sameAs(pad.exportBitmap(Color.WHITE)))
            }
            onView(withId(R.id.spvTest)).perform(swipeRight())
            onView(withId(R.id.btnClear)).perform(click())
            onView(withId(R.id.tvWritingState)).check(matches(withText(R.string.signature_cleared)))
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertFalse(pad.canUndo)
                assertTrue(blank.sameAs(pad.exportBitmap(Color.WHITE)))
            }
        }
    }
}
