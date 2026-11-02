package ink.x2.signkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class SignPadCubicStrokeTest {
    @Test
    fun previewsDoNotChangeCommittedCubicStroke() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        val uninterrupted = draw(pad, previews = false)
        pad.clear()
        assertTrue(uninterrupted.sameAs(draw(pad, previews = true)))
    }

    @Test
    fun curvedStrokeKeepsEndpointAndUndoRestoresBlank() = withPad { pad ->
        pad.penMode = SignPadView.PenMode.VELOCITY
        val blank = pad.exportBitmap()
        val bitmap = draw(pad, previews = true)
        assertTrue(Color.alpha(bitmap.getPixel(400, 125)) > 0)
        assertTrue(pad.undo())
        assertTrue(blank.sameAs(pad.exportBitmap()))
    }

    @Test
    fun renderSyntheticModeComparison() = withPad { pad ->
        val comparison = Bitmap.createBitmap(920, 580, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(comparison)
        canvas.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 18f }
        SignPadView.PenMode.entries.forEachIndexed { index, mode ->
            pad.clear()
            pad.penMode = mode
            val bitmap = draw(pad, previews = false)
            assertTrue((30..410).any { x -> (30..220).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 } })
            val left = (index % 2) * 460f
            val top = (index / 2) * 280f
            canvas.drawText(mode.name, left + 12f, top + 24f, label)
            canvas.drawBitmap(bitmap, left, top + 30f, null)
        }
        if (InstrumentationRegistry.getArguments().getString("captureBrush") == "true") {
            val output = File(pad.context.filesDir, "reference-brush.png")
            output.outputStream().use { assertTrue(comparison.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    private fun draw(pad: SignPadView, previews: Boolean): Bitmap {
        var time = 1000L
        for (index in 0..60) {
            val t = index / 60f
            val x = 40f + 360f * t
            val y = 125f + 50f * sin(2f * PI.toFloat() * t) + 15f * sin(6f * PI.toFloat() * t)
            val pressure = 0.15f + 0.75f * sin(PI.toFloat() * t)
            time += if (index < 30) 24L else 4L
            val action = when (index) {
                0 -> MotionEvent.ACTION_DOWN
                60 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(1000L, time, action, x, y, pressure, 1f, 0, 1f, 1f, 0, 0)
            try {
                assertTrue(pad.onTouchEvent(event))
                if (previews) repeat(2) { pad.exportBitmap() }
            } finally {
                event.recycle()
            }
        }
        return pad.exportBitmap()
    }

    private fun withPad(block: (SignPadView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pad = SignPadView(instrumentation.targetContext)
            pad.layout(0, 0, 440, 240)
            pad.strokeWidth = 18f
            block(pad)
        }
    }
}
