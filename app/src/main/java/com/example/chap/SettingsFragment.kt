package com.example.chap

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var equalizerLaunched = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        findPreference<ListPreference>("sleep_timer_preference")?.setOnPreferenceChangeListener { _, newValue ->
            val minutes = (newValue as String).toInt()
            setSleepTimer(minutes)
            true
        }

        findPreference<Preference>("equalizer_preference")?.setOnPreferenceClickListener {
            if (!equalizerLaunched) {
                equalizerLaunched = true
                startActivity(Intent(requireContext(), EqualizerHostActivity::class.java))
            }
            true
        }
    }

    private fun setSleepTimer(minutes: Int) {
        activity?.let {
            val intent = Intent(it, MusicService::class.java).apply {
                action = MusicService.ACTION_SET_SLEEP_TIMER
                putExtra(MusicService.EXTRA_SLEEP_TIMER_MINUTES, minutes)
            }
            it.startService(intent)

            if (minutes > 0) {
                Toast.makeText(it, "Таймер установлен на $minutes минут", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(it, "Таймер отключен", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        equalizerLaunched = false
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "theme_preference") {
            (activity as? AppCompatActivity)?.recreate()
        }
    }
}

class EqualizerHostActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isServiceBound = true
            checkAudioSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer_host)

        bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    private fun checkAudioSession() {
        musicService?.getAudioSessionIdLiveData()?.observe(this) { audioSessionId ->
            if (audioSessionId != null && audioSessionId != 0) {
                startEqualizer(audioSessionId)
            } else {
                showErrorAndFinish()
            }
        }
    }

    private fun startEqualizer(audioSessionId: Int) {
        val intent = Intent(this, EqualizerActivity::class.java).apply {
            putExtra("audioSessionId", audioSessionId)
        }
        startActivity(intent)
        finish()
    }

    private fun showErrorAndFinish() {
        Toast.makeText(this, "Эквалайзер доступен только при воспроизведении", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}