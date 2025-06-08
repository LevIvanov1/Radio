package com.example.chap

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.util.Log

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var audioSessionId = 0
    private lateinit var bassBoostSeekBar: SeekBar
    private lateinit var bandSeekBars: List<SeekBar>
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

        audioSessionId = intent.getIntExtra("audioSessionId", 0)

        if (audioSessionId == 0) {
            Log.e("Equalizer", "Audio Session ID not provided")
            finish()
            return
        }

        bandSeekBars = listOf(
            findViewById(R.id.band1SeekBar),
            findViewById(R.id.band2SeekBar),
            findViewById(R.id.band3SeekBar),
            findViewById(R.id.band4SeekBar),
            findViewById(R.id.band5SeekBar)
        )

        bassBoostSeekBar = findViewById(R.id.bassBoostSeekBar)
        buttonHighs = findViewById(R.id.buttonHighs)
        buttonLows = findViewById(R.id.buttonLows)
        buttonClear = findViewById(R.id.buttonClear)
        buttonSmooth = findViewById(R.id.buttonSmooth)
        buttonDynamic = findViewById(R.id.buttonDynamic)
        buttonStandard = findViewById(R.id.buttonStandard)

        setupEqualizer(audioSessionId)
        setupBassBoost(audioSessionId)
        setupPresetButtons()
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
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true

            val bands = equalizer?.numberOfBands ?: 0

            if (bands < bandSeekBars.size) {
                return
            }

            val minEQLevel = equalizer?.bandLevelRange?.get(0) ?: 0
            val maxEQLevel = equalizer?.bandLevelRange?.get(1) ?: 0

            // Сохраняем начальные значения
            for (i in 0 until bandSeekBars.size) {
                val band = i.toShort()
                initialBandLevels.add(equalizer?.getBandLevel(band) ?: 0)
            }

            for (i in 0 until bandSeekBars.size) {
                val band = i.toShort()
                bandSeekBars[i].max = maxEQLevel - minEQLevel
                bandSeekBars[i].progress = (equalizer?.getBandLevel(band) ?: 0) - minEQLevel

                bandSeekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        equalizer?.setBandLevel(band, (progress + minEQLevel).toShort())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBassBoost(audioSessionId: Int) {
        try {
            bassBoost = BassBoost(0, audioSessionId)
            bassBoost?.enabled = true
            bassBoostSeekBar.max = 1000
            bassBoostSeekBar.progress = 0

            initialBassBoostStrength = 0

            bassBoostSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    bassBoost?.setStrength(progress.toShort())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyHighsPreset() {
        equalizer?.let { eq ->
            eq.setBandLevel(0, 0)
            eq.setBandLevel(1, 0)
            eq.setBandLevel(2, 0)
            eq.setBandLevel(3, 1000)
            eq.setBandLevel(4, 1500)
            updateSeekBars()
        }
        bassBoost?.setStrength(0)
        bassBoostSeekBar.progress = 0
    }

    private fun applyLowsPreset() {
        equalizer?.let { eq ->
            eq.setBandLevel(0, 1500)
            eq.setBandLevel(1, 1000)
            eq.setBandLevel(2, 0)
            eq.setBandLevel(3, 0)
            eq.setBandLevel(4, 0)
            updateSeekBars()
        }
        bassBoost?.setStrength(500)
        bassBoostSeekBar.progress = 500
    }

    private fun applyClearPreset() {
        equalizer?.let { eq ->
            eq.setBandLevel(0, 0)
            eq.setBandLevel(1, 0)
            eq.setBandLevel(2, 500)
            eq.setBandLevel(3, 0)
            eq.setBandLevel(4, 0)
            updateSeekBars()
        }
        bassBoost?.setStrength(0)
        bassBoostSeekBar.progress = 0
    }

    private fun applySmoothPreset() {
        equalizer?.let { eq ->
            eq.setBandLevel(0, 500)
            eq.setBandLevel(1, 500)
            eq.setBandLevel(2, 500)
            eq.setBandLevel(3, 500)
            eq.setBandLevel(4, 500)
            updateSeekBars()
        }
        bassBoost?.setStrength(200)
        bassBoostSeekBar.progress = 200
    }

    private fun applyDynamicPreset() {
        equalizer?.let { eq ->
            eq.setBandLevel(0, 1000)
            eq.setBandLevel(1, 500)
            eq.setBandLevel(2, 0)
            eq.setBandLevel(3, 500)
            eq.setBandLevel(4, 1000)
            updateSeekBars()
        }
        bassBoost?.setStrength(300)
        bassBoostSeekBar.progress = 300
    }

    private fun applyStandardPreset() {
        equalizer?.let { eq ->
            for (i in 0 until bandSeekBars.size) {
                val band = i.toShort()
                eq.setBandLevel(band, initialBandLevels[i])
            }
            updateSeekBars()
        }
        bassBoost?.setStrength(initialBassBoostStrength)
        bassBoostSeekBar.progress = initialBassBoostStrength.toInt()
    }

    private fun updateSeekBars() {
        equalizer?.let { eq ->
            val minEQLevel = eq.bandLevelRange[0]
            for (i in 0 until bandSeekBars.size) {
                val band = i.toShort()
                bandSeekBars[i].progress = eq.getBandLevel(band) - minEQLevel
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        equalizer?.release()
        bassBoost?.release()
    }
}