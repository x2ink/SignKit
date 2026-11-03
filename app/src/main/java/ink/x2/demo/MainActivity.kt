package ink.x2.demo

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ink.x2.demo.databinding.ActivityMainBinding
import ink.x2.signkit.SignPadView

class MainActivity : AppCompatActivity() {
    private var minimumPressure: Float? = null
    private var maximumPressure: Float? = null
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        applyInsets()
        bindWritingListener()
        bindInputListener()
        bindStrokeWidth()
        bindPenModes()
        bindActions()
    }

    private fun applyInsets() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun bindWritingListener() {
        binding.spvTest.setOnWriteListener(object : SignPadView.OnWriteListener {
            override fun onWriteStart(view: SignPadView) {
                minimumPressure = null
                maximumPressure = null
                binding.tvWritingState.setText(R.string.writing_started)
                binding.btnUndo.isEnabled = view.canUndo
            }

            override fun onWriting(view: SignPadView) {
                binding.tvWritingState.setText(R.string.writing_in_progress)
            }

            override fun onWriteEnd(view: SignPadView) {
                binding.tvWritingState.setText(R.string.writing_ended)
                binding.btnUndo.isEnabled = view.canUndo
            }
        })
    }

    private fun bindInputListener() {
        binding.spvTest.setOnInputListener { input ->
            if (input.isContact && input.pressure.isFinite()) {
                minimumPressure = minOf(minimumPressure ?: input.pressure, input.pressure)
                maximumPressure = maxOf(maximumPressure ?: input.pressure, input.pressure)
            }
            val tool = when (input.toolType) {
                MotionEvent.TOOL_TYPE_FINGER -> R.string.tool_finger
                MotionEvent.TOOL_TYPE_STYLUS -> R.string.tool_stylus
                MotionEvent.TOOL_TYPE_MOUSE -> R.string.tool_mouse
                else -> R.string.tool_other
            }
            binding.tvPressure.text = getString(
                R.string.pressure_values, input.pressure,
                minimumPressure ?: input.pressure, maximumPressure ?: input.pressure, getString(tool),
            )
        }
    }

    private fun bindStrokeWidth() {
        updateStrokeWidth(binding.sbStrokeWidth.progress)
        binding.sbStrokeWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateStrokeWidth(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateStrokeWidth(widthDp: Int) {
        binding.spvTest.strokeWidth = widthDp * resources.displayMetrics.density
        binding.tvStrokeWidth.text = getString(
            R.string.stroke_width, widthDp, widthDp * binding.spvTest.minStrokeWidthRatio,
        )
    }

    private fun bindPenModes() {
        binding.spPenMode.adapter = ArrayAdapter.createFromResource(
            this, R.array.pen_modes, android.R.layout.simple_spinner_dropdown_item,
        )
        binding.spvTest.penMode = SignPadView.PenMode.VELOCITY
        binding.spPenMode.setSelection(SignPadView.PenMode.VELOCITY.ordinal)
        binding.spPenMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                binding.spvTest.penMode = SignPadView.PenMode.entries[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun bindActions() {
        binding.spvTest.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateExportAvailability()
        }
        updateExportAvailability()
        binding.btnUndo.setOnClickListener {
            binding.spvTest.undo()
            binding.btnUndo.isEnabled = binding.spvTest.canUndo
        }
        binding.btnClear.setOnClickListener {
            binding.spvTest.clear()
            binding.btnUndo.isEnabled = false
            binding.tvWritingState.setText(R.string.signature_cleared)
        }
        binding.btnExport.setOnClickListener {
            if (!hasExportSize()) {
                updateExportAvailability()
                return@setOnClickListener
            }
            val bitmap = binding.spvTest.exportBitmap(Color.WHITE)
            binding.ivExport.setImageBitmap(bitmap)
            binding.tvExportInfo.text = getString(R.string.export_size, bitmap.width, bitmap.height)
        }
    }

    private fun hasExportSize(): Boolean = binding.spvTest.width > 0 && binding.spvTest.height > 0

    private fun updateExportAvailability() {
        binding.btnExport.isEnabled = hasExportSize()
    }
}
