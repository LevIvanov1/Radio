package com.example.chap

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var audioSessionId = 0
    private lateinit var bassBoostSlider: Slider
    private lateinit var bandSliders: List<Slider>
    private lateinit var buttonHighs: Button
    private lateinit var buttonLows: Button
    private lateinit var buttonClear: Button
    private lateinit var buttonSmooth: Button
    private lateinit var buttonDynamic: Button
    private lateinit var buttonStandard: Button

    private val initialBandLevels = mutableListOf<Short>()
    private var initialBassBoostStrength: Short = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.equalizer_activity)

        audioSessionId = intent.getIntExtra("audioSessionId", 0).takeIf { it != 0 } ?: run {
            finish()
            return
        }

        initViews()
        setupEqualizer(audioSessionId)
        setupBassBoost(audioSessionId)
        setupPresetButtons()
    }

    private fun initViews() {
        bandSliders = listOf(
            findViewById(R.id.band1SeekBar),
            findViewById(R.id.band2SeekBar),
            findViewById(R.id.band3SeekBar),
            findViewById(R.id.band4SeekBar),
            findViewById(R.id.band5SeekBar)
        )

        bassBoostSlider = findViewById(R.id.bassBoostSeekBar)
        buttonHighs = findViewById(R.id.buttonHighs)
        buttonLows = findViewById(R.id.buttonLows)
        buttonClear = findViewById(R.id.buttonClear)
        buttonSmooth = findViewById(R.id.buttonSmooth)
        buttonDynamic = findViewById(R.id.buttonDynamic)
        buttonStandard = findViewById(R.id.buttonStandard)
    }

    private fun setupPresetButtons() {
        buttonHighs.setOnClickListener { applyHighsPreset() }
        buttonLows.setOnClickListener { applyLowsPreset() }
        buttonClear.setOnClickListener { applyClearPreset() }
        buttonSmooth.setOnClickListener { applySmoothPreset() }
        buttonDynamic.setOnClickListener { applyDynamicPreset() }
        buttonStandard.setOnClickListener { applyStandardPreset() }
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true

                val bands = numberOfBands
                if (bands < bandSliders.size) return

                val (minEQLevel, maxEQLevel) = bandLevelRange.let { it[0] to it[1] }

                repeat(bandSliders.size) { i ->
                    initialBandLevels.add(getBandLevel(i.toShort()))

                    bandSliders[i].apply {
                        valueFrom = minEQLevel.toFloat()
                        valueTo = maxEQLevel.toFloat()
                        value = getBandLevel(i.toShort()).toFloat()

                        addOnChangeListener { _, value, _ ->
                            equalizer?.setBandLevel(i.toShort(), safeFloatToShort(value))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBassBoost(audioSessionId: Int) {
        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                bassBoostSlider.apply {
                    valueFrom = 0f
                    valueTo = 1000f
                    value = initialBassBoostStrength.toFloat()

                    addOnChangeListener { _, value, _ ->
                        bassBoost?.setStrength(safeFloatToShort(value))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun safeFloatToShort(value: Float): Short {
        return value.toInt().toShort()
    }

    private fun applyHighsPreset() {
        equalizer?.run {
            setBandLevel(0, 0)
            setBandLevel(1, 0)
            setBandLevel(2, 0)
            setBandLevel(3, 1000.toShort())
            setBandLevel(4, 1500.toShort())
            updateSliders()
        }
        bassBoost?.setStrength(0)
        bassBoostSlider.value = 0f
    }

    private fun applyLowsPreset() {
        equalizer?.run {
            setBandLevel(0, 1500.toShort())
            setBandLevel(1, 1000.toShort())
            setBandLevel(2, 0)
            setBandLevel(3, 0)
            setBandLevel(4, 0)
            updateSliders()
        }
        bassBoost?.setStrength(500.toShort())
        bassBoostSlider.value = 500f
    }

    private fun applyClearPreset() {
        equalizer?.run {
            setBandLevel(0, 0)
            setBandLevel(1, 0)
            setBandLevel(2, 500.toShort())
            setBandLevel(3, 0)
            setBandLevel(4, 0)
            updateSliders()
        }
        bassBoost?.setStrength(0)
        bassBoostSlider.value = 0f
    }

    private fun applySmoothPreset() {
        equalizer?.run {
            setBandLevel(0, 500.toShort())
            setBandLevel(1, 500.toShort())
            setBandLevel(2, 500.toShort())
            setBandLevel(3, 500.toShort())
            setBandLevel(4, 500.toShort())
            updateSliders()
        }
        bassBoost?.setStrength(200.toShort())
        bassBoostSlider.value = 200f
    }

    private fun applyDynamicPreset() {
        equalizer?.run {
            setBandLevel(0, 1000.toShort())
            setBandLevel(1, 500.toShort())
            setBandLevel(2, 0)
            setBandLevel(3, 500.toShort())
            setBandLevel(4, 1000.toShort())
            updateSliders()
        }
        bassBoost?.setStrength(300.toShort())
        bassBoostSlider.value = 300f
    }

    private fun applyStandardPreset() {
        equalizer?.run {
            repeat(bandSliders.size) { i ->
                setBandLevel(i.toShort(), initialBandLevels[i])
            }
            updateSliders()
        }
        bassBoost?.setStrength(initialBassBoostStrength)
        bassBoostSlider.value = initialBassBoostStrength.toFloat()
    }

    private fun updateSliders() {
        equalizer?.run {
            repeat(bandSliders.size) { i ->
                bandSliders[i].value = getBandLevel(i.toShort()).toFloat()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        equalizer?.release()
        bassBoost?.release()
    }
}