package ink.x2.demo

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.x2.signkit.SignPadView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmallWindowTest {
    @Test
    fun verticalWritingDoesNotScrollThePage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.spvTest)).perform(scrollTo())
            var scrollBefore = 0
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                scrollBefore = (content.getChildAt(0) as ScrollView).scrollY
            }
            onView(withId(R.id.spvTest)).perform(swipeDown())
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertEquals(scrollBefore, (content.getChildAt(0) as ScrollView).scrollY)
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                assertTrue(pad.canUndo)
                val bitmap = pad.exportBitmap()
                val inkRows = (0 until bitmap.height).filter { y ->
                    android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, y)) > 0
                }
                assertTrue(inkRows.isNotEmpty())
                assertTrue(inkRows.last() - inkRows.first() >= bitmap.height / 2)
            }
        }
    }

    @Test
    fun shortWindowKeepsSignatureAreaAndCanExport() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val root = content.getChildAt(0)
                val density = activity.resources.displayMetrics.density
                val width = (800 * density).toInt()
                val height = (320 * density).toInt()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, width, height)
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                val export = activity.findViewById<Button>(R.id.btnExport)
                assertTrue("Short windows need a usable signature area", pad.height >= (200 * density).toInt())
                assertTrue("Controls and preview must remain reachable", (root as ScrollView).canScrollVertically(1))
                assertTrue(export.isEnabled)
                export.performClick()
                val image = activity.findViewById<ImageView>(R.id.ivExport)
                val bitmap = (image.drawable as BitmapDrawable).bitmap
                assertEquals(pad.width, bitmap.width)
                assertEquals(pad.height, bitmap.height)
            }
        }
    }

    @Test
    fun invalidSignatureSizeDisablesExportAndRecoversAfterLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pad = activity.findViewById<SignPadView>(R.id.spvTest)
                val export = activity.findViewById<Button>(R.id.btnExport)
                val preview = activity.findViewById<ImageView>(R.id.ivExport)
                pad.layout(0, 0, 0, 0)
                assertFalse(export.isEnabled)
                export.performClick()
                assertNull(preview.drawable)
                pad.layout(0, 0, 200, 100)
                assertTrue(export.isEnabled)
                export.performClick()
                val bitmap = (preview.drawable as BitmapDrawable).bitmap
                assertEquals(200, bitmap.width)
                assertEquals(100, bitmap.height)
            }
        }
    }
}
