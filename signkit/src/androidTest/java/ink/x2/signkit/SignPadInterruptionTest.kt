package ink.x2.signkit

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadInterruptionTest {
    private class RecordingParent(context: Context) : FrameLayout(context) {
        val interceptRequests = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            interceptRequests.add(disallowIntercept)
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    @Test
    fun disablingMidStrokeEndsOnceAndPreservesEveryPenMode() = withPad { pad, parent ->
        for (mode in SignPadView.PenMode.entries) {
            pad.clear()
            pad.penMode = mode
            val events = recordEvents(pad)
            val blank = pad.exportBitmap()
            touch(pad, MotionEvent.ACTION_DOWN, 40f)
            touch(pad, MotionEvent.ACTION_MOVE, 120f)
            val preview = pad.exportBitmap()
            pad.isEnabled = false
            assertEquals(listOf("start", "writing", "end"), events)
            assertEquals(false, parent.interceptRequests.last())
            assertTrue("$mode interrupted stroke must stay visible", preview.sameAs(pad.exportBitmap()))
            assertTrue(pad.canUndo)
            assertFalse(touch(pad, MotionEvent.ACTION_CANCEL, 120f))
            assertFalse(touch(pad, MotionEvent.ACTION_UP, 160f))
            pad.isEnabled = false
            assertEquals(3, events.size)
            pad.isEnabled = true
            drawTap(pad)
            assertTrue(pad.undo())
            assertTrue(preview.sameAs(pad.exportBitmap()))
            assertTrue(pad.undo())
            assertTrue(blank.sameAs(pad.exportBitmap()))
            assertFalse(pad.canUndo)
        }
    }

    @Test
    fun disablingInStartCallbackPairsLifecycleAndIgnoresFollowingMovement() = withPad { pad, parent ->
        val events = mutableListOf<String>()
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteStart(view: SignPadView) {
                events.add("start")
                view.isEnabled = false
            }

            override fun onWriting(view: SignPadView) {
                events.add("writing")
            }

            override fun onWriteEnd(view: SignPadView) {
                events.add("end")
            }
        })
        touch(pad, MotionEvent.ACTION_DOWN, 40f)
        val tap = pad.exportBitmap()
        assertEquals(listOf("start", "end"), events)
        assertEquals(false, parent.interceptRequests.last())
        assertFalse(touch(pad, MotionEvent.ACTION_MOVE, 120f))
        assertTrue(tap.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
    }

    @Test
    fun endCallbackMayClearAndReenableWithoutDuplicateEnd() = withPad { pad, _ ->
        var endings = 0
        val blank = pad.exportBitmap()
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteEnd(view: SignPadView) {
                endings++
                if (endings == 1) assertFalse(view.isEnabled)
                view.clear()
                view.isEnabled = true
            }
        })
        touch(pad, MotionEvent.ACTION_DOWN, 40f)
        touch(pad, MotionEvent.ACTION_MOVE, 120f)
        pad.isEnabled = false
        assertEquals(1, endings)
        assertTrue(pad.isEnabled)
        assertFalse(pad.canUndo)
        assertTrue(blank.sameAs(pad.exportBitmap()))
        touch(pad, MotionEvent.ACTION_UP, 120f)
        assertEquals(1, endings)
        drawTap(pad)
        assertEquals(2, endings)
    }

    private fun recordEvents(pad: SignPadView): MutableList<String> = mutableListOf<String>().also { events ->
        pad.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteStart(view: SignPadView) { events.add("start") }
            override fun onWriting(view: SignPadView) { events.add("writing") }
            override fun onWriteEnd(view: SignPadView) { events.add("end") }
        })
    }

    private fun drawTap(pad: SignPadView) {
        touch(pad, MotionEvent.ACTION_DOWN, 180f)
        touch(pad, MotionEvent.ACTION_UP, 180f)
    }

    private fun touch(pad: SignPadView, action: Int, x: Float): Boolean {
        val event = MotionEvent.obtain(1000L, 1020L, action, x, 100f, 0.8f, 1f, 0, 1f, 1f, 0, 0)
        return try { pad.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun withPad(block: (SignPadView, RecordingParent) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val parent = RecordingParent(instrumentation.targetContext)
            val pad = SignPadView(instrumentation.targetContext)
            parent.addView(pad)
            pad.layout(0, 0, 240, 200)
            pad.strokeWidth = 24f
            block(pad, parent)
        }
    }
}
