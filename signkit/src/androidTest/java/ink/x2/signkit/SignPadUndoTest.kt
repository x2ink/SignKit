package ink.x2.signkit

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignPadUndoTest {
    @Test
    fun emptyUndoDoesNothing() = withPad { pad ->
        val blank = pad.exportBitmap()
        assertFalse(pad.canUndo)
        assertFalse(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
    }

    @Test
    fun repeatedUndoRestoresEachPreviousBitmap() = withPad { pad ->
        val blank = pad.exportBitmap()
        stroke(pad, 30f)
        val first = pad.exportBitmap()
        stroke(pad, 100f)
        assertFalse(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.canUndo)
        assertTrue(pad.undo())
        assertTrue(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
        assertFalse(pad.canUndo)
        assertFalse(pad.undo())
    }

    @Test
    fun tapIsOneUndoableStroke() = withPad { pad ->
        val blank = pad.exportBitmap(Color.WHITE)
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        touch(pad, MotionEvent.ACTION_UP, 40f, 40f)
        assertFalse(blank.sameAs(pad.exportBitmap(Color.WHITE)))
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap(Color.WHITE)))
    }

    @Test
    fun undoDuringDrawingDiscardsOnlyCurrentStroke() = withPad { pad ->
        stroke(pad, 30f)
        val first = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_DOWN, 30f, 100f)
        touch(pad, MotionEvent.ACTION_MOVE, 60f, 110f)
        touch(pad, MotionEvent.ACTION_MOVE, 90f, 120f)
        assertFalse(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        touch(pad, MotionEvent.ACTION_MOVE, 120f, 100f)
        touch(pad, MotionEvent.ACTION_UP, 120f, 100f)
        assertTrue(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertFalse(pad.canUndo)
    }

    @Test
    fun clearDuringDrawingResetsHistoryAndAllowsNewStroke() = withPad { pad ->
        val blank = pad.exportBitmap()
        stroke(pad, 30f)
        touch(pad, MotionEvent.ACTION_DOWN, 30f, 100f)
        touch(pad, MotionEvent.ACTION_MOVE, 60f, 110f)
        pad.clear()
        touch(pad, MotionEvent.ACTION_UP, 90f, 120f)
        assertFalse(pad.canUndo)
        assertFalse(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
        stroke(pad, 150f)
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
    }

    @Test
    fun canceledStrokeIsPreservedAndUndoable() = withPad { pad ->
        stroke(pad, 30f)
        val first = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_DOWN, 30f, 100f)
        touch(pad, MotionEvent.ACTION_MOVE, 90f, 120f)
        touch(pad, MotionEvent.ACTION_CANCEL, 90f, 120f)
        assertFalse(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(first.sameAs(pad.exportBitmap()))
    }

    @Test
    fun drawingAfterUndoStartsAnIndependentStroke() = withPad { pad ->
        stroke(pad, 30f)
        val first = pad.exportBitmap()
        stroke(pad, 100f)
        pad.undo()
        stroke(pad, 150f)
        assertFalse(first.sameAs(pad.exportBitmap()))
        assertTrue(pad.undo())
        assertTrue(first.sameAs(pad.exportBitmap()))
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 240, 240)
            block(pad)
        }
    }

    @Test
    fun remainingPointerCannotRestoreUndoneStroke() = withPad { pad ->
        val blank = pad.exportBitmap()
        touch(pad, MotionEvent.ACTION_DOWN, 40f, 40f)
        multiTouch(
            pad,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(0, 1),
        )
        multiTouch(pad, MotionEvent.ACTION_POINTER_UP, intArrayOf(0, 1))
        assertTrue(pad.canUndo)
        assertTrue(pad.undo())
        multiTouch(pad, MotionEvent.ACTION_MOVE, intArrayOf(1))
        multiTouch(pad, MotionEvent.ACTION_UP, intArrayOf(1))
        assertTrue(blank.sameAs(pad.exportBitmap()))
        assertFalse(pad.canUndo)
    }

    private fun multiTouch(pad: SignPadView, action: Int, ids: IntArray) {
        val properties = ids.map { id ->
            MotionEvent.PointerProperties().apply {
                this.id = id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = ids.map { id ->
            MotionEvent.PointerCoords().apply {
                x = 40f + id * 80f
                y = 40f + id * 80f
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            time, time, action, ids.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        try {
            assertTrue(pad.onTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun stroke(pad: SignPadView, y: Float) {
        touch(pad, MotionEvent.ACTION_DOWN, 30f, y)
        touch(pad, MotionEvent.ACTION_MOVE, 60f, y + 10f)
        touch(pad, MotionEvent.ACTION_MOVE, 90f, y + 20f)
        touch(pad, MotionEvent.ACTION_UP, 120f, y)
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
