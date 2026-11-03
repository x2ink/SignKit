package ink.x2.signkit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import kotlin.math.abs

class SignPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class TouchPoint(
        val x: Float,
        val y: Float,
    )

    interface OnWriteListener {
        fun onWriteStart(view: SignPadView) {}
        fun onWriting(view: SignPadView) {}
        fun onWriteEnd(view: SignPadView) {}
    }

    enum class PenMode {
        FIXED,
        VELOCITY,
        PRESSURE,
        PRESSURE_AND_VELOCITY,
    }

    data class InputInfo(val pressure: Float, val toolType: Int, val deviceId: Int, val isContact: Boolean)

    private data class Stroke(val path: Path, val width: Float, val filled: Boolean)

    private val strokes = ArrayDeque<Stroke>()
    private val path = Path()
    private val renderPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val minimumDistance = 2f * resources.displayMetrics.density
    private var lastSample: StrokeSample? = null
    private var startPoint: TouchPoint? = null
    private var isDrawing = false
    private var hasMoved = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var currentStrokeWidth = 0f
    private var writeListener: OnWriteListener? = null
    private var currentPenMode = PenMode.FIXED
    private var widthModel: StrokeWidthModel? = null
    private var variableStroke: VariableStroke? = null
    private var inputListener: ((InputInfo) -> Unit)? = null

    var penMode: PenMode = PenMode.FIXED

    /** 动态模式的最小宽度与最大宽度之比，下一笔生效。 */
    var minStrokeWidthRatio: Float = 3f / 7f
        set(value) {
            require(value.isFinite() && value > 0f && value <= 1f) { "minStrokeWidthRatio must be in (0, 1]" }
            field = value
        }

    var velocityFilterWeight: Float = 0.9f
        set(value) {
            require(value.isFinite() && value > 0f && value <= 1f) { "velocityFilterWeight must be in (0, 1]" }
            field = value
        }

    /** 速度尺度，单位 dp/ms；默认等效上游的 1px/ms，下一笔生效。 */
    var velocityReference: Float = 1f / resources.displayMetrics.density
        set(value) {
            require(value.isFinite() && value > 0f) { "velocityReference must be finite and positive" }
            field = value
        }

    /** 后续新笔画的宽度，单位 px；动态模式下为最大宽度，当前与历史笔画不受配置修改影响。 */
    var strokeWidth: Float = 5f * resources.displayMetrics.density
        set(value) {
            require(value.isFinite() && value > 0f) { "strokeWidth must be finite and positive" }
            field = value
        }

    val canUndo: Boolean
        get() = isDrawing || strokes.isNotEmpty()

    /** 在 UI 线程注册监听；传 null 移除。回调发生在对应状态更新后。 */
    fun setOnWriteListener(listener: OnWriteListener?) {
        writeListener = listener
    }

    /** 主指当前与历史原始输入，包括未通过位移过滤的压力样本；UI线程回调。 */
    fun setOnInputListener(listener: ((InputInfo) -> Unit)?) {
        inputListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSignature(canvas)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // 禁用后不会再收到有效的UP/CANCEL，需在此按取消语义结束并释放父容器拦截。
        if (!enabled) finishStroke()
    }

    private fun drawSignature(canvas: Canvas) {
        strokes.forEach {
            paint.strokeWidth = it.width
            paint.style = if (it.filled) Paint.Style.FILL else Paint.Style.STROKE
            canvas.drawPath(it.path, paint)
        }
        if (!isDrawing) return

        paint.strokeWidth = currentStrokeWidth
        if (currentPenMode != PenMode.FIXED) {
            paint.style = Paint.Style.FILL
            renderPath.set(path)
            variableStroke?.appendTail(renderPath)
            canvas.drawPath(renderPath, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        if (hasMoved) {
            renderPath.set(path)
            lastSample?.let { renderPath.lineTo(it.x, it.y) }
            // 合并实时末段后只绘制一次，避免两条路径接缝处叠加抗锯齿边缘。
            canvas.drawPath(renderPath, paint)
        } else {
            startPoint?.let { canvas.drawPoint(it.x, it.y, paint) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val inputIndex = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_POINTER_UP -> event.actionIndex.takeIf {
                event.getPointerId(it) == activePointerId
            } ?: -1
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                event.findPointerIndex(activePointerId)
            else -> -1
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startStroke(event)
            MotionEvent.ACTION_MOVE -> moveStroke(event)
            MotionEvent.ACTION_POINTER_DOWN -> Unit
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    endStroke(event, event.actionIndex)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    endStroke(event, event.findPointerIndex(activePointerId))
                }
            }
            MotionEvent.ACTION_CANCEL -> finishStroke()
            else -> return super.onTouchEvent(event)
        }
        if (inputIndex >= 0) publishInput(event, inputIndex)
        return true
    }

    private fun publishInput(event: MotionEvent, index: Int) {
        val listener = inputListener ?: return
        for (history in 0 until event.historySize) {
            listener(InputInfo(event.getHistoricalPressure(index, history), event.getToolType(index), event.deviceId, true))
        }
        val contact = event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_POINTER_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        listener(InputInfo(event.getPressure(index), event.getToolType(index), event.deviceId, contact))
    }

    private fun startStroke(event: MotionEvent) {
        val sample = StrokeSample(event.x, event.y, event.eventTime, event.getPressure(0))
        isDrawing = true
        hasMoved = false
        activePointerId = event.getPointerId(0)
        currentPenMode = penMode
        val settings = PenSettings(
            strokeWidth, penMode, minStrokeWidthRatio, velocityFilterWeight, velocityReference,
        )
        val model = StrokeWidthModel(settings, resources.displayMetrics.density)
        widthModel = model
        model.accept(sample)
        currentStrokeWidth = if (penMode == PenMode.FIXED) strokeWidth else model.tapWidth
        lastSample = sample
        startPoint = TouchPoint(sample.x, sample.y)
        path.reset()
        if (currentPenMode == PenMode.FIXED) {
            path.moveTo(sample.x, sample.y)
        } else {
            variableStroke = VariableStroke(sample, model)
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
        writeListener?.onWriteStart(this)
    }

    private fun moveStroke(event: MotionEvent) {
        if (!isDrawing) return
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        val changed = addSamples(event, index)
        invalidate()
        if (changed) writeListener?.onWriting(this)
    }

    private fun addSamples(event: MotionEvent, index: Int, forceEnd: Boolean = false): Boolean {
        var changed = false
        for (i in 0 until event.historySize) {
            val sample = StrokeSample(
                event.getHistoricalX(index, i), event.getHistoricalY(index, i),
                event.getHistoricalEventTime(i), event.getHistoricalPressure(index, i),
            )
            changed = addPoint(sample) || changed
        }
        val sample = StrokeSample(event.getX(index), event.getY(index), event.eventTime, event.getPressure(index))
        return addPoint(sample, force = forceEnd, isRelease = forceEnd) || changed
    }

    private fun addPoint(sample: StrokeSample, force: Boolean = false, isRelease: Boolean = false): Boolean {
        val last = lastSample ?: return false
        val dx = sample.x - last.x
        val dy = sample.y - last.y
        val belowThreshold = dx * dx + dy * dy < minimumDistance * minimumDistance
        val pressureChanged = !isRelease && usesPressure() &&
            abs(normalizedPressure(sample.pressure) - normalizedPressure(last.pressure)) >= 0.01f
        val moved = dx != 0f || dy != 0f
        if (!moved && !pressureChanged) return false
        if (!force && !pressureChanged && belowThreshold) return false
        var accepted = if (!force && belowThreshold) sample.copy(x = last.x, y = last.y) else sample
        if (isRelease) accepted = accepted.copy(pressure = last.pressure)

        if (currentPenMode == PenMode.FIXED) {
            path.quadTo(last.x, last.y, (last.x + accepted.x) / 2f, (last.y + accepted.y) / 2f)
        } else {
            variableStroke?.append(path, accepted)
        }
        lastSample = accepted
        hasMoved = hasMoved || accepted.x != last.x || accepted.y != last.y
        return true
    }

    private fun usesPressure(): Boolean =
        currentPenMode == PenMode.PRESSURE || currentPenMode == PenMode.PRESSURE_AND_VELOCITY

    private fun endStroke(event: MotionEvent, index: Int) {
        if (index >= 0) addSamples(event, index, forceEnd = true)
        val wasTap = !hasMoved
        finishStroke()
        if (wasTap) performClick()
    }

    private fun finishStroke() {
        if (!isDrawing) return
        if (currentPenMode != PenMode.FIXED) {
            variableStroke?.appendTail(path)
        } else if (hasMoved) {
            lastSample?.let { path.lineTo(it.x, it.y) }
        } else {
            startPoint?.let { path.lineTo(it.x + 0.01f, it.y + 0.01f) }
        }
        strokes.addLast(Stroke(Path(path), currentStrokeWidth, currentPenMode != PenMode.FIXED))
        resetCurrentStroke()
        invalidate()
        writeListener?.onWriteEnd(this)
    }

    override fun performClick(): Boolean = super.performClick()

    fun clear() {
        val wasDrawing = isDrawing
        strokes.clear()
        resetCurrentStroke()
        invalidate()
        if (wasDrawing) writeListener?.onWriteEnd(this)
    }

    /** 撤销当前正在绘制的一笔，或最后完成的一笔；无笔画时返回 false。 */
    fun undo(): Boolean {
        if (!canUndo) return false
        val wasDrawing = isDrawing
        if (wasDrawing) {
            resetCurrentStroke()
        } else {
            strokes.removeLast()
        }
        invalidate()
        if (wasDrawing) writeListener?.onWriteEnd(this)
        return true
    }

    private fun resetCurrentStroke() {
        path.reset()
        renderPath.reset()
        lastSample = null
        widthModel = null
        variableStroke = null
        startPoint = null
        isDrawing = false
        hasMoved = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    fun exportBitmap(backgroundColor: Int = Color.TRANSPARENT): Bitmap {
        check(width > 0 && height > 0) { "SignPadView 尚未完成布局" }
        return createBitmap(width, height).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            drawSignature(canvas)
        }
    }
}
