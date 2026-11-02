package ink.x2.signkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceBrushTest {
    @Test
    fun cubicMatchesIndependentUpstreamGolden() {
        val samples = listOf(
            StrokeSample(0f, 0f, 0L, 1f), StrokeSample(10f, 20f, 10L, 1f),
            StrokeSample(30f, 10f, 20L, 1f), StrokeSample(50f, 30f, 30L, 1f),
        )
        val curve = CubicCurve.through(samples)
        assertEquals(17.5f, curve.control1.x, 0.0001f)
        assertEquals(22.5f, curve.control1.y, 0.0001f)
        assertEquals(21.169631958008f, curve.control2.x, 0.0001f)
        assertEquals(7.792407989502f, curve.control2.y, 0.0001f)
        val expected = listOf(15.047291994095f to 19.181744873524f,
            19.501111984253f to 15.109652996063f, 24.204375982285f to 10.982734620571f)
        expected.forEachIndexed { index, point ->
            val t = (index + 1) / 4f
            assertEquals(point.first, curve.x(t), 0.0001f)
            assertEquals(point.second, curve.y(t), 0.0001f)
        }
        assertEquals(23.616275787354f, curve.length(), 0.0001f)
    }

    @Test
    fun velocityAndWidthMatchIndependentUpstreamGolden() {
        val model = StrokeWidthModel(PenSettings(14f, SignPadView.PenMode.VELOCITY, 3f / 7f, 0.9f, 1f), 1f)
        model.accept(StrokeSample(0f, 0f, 0L, 1f))
        assertEquals(10f, model.width, 0f)
        val targets = listOf(11.864407539368f, 7.299270153046f, 6f, 7.695775032043f)
        val halves = listOf(10.233051300049f, 11.293765068054f, 7.136861324310f, 6.211971759796f)
        listOf(2f, 12f, 52f, 57f).forEachIndexed { index, x ->
            val start = model.width
            val end = model.accept(StrokeSample(x, 0f, (index + 1) * 10L, 1f))
            assertEquals(targets[index], end, 0.0001f)
            assertEquals(halves[index], curveWidth(start, end, 0.5f), 0.0001f)
        }
    }

    @Test
    fun degenerateControlsRemainFinite() {
        val sample = StrokeSample(20f, 30f, 10L, 1f)
        val curve = CubicCurve.through(List(4) { sample })
        assertEquals(0f, curve.length(), 0.0001f)
        assertTrue(curve.x(0.5f).isFinite() && curve.y(0.5f).isFinite())
    }
}
