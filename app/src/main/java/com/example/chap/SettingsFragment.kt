package com.example.chap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.exoplayer2.util.Log

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        val equalizerPreference: Preference? = findPreference("equalizer_preference")
        equalizerPreference?.setOnPreferenceClickListener {
            Log.d("Equalizer", "Preference Clicked")
            Log.d("Equalizer", "Starting Activity")
            try {
                val intent = Intent(requireContext(), SettingsActivity::class.java)
                intent.putExtra("start_equalizer", true) // Добавляем флаг
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("Equalizer", "Error starting activity: ${e.message}")
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "theme_preference") {
            // Получаем ссылку на MainActivity
            val activity = activity as? AppCompatActivity
            // Пересоздаем MainActivity, чтобы применить изменения темы
            activity?.recreate()
        }
    }
}

class SettingsActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true // Устанавливаем isServiceBound в true

            musicService?.getAudioSessionIdLiveData()?.observe(this@SettingsActivity) { audioSessionId ->
                if (audioSessionId != null && audioSessionId != 0) {
                    startEqualizerActivity(audioSessionId)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Log.d("Equalizer", "SettingsActivity.onCreate() called")
        Log.d(
            "Equalizer",
            "Intent has start_equalizer: ${intent.getBooleanExtra("start_equalizer", false)}"
        )

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Привязываем MusicService
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isServiceBound = true
        }
    }

    private fun startEqualizerActivity(audioSessionId: Int) {
        val intent = Intent(this, EqualizerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        intent.putExtra("audioSessionId", audioSessionId)
        startActivity(intent)
    }
}
