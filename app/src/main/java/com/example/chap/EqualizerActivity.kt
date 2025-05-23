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
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.equalizer_activity)

        audioSessionId = intent.getIntExtra("audioSessionId", 0)

        if (audioSessionId == 0) {
            // Обработка ошибки: Audio Session ID не предоставлен
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

        resetButton = findViewById(R.id.buttonReset)
        bassBoostSeekBar = findViewById(R.id.bassBoostSeekBar)

        setupEqualizer(audioSessionId)
        setupBassBoost(audioSessionId)
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true

            val bands = equalizer?.numberOfBands ?: 0 // Обработка возможного null

            if (bands < bandSeekBars.size) {
                // Обработка ошибки: недостаточно ползунков
                return
            }

            val minEQLevel = equalizer?.bandLevelRange?.get(0) ?: 0 // Обработка возможного null
            val maxEQLevel = equalizer?.bandLevelRange?.get(1) ?: 0 // Обработка возможного null

            for (i in 0 until bandSeekBars.size) { // Используем длину массива SeekBar
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
            e.printStackTrace() // Логируйте ошибку
        }
    }

    private fun setupBassBoost(audioSessionId: Int) {
        try {
            bassBoost = BassBoost(0, audioSessionId)
            bassBoost?.enabled = true

            bassBoostSeekBar.max = 1000 // Максимальное значение BassBoost

            // Начинаем с нуля, так как не можем получить текущее значение
            bassBoostSeekBar.progress = 0

            bassBoostSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    bassBoost?.setStrength(progress.toShort()) // Установка силы BassBoost
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        equalizer?.release()
        bassBoost?.release()
    }
}